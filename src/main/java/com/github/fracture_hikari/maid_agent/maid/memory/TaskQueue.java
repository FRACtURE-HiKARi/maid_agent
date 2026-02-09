package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Queue of pending tasks for the maid.
 * Supports task dependencies (DAG) using a PriorityQueue.
 * Tasks with 0 reference count are prioritized.
 * Notifies LLM via MaidAIChatManager.onPendingSuccess() when toolCallId group completes.
 */
public class TaskQueue {
    
    // PriorityQueue to automatically surface ready tasks (refCount == 0)
    private final PriorityQueue<PendingTask> tasks = new PriorityQueue<>();
    private final EntityMaid maid;
    private final List<TaskResult> completedResults = new ArrayList<>();
    
    // Track results and pending counts per toolCallId
    private final Map<String, List<TaskResult>> resultsByToolCallId = new HashMap<>();
    private final Map<String, Integer> pendingCountByToolCallId = new HashMap<>();
    
    public TaskQueue(EntityMaid maid) {
        this.maid = maid;
    }
    
    /**
     * Add a task to the queue.
     * @return Position in queue (approximation, as it's a heap)
     */
    public int enqueue(PendingTask task) {
        tasks.add(task);
        return tasks.size();
    }
    
    /**
     * Add multiple tasks to the queue with toolCallId tracking.
     */
    public void enqueue(List<PendingTask> newTasks, String toolCallId) {
        for (PendingTask task : newTasks) {
            task.setToolCallId(toolCallId);
            tasks.add(task);
        }
        pendingCountByToolCallId.merge(toolCallId, newTasks.size(), Integer::sum);
    }
    
    /**
     * Add multiple tasks to the queue (legacy, no toolCallId).
     * @deprecated Use enqueue(List, String) with toolCallId instead
     */
    @Deprecated
    public void enqueue(List<PendingTask> newTasks) {
        tasks.addAll(newTasks);
    }
    
    /**
     * Get any task that is ready to execute (refCount == 0).
     * PriorityQueue guarantees the task with lowest refCount is at head.
     */
    @Nullable
    public PendingTask peek() {
        var copy = new PriorityQueue<>(tasks);
        for (PendingTask task: copy) {
            if (task.getReferenceCount() == 0 && !task.isBlocked()) {
                return task;
            }
        }
        return null;
    }
    
    /**
     * Check if queue is empty.
     */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }
    
    /**
     * Get number of pending tasks.
     */
    public int size() {
        return tasks.size();
    }
    
    /**
     * Clear all tasks from the queue.
     */
    public void clear() {
        tasks.clear();
        completedResults.clear();
        resultsByToolCallId.clear();
        pendingCountByToolCallId.clear();
    }
    
    /**
     * Check if queue has similar task.
     */
    public boolean hasSimilarTask(Class<? extends PendingTask> taskType, net.minecraft.world.item.ItemStack item, int storageIndex) {
        for (PendingTask task : tasks) {
            if (taskType.isInstance(task) && 
                studio.fantasyit.maid_storage_manager.util.ItemStackUtil.isSame(task.getRequestedItem(), item, false) &&
                task.getTarget().isPresent() &&
                task.getTarget().get().getPos().equals(getStoragePosForIndex(storageIndex))) {
                return true;
            }
        }
        return false;
    }
    
    private BlockPos getStoragePosForIndex(int storageIndex) {
        return maid.getBrain()
                .getMemory(com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry.VIEWED_STORAGE.get())
                .flatMap(mem -> mem.getStorageByIndex(storageIndex))
                .map(WorkBlockTarget::getPos)
                .orElse(null);
    }
    
    /**
     * Mark a specific task as complete.
     * Decrements reference counts of dependent tasks.
     * Tracks results per toolCallId and triggers async callback when group completes.
     */
    public void completeTask(PendingTask task, boolean success, String message, int actualCount) {
        if (tasks.remove(task)) {
            // Success: decrement refcounts of dependents and update them in PQ
            for (PendingTask dependent : task.getDependents()) {
                // Must remove and re-add to update priority in PQ
                if (tasks.remove(dependent)) {
                    dependent.decrementRefCount();
                    tasks.add(dependent);
                } else {
                     dependent.decrementRefCount();
                }
            }
            
            TaskResult result = new TaskResult(
                task.getTypeName(),
                com.github.fracture_hikari.maid_agent.util.ItemIdUtils.getId(task.getRequestedItem()),
                task.getRequestedItem().getCount(),
                actualCount,
                success,
                message
            );
            completedResults.add(result);
            
            // Track by toolCallId and notify when group completes
            String toolCallId = task.getToolCallId();
            if (toolCallId != null) {
                resultsByToolCallId
                    .computeIfAbsent(toolCallId, k -> new ArrayList<>())
                    .add(result);
                
                int remaining = pendingCountByToolCallId.merge(toolCallId, -1, Integer::sum);
                if (remaining <= 0) {
                    notifyToolCallComplete(toolCallId);
                }
            }
        }
    }
    
    /**
     * Notify MaidAIChatManager that all tasks for a toolCallId have completed.
     */
    private void notifyToolCallComplete(String toolCallId) {
        List<TaskResult> results = resultsByToolCallId.remove(toolCallId);
        pendingCountByToolCallId.remove(toolCallId);
        
        if (results != null && !results.isEmpty()) {
            String summary = buildSummaryFor(results);
            maid.getAiChatManager().onPendingComplete(toolCallId, summary);
        } else {
            MaidAgent.LOGGER.warn("TaskQueue: no related results of toolCallId: {}", toolCallId);
        }
    }
    
    /**
     * Build summary for a specific set of results.
     */
    private String buildSummaryFor(List<TaskResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Completed %d task(s):\n", results.size()));
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < results.size(); i++) {
            TaskResult result = results.get(i);
            String status = result.success ? "Success" : "Failed";
            if (result.success) successCount++; else failCount++;
            
            if (result.typeName.equals(ExploreAllTask.TYPE_NAME) || result.typeName.equals(ExploreTask.TYPE_NAME)) {
                sb.append(String.format("%d. [%s] - %s\n", i + 1, result.typeName, status));
                if (result.message != null && !result.message.isEmpty()) {
                    sb.append(result.message).append("\n");
                }
            } else {
                String itemDisplay = result.itemId.isEmpty() ? "" : result.itemId.replace("minecraft:", "");
                String countDisplay = result.actualCount > 0 ? " x" + result.actualCount : "";
                
                sb.append(String.format("%d. [%s] %s%s - %s",
                    i + 1, result.typeName, itemDisplay, countDisplay, status));
                
                if (result.message != null && !result.message.isEmpty()) {
                    sb.append(" (").append(result.message).append(")");
                }
                sb.append("\n");
            }
        }
        
        if (failCount > 0) {
            sb.append(String.format("\nSummary: %d succeeded, %d failed", successCount, failCount));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Deprecated: Use completeTask(PendingTask, ...) instead.
     * Completes the task returned by peek().
     */
    @Deprecated
    public void completeCurrentTask(boolean success, String message, int actualCount) {
        PendingTask task = peek();
        if (task != null) {
            completeTask(task, success, message, actualCount);
        }
    }
    
    public String getBatchSummary() {
        if (completedResults.isEmpty()) {
            return "No tasks completed.";
        }
        
       return buildSummaryFor(completedResults);
    }

    public int getCompletedCount() {
        return completedResults.size();
    }
    
    public record TaskResult(
        String typeName,
        String itemId,
        int requestedCount,
        int actualCount,
        boolean success,
        String message
    ) {}
}

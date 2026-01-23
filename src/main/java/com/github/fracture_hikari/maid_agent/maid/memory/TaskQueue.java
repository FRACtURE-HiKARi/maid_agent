package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.ai.AIChatCallback;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Queue of pending tasks for the maid.
 * Supports multiple LLM function calls executed sequentially.
 * Notifies LLM only after entire batch completes.
 */
public class TaskQueue extends AbstractJobContainerWithAICallback<TaskQueue.TaskResult> {
    
    private final Queue<PendingTask> tasks = new ArrayDeque<>();
    private EntityMaid maid;
    
    public TaskQueue(EntityMaid maid) {
        super(maid);
        this.maid = maid;
    }
    
    /**
     * Add a task to the queue.
     * @return Position in queue (1-indexed)
     */
    public int enqueue(PendingTask task) {
        tasks.add(task);
        return tasks.size();
    }
    
    /**
     * Get the current task without removing it.
     */
    @Nullable
    public PendingTask peek() {
        return tasks.peek();
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
        super.clear();
    }
    
    /**
     * Check if queue has a similar task (same type, item, and storage).
     * Used for duplicate detection warning (but doesn't prevent adding).
     */
    public boolean hasSimilarTask(PendingTask.TaskType type, net.minecraft.world.item.ItemStack item, int storageIndex) {
        for (PendingTask task : tasks) {
            if (task.getType() == type && 
                studio.fantasyit.maid_storage_manager.util.ItemStackUtil.isSame(task.getRequestedItem(), item, false) &&
                task.getTarget().isPresent() &&
                task.getTarget().get().getPos().equals(getStoragePosForIndex(storageIndex))) {
                return true;
            }
        }
        return false;
    }
    
    // Helper to check storage position - compares by storage index via ViewedStorageMemory
    private net.minecraft.core.BlockPos getStoragePosForIndex(int storageIndex) {
        return maid.getBrain()
                .getMemory(com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry.VIEWED_STORAGE.get())
                .flatMap(mem -> mem.getStorageByIndex(storageIndex))
                .map(target -> target.getPos())
                .orElse(null);
    }
    
    /**
     * Mark current task as complete and move to next.
     * @param success Whether the task succeeded
     * @param message Result message
     * @param actualCount Actual count processed
     */
    public void completeCurrentTask(boolean success, String message, int actualCount) {
        PendingTask task = tasks.poll();
        if (task != null) {
            completedResults.add(new TaskResult(
                task.getType(),
                com.github.fracture_hikari.maid_agent.util.ItemIdUtils.getId(task.getRequestedItem()),
                task.getRequestedItem().getCount(),
                actualCount,
                success,
                message
            ));
        }
    }
    
    /**
     * Check if the entire batch is complete.
     */
    public boolean isBatchComplete() {
        return tasks.isEmpty() && !completedResults.isEmpty();
    }
    
    /**
     * Get summary of all completed tasks for LLM notification.
     */
    public String getBatchSummary() {
        if (completedResults.isEmpty()) {
            return "No tasks completed.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Completed %d task(s):\n", completedResults.size()));
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < completedResults.size(); i++) {
            TaskResult result = completedResults.get(i);
            String status = result.success ? "Success" : "Failed";
            if (result.success) successCount++; else failCount++;
            
            sb.append(String.format("%d. [%s] %s %s - %s",
                i + 1,
                result.type.name(),
                result.itemId.replace("minecraft:", ""),
                result.actualCount > 0 ? "x" + result.actualCount : "",
                status
            ));
            
            if (result.message != null && !result.message.isEmpty()) {
                sb.append(" (").append(result.message).append(")");
            }
            sb.append("\n");
        }
        
        if (failCount > 0) {
            sb.append(String.format("\nSummary: %d succeeded, %d failed", successCount, failCount));
        }
        
        return sb.toString().trim();
    }
    


    /**
     * Get count of completed results.
     */
    public int getCompletedCount() {
        return completedResults.size();
    }
    
    /**
     * Record of a completed task result.
     */
    public record TaskResult(
        PendingTask.TaskType type,
        String itemId,
        int requestedCount,
        int actualCount,
        boolean success,
        String message
    ) {}
}

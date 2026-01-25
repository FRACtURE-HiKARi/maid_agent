package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.PriorityQueue;

/**
 * Queue of pending tasks for the maid.
 * Supports task dependencies (DAG) using a PriorityQueue.
 * Tasks with 0 reference count are prioritized.
 * Notifies LLM only after entire batch completes.
 */
public class TaskQueue extends AbstractJobContainerWithAICallback<TaskQueue.TaskResult> {
    
    // PriorityQueue to automatically surface ready tasks (refCount == 0)
    private final PriorityQueue<PendingTask> tasks = new PriorityQueue<>();
    private EntityMaid maid;
    
    public TaskQueue(EntityMaid maid) {
        super(maid);
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
     * Add multiple tasks to the queue.
     */
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
        super.clear();
    }
    
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
    
    private BlockPos getStoragePosForIndex(int storageIndex) {
        return maid.getBrain()
                .getMemory(com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry.VIEWED_STORAGE.get())
                .flatMap(mem -> mem.getStorageByIndex(storageIndex))
                .map(target -> target.getPos())
                .orElse(null);
    }
    
    /**
     * Mark a specific task as complete.
     * Decrements reference counts of dependent tasks.
     */
    public void completeTask(PendingTask task, boolean success, String message, int actualCount) {
        if (tasks.remove(task)) {
            // Success: decrement refcounts of dependents and update them in PQ
            for (PendingTask dependent : task.getDependents()) {
                // Must remove and re-add to update priority in PQ
                // TODO: lazy insertion?
                if (tasks.remove(dependent)) {
                    dependent.decrementRefCount();
                    tasks.add(dependent);
                } else {
                     // Dependent might not be in queue yet or already handled
                     dependent.decrementRefCount();
                }
            }
            
            completedResults.add(new TaskResult(
                task.getType(),
                com.github.fracture_hikari.maid_agent.util.ItemIdUtils.getId(task.getRequestedItem()),
                task.getRequestedItem().getCount(),
                actualCount,
                success,
                message
            ));
        }
        if (tasks.isEmpty()) {
            notifyBatchComplete();
            clear();
        }
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
    
    public boolean isBatchComplete() {
        return tasks.isEmpty() && !completedResults.isEmpty();
    }
    
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

    public int getCompletedCount() {
        return completedResults.size();
    }
    
    public record TaskResult(
        PendingTask.TaskType type,
        String itemId,
        int requestedCount,
        int actualCount,
        boolean success,
        String message
    ) {}
}

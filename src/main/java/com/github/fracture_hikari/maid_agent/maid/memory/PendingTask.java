package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.ai.AIChatCallback;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Represents a pending task for the maid to execute.
 * Set by LLM function calls, consumed by behaviors.
 */
public class PendingTask {
    
    public enum TaskType {
        FETCH,   // Get items from storage
        STORE,   // Put items into storage
        CRAFT,   // Craft items at workstation (instant)
        PROCESS  // Two-phase: insert ingredients → wait → collect output
    }

    public enum TaskStatus {
        PENDING,    // Task just created, waiting to start
        MOVING,     // Maid is walking to target
        WORKING,    // Maid is interacting with target
        COMPLETED,  // Task finished successfully
        FAILED      // Task failed
    }
    
    public enum WorkstationType {
        CRAFTING_TABLE,
        FURNACE,
        SMOKER,
        BLAST_FURNACE
    }

    private final TaskType type;
    private final String itemId;
    private final int count;
    @Nullable
    private WorkBlockTarget target;
    private TaskStatus status;
    @Nullable
    private String resultMessage;
    private int actualCount; // Actual items fetched/stored/crafted
    private AIChatCallback callback;
    @Nullable
    private WorkstationType workstationType;  // For CRAFT tasks
    @Nullable
    private String recipeId;  // For CRAFT tasks - main recipe
    @Nullable
    private java.util.LinkedHashMap<String, Integer> craftingSteps;  // Ordered recipe ID -> craft count (sub-recipes first)
    @Nullable
    private java.util.List<SlotMapping> slotMappings;  // Slot mappings for PROCESS tasks

    public PendingTask(EntityMaid maid, TaskType type, String itemId, int count) {
        this(maid, type, itemId, count, null);
    }

    public PendingTask(EntityMaid maid, TaskType type, String itemId, int count, @Nullable WorkBlockTarget target) {
        this.type = type;
        this.itemId = itemId;
        this.count = count;
        this.target = target;
        this.status = TaskStatus.PENDING;
        this.resultMessage = null;
        this.actualCount = 0;
        this.callback = new AIChatCallback(maid); // TODO: replace this with proper client info.
        this.workstationType = null;
        this.recipeId = null;
        this.craftingSteps = null;
        this.slotMappings = null;
    }

    public TaskType getType() {
        return type;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    @Nullable
    public WorkBlockTarget getTarget() {
        return target;
    }

    public void setTarget(@Nullable WorkBlockTarget target) {
        this.target = target;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @Nullable
    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(@Nullable String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public int getActualCount() {
        return actualCount;
    }

    public void setActualCount(int actualCount) {
        this.actualCount = actualCount;
    }

    public boolean isComplete() {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED;
    }

    public void complete(String message, int actualCount) {
        this.status = TaskStatus.COMPLETED;
        this.resultMessage = message;
        this.actualCount = actualCount;
    }

    public void fail(String message) {
        this.status = TaskStatus.FAILED;
        this.resultMessage = message;
    }
    
    @Nullable
    public WorkstationType getWorkstationType() {
        return workstationType;
    }
    
    public void setWorkstationType(@Nullable WorkstationType workstationType) {
        this.workstationType = workstationType;
    }
    
    @Nullable
    public String getRecipeId() {
        return recipeId;
    }
    
    public void setRecipeId(@Nullable String recipeId) {
        this.recipeId = recipeId;
    }
    
    @Nullable
    public java.util.LinkedHashMap<String, Integer> getCraftingSteps() {
        return craftingSteps;
    }
    
    public void setCraftingSteps(@Nullable java.util.LinkedHashMap<String, Integer> craftingSteps) {
        this.craftingSteps = craftingSteps;
    }
    
    @Nullable
    public java.util.List<SlotMapping> getSlotMappings() {
        return slotMappings;
    }
    
    public void setSlotMappings(@Nullable java.util.List<SlotMapping> slotMappings) {
        this.slotMappings = slotMappings;
    }

    @Override
    public String toString() {
        return String.format("PendingTask{type=%s, item=%s, count=%d, status=%s}",
                type, itemId, count, status);
    }

    public static Optional<PendingTask> maidPeekTask(EntityMaid maid) {
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        return queueOpt.map(TaskQueue::peek);
    }
}

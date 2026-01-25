package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Represents a pending task for the maid to execute.
 * Set by LLM function calls, consumed by behaviors.
 */
public class PendingTask implements Comparable<PendingTask> {
    
    public enum TaskType {
        FETCH,   // Get items from storage
        STORE,   // Put items into storage
        CRAFT,   // Craft items at workstation (instant)
        PROCESS  // Two-phase: insert ingredients → wait → collect output
    }

    // TaskStatus deprecated and removed

    // Dependency Management
    private final java.util.List<PendingTask> dependents = new java.util.ArrayList<>();
    private int referenceCount = 0;

    public void addDependent(PendingTask dependent) {
        this.dependents.add(dependent);
        dependent.incrementRefCount();
    }

    public java.util.List<PendingTask> getDependents() {
        return dependents;
    }

    public void incrementRefCount() {
        this.referenceCount++;
    }

    public void decrementRefCount() {
        if (this.referenceCount > 0) {
            this.referenceCount--;
        }
    }

    public int getReferenceCount() {
        return referenceCount;
    }
    
    public enum WorkstationType {
        CRAFTING_TABLE,
        FURNACE,
        SMOKER,
        BLAST_FURNACE
    }

    private final TaskType type;
    private final ItemStack requestedItem;
    @Nullable
    private WorkBlockTarget target;
    private int actualCount; // Actual items fetched/stored/crafted
    @Nullable
    private WorkstationType workstationType;  // For CRAFT tasks
    @Nullable
    private String recipeId;  // For CRAFT tasks - main recipe

    @Nullable
    private java.util.List<SlotMapping> slotMappings;  // Slot mappings for PROCESS tasks

    public PendingTask(EntityMaid maid, TaskType type, ItemStack requestedItem) {
        this(maid, type, requestedItem, null);
    }

    public PendingTask(EntityMaid maid, TaskType type, ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        this.type = type;
        this.requestedItem = requestedItem.copy();
        this.target = target;
        this.actualCount = -1;
        this.workstationType = null;
        this.recipeId = null;
        this.slotMappings = null;
    }

    public TaskType getType() {
        return type;
    }

    public ItemStack getRequestedItem() {
        return requestedItem;
    }

    public int getCount() {return requestedItem.getCount();}

    public Optional<WorkBlockTarget> getTarget() {
        return Optional.ofNullable(target);
    }

    public void setTarget(@Nullable WorkBlockTarget target) {
        this.target = target;
    }

    public int getActualCount() {
        return actualCount;
    }

    public void setActualAccount(int actualCount) {
        this.actualCount = actualCount;
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
    public java.util.List<SlotMapping> getSlotMappings() {
        return slotMappings;
    }

    // Changing signature to include level as per logic requirement
    public boolean isValidWorkBlock(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (target == null) return false;
        
        if (target.getPos() != null) {
            return target.getPos().equals(pos);
        } else {
            // Target is generic (null pos), check if block at 'pos' matches 'target.type'
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            net.minecraft.resources.ResourceLocation blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return blockId != null && blockId.equals(target.getType());
        }
    }
    
    public void setSlotMappings(@Nullable java.util.List<SlotMapping> slotMappings) {
        this.slotMappings = slotMappings;
    }

    @Override
    public String toString() {
        return String.format("PendingTask{type=%s, item=%s, count=%d, actual=%d, refCount=%d}",
                type, com.github.fracture_hikari.maid_agent.util.ItemIdUtils.getId(requestedItem), requestedItem.getCount(), actualCount, referenceCount);
    }

    @Override
    public int compareTo(PendingTask other) {
        return Integer.compare(this.referenceCount, other.referenceCount);
    }
}

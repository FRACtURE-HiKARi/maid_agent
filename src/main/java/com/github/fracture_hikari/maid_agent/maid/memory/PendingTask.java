package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for pending tasks the maid executes.
 * Set by LLM function calls, consumed by behaviors.
 * 
 * Subclasses: FetchTask, StoreTask, ExploreTask, ExploreAllTask, CraftTask, ProcessTask
 */
public abstract class PendingTask implements Comparable<PendingTask> {

    // Dependency Management
    private final List<PendingTask> dependents = new ArrayList<>();
    private int referenceCount = 0;

    // Common fields
    private final ItemStack requestedItem;
    @Nullable
    private WorkBlockTarget target;
    private int actualCount;

    @Nullable
    private String toolCallId;  // Associates task with originating LLM tool call

    private ProcessingJob blockingJob = null;

    protected PendingTask(ItemStack requestedItem) {
        this(requestedItem, null);
    }

    protected PendingTask(ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        this(requestedItem, target, null);
    }

    protected PendingTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, @Nullable String toolCallId) {
        this.requestedItem = requestedItem.copy();
        this.target = target;
        this.actualCount = -1;
        this.toolCallId = toolCallId;
    }

    /**
     * Get the type name for logging/summaries.
     * Subclasses should define a static TYPE_NAME field.
     */
    public abstract String getTypeName();

    // Dependency methods
    public void addDependent(PendingTask dependent) {
        this.dependents.add(dependent);
        dependent.incrementRefCount();
    }

    public List<PendingTask> getDependents() {
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

    // Blocking job
    public void setBlockingJob(ProcessingJob blockingJob) {
        this.blockingJob = blockingJob;
    }

    public boolean isBlocked() {
        return blockingJob != null;
    }

    // Common getters/setters
    public ItemStack getRequestedItem() {
        return requestedItem;
    }

    public int getCount() {
        return requestedItem.getCount();
    }

    public Optional<WorkBlockTarget> getTarget() {
        return Optional.ofNullable(target);
    }

    public void setTarget(@Nullable WorkBlockTarget target) {
        this.target = target;
    }

    public int getActualCount() {
        return actualCount;
    }

    public void setActualCount(int actualCount) {
        this.actualCount = actualCount;
    }

    @Nullable
    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(@Nullable String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public boolean isValidWorkBlock(ServerLevel level, BlockPos pos) {
        if (target == null) return false;

        if (target.getPos() != null) {
            return target.getPos().equals(pos);
        } else {
            // Target is generic (null pos), check if block at 'pos' matches 'target.type'
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            net.minecraft.resources.ResourceLocation blockId = 
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return blockId != null && blockId.equals(target.getType());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s{item=%s, count=%d, actual=%d, refCount=%d}",
                getTypeName(), ItemIdUtils.getId(requestedItem), requestedItem.getCount(), actualCount, referenceCount));
        if (isBlocked()) {
            sb.append(" blocked by ").append(blockingJob.toString());
        }
        return sb.toString();
    }

    @Override
    public int compareTo(PendingTask other) {
        return Integer.compare(this.referenceCount, other.referenceCount);
    }
}

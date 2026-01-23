package com.github.fracture_hikari.maid_agent.util;

import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Utility class for task queue operations.
 * Handles task creation, workstation finding, and maid movement.
 */
public final class TaskQueueHelper {
    
    private static final float DEFAULT_WALK_SPEED = 0.6f;
    
    private TaskQueueHelper() {} // Prevent instantiation
    
    /**
     * Find nearest workstation of given type within radius.
     * @param level Server level
     * @param center Center position to search from
     * @param radius Search radius
     * @param type Workstation type to find
     * @return Optional containing nearest position if found
     */
    public static Optional<BlockPos> findNearestWorkstation(
            ServerLevel level, BlockPos center, int radius, PendingTask.WorkstationType type) {
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -4, -radius),
                center.offset(radius, 4, radius))) {
            if (isWorkstation(level, pos, type)) {
                double dist = center.distSqr(pos);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        
        return Optional.ofNullable(nearest);
    }
    
    /**
     * Check if block at position is a workstation of given type.
     */
    public static boolean isWorkstation(ServerLevel level, BlockPos pos, PendingTask.WorkstationType type) {
        BlockState state = level.getBlockState(pos);
        return switch (type) {
            case CRAFTING_TABLE -> state.is(Blocks.CRAFTING_TABLE);
            case FURNACE -> state.is(Blocks.FURNACE);
            case SMOKER -> state.is(Blocks.SMOKER);
            case BLAST_FURNACE -> state.is(Blocks.BLAST_FURNACE);
        };
    }
    
    /**
     * Get task queue for maid (read-only, does not create if absent).
     * Use this for querying queue status without side effects.
     */
    public static Optional<TaskQueue> getQueue(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
    }
    
    /**
     * Get or create task queue for maid.
     */
    public static TaskQueue getOrCreateQueue(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .orElseGet(() -> {
                    TaskQueue newQueue = new TaskQueue(maid);
                    maid.getBrain().setMemory(MemoryModuleRegistry.TASK_QUEUE.get(), newQueue);
                    return newQueue;
                });
    }
    
    /**
     * Queue a task. If it's the only task, start movement.
     * @param maid The maid
     * @param task Task to queue
     * @param targetPos Optional target position for movement
     */
    public static void queueTask(EntityMaid maid, PendingTask task, BlockPos targetPos) {
        TaskQueue queue = getOrCreateQueue(maid);
        queue.enqueue(task);
    }
    
    /**
     * Queue a task without movement.
     */
    public static void queueTask(EntityMaid maid, PendingTask task) {
        queueTask(maid, task, null);
    }
    
    /**
     * Set maid movement target.
     */
    public static void setMovementTarget(EntityMaid maid, BlockPos target, float speed) {
        maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(target));
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, speed, 1);
    }
    
    /**
     * Set maid movement target with default speed.
     */
    public static void setMovementTarget(EntityMaid maid, BlockPos target) {
        setMovementTarget(maid, target, DEFAULT_WALK_SPEED);
    }
    
    /**
     * Get search radius for maid (uses restriction radius if set).
     */
    public static int getSearchRadius(EntityMaid maid, int defaultRadius) {
        return maid.hasRestriction() ? (int) maid.getRestrictRadius() : defaultRadius;
    }
}

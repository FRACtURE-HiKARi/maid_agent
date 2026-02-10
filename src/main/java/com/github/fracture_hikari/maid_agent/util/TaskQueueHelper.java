package com.github.fracture_hikari.maid_agent.util;

import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

/**
 * Utility class for task queue operations.
 * Handles task creation, workstation finding, and maid movement.
 */
public final class TaskQueueHelper {
    
    private static final float DEFAULT_WALK_SPEED = 0.6f;
    
    private TaskQueueHelper() {} // Prevent instantiation

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
}

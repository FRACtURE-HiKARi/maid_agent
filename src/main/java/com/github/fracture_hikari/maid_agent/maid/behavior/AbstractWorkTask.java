package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for work tasks.
 * Handles common movement, targeting, and task queue management.
 * Subclasses implement canHandle() to filter task types and performWork() for actual work.
 */
public abstract class AbstractWorkTask extends Behavior<EntityMaid> {
    protected static final double CLOSE_ENOUGH = 2.5;
    protected static final float WALK_SPEED = 0.6f;
    
    protected boolean workDone = false;
    protected String resultMessage = null;

    public AbstractWorkTask() {
        this(100, 200);
    }
    
    public AbstractWorkTask(int minDuration, int maxDuration) {
        super(Map.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED,
                MemoryModuleRegistry.PROCESSING_JOBS.get(), MemoryStatus.REGISTERED
        ), minDuration, maxDuration);
    }

    /**
     * Check if this behavior can handle the given task type.
     */
    protected abstract boolean canHandle(PendingTask task);
    
    /**
     * Perform the actual work. Called when maid has reached target.
     * @return Result message describing what happened
     */
    protected abstract String performWork(ServerLevel level, EntityMaid maid, PendingTask task);

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isEmpty() || queueOpt.get().isEmpty()) {
            return false;
        }
        
        PendingTask task = queueOpt.get().peek();
        if (task == null) {
            return false;
        }

        if (task.getStatus() != PendingTask.TaskStatus.MOVING) {
            return false;
        }
        
        // Check if this behavior can handle this task
        if (!canHandle(task)) {
            return false;
        }
        return hasReachedTarget(maid);
    }
    
    protected boolean hasReachedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            double distSq = maid.distanceToSqr(targetV3d);
            boolean arrived = distSq < Math.pow(CLOSE_ENOUGH, 2);
            
            if (!arrived) {
                Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty()) {
                    BlockPos targetBlockPos = BlockPos.containing(targetV3d);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, targetBlockPos, WALK_SPEED, 1);
                }
            }
            return arrived;
        }).orElse(false);
    }
    
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !workDone;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        workDone = false;
        resultMessage = null;
        
        maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .map(TaskQueue::peek)
                .ifPresent(task -> task.setStatus(PendingTask.TaskStatus.WORKING));
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (workDone) return;
        
        maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .map(TaskQueue::peek)
                .ifPresent(task -> {
                    if (canHandle(task)) {
                        resultMessage = performWork(level, maid, task);
                    } else {
                        resultMessage = "Task type not handled by this behavior";
                        task.fail(resultMessage);
                    }
                    workDone = true;
                });
    }
    
    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // Clear movement memories
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isEmpty()) return;
        
        TaskQueue queue = queueOpt.get();
        PendingTask completedTask = queue.peek();
        
        if (completedTask != null && canHandle(completedTask)) {
            boolean success = completedTask.getStatus() == PendingTask.TaskStatus.COMPLETED;
            queue.completeCurrentTask(success, completedTask.getResultMessage(), completedTask.getActualCount());
            MaidAgent.LOGGER.info("Task completed: {} - {}", completedTask.getType(), resultMessage);
            //completedTask.notifyComplete(); shouldn't notify, for each of the tasks.
        }
        
        advanceToNextTask(maid, queue);
    }
    
    /**
     * Advance to next task in queue or complete batch.
     */
    protected void advanceToNextTask(EntityMaid maid, TaskQueue queue) {
        if (queue.isBatchComplete()) {
            queue.notifyBatchComplete();
            maid.getBrain().eraseMemory(MemoryModuleRegistry.TASK_QUEUE.get());
            MaidAgent.LOGGER.info("Batch complete, notified LLM");
        } else if (!queue.isEmpty()) {
            PendingTask nextTask = queue.peek();
            if (nextTask != null && nextTask.getTarget() != null) {
                nextTask.setStatus(PendingTask.TaskStatus.MOVING);
                BlockPos targetPos = nextTask.getTarget().getPos();
                TaskQueueHelper.setMovementTarget(maid, targetPos, WALK_SPEED);
                MaidAgent.LOGGER.info("Starting next task: {} at {}", nextTask.getType(), targetPos);
            }
        }
    }
    
    /**
     * Helper to get item display name.
     */
    protected String getItemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }
}

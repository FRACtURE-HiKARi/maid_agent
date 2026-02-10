package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import studio.fantasyit.maid_storage_manager.storage.ItemHandler.SimulateTargetInteractHelper;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Abstract base class for work tasks.
 * Handles common movement, targeting, and task queue management.
 * Subclasses implement canHandle() to filter task types and performWork() for actual work.
 */
public abstract class AbstractWorkTask extends MaidCheckRateTask {
    protected static final double CLOSE_ENOUGH = 2.5;
    protected static final float WALK_SPEED = 0.6f;
    protected SimulateTargetInteractHelper helper = null;

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
     * Perform the actual work. Called when maid has reached target.
     * @return Result message describing what happened
     */
    protected abstract void handle(ServerLevel level, EntityMaid maid);

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        return hasReachedTarget(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long pTime) {
        if (helper != null) {
            helper.open();
        }
        handle(level, maid);
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (helper != null) {
            helper.stop();
            helper = null;
        }
    }

    protected boolean hasReached(EntityMaid maid, Vec3 targetV3d) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
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

    protected boolean hasReachedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            return hasReached(maid, targetV3d);
        }).orElse(false);
    }
    
    /**
     * Helper to get item display name.
     */
    protected String getItemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    protected BlockPos getWorkBlockPos(ServerLevel level, EntityMaid maid, PendingTask task) {
        WorkBlockTarget target = task.getTarget().get();
        BlockPos targetPos = target.getPos();

        // If target pos is generic (null), try to use the position the maid actually walked to
        if (targetPos == null) {
            var memOpt =
                    maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
            if (memOpt.isPresent()) {
                BlockPos memPos = BlockPos.containing(memOpt.get().currentPosition());
                if (task.isValidWorkBlock(level, memPos)) {
                    targetPos = memPos;
                }
            }
        }
        return targetPos;
    }

    protected boolean checkTaskMemory(ServerLevel level, EntityMaid maid, Predicate<PendingTask> predicate) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        PendingTask task = taskOpt.get();
        if (!predicate.test(task)) return false;

        if (task.getTarget().isPresent()) {
            WorkBlockTarget target = task.getTarget().get();
            BlockPos targetPos = getWorkBlockPos(level, maid, task);

            if (targetPos != null) {
                helper = new SimulateTargetInteractHelper(maid, targetPos, target.getSideOrNull(), level);
            } else {
                return false;
            }
        }
        return true;
    }
}

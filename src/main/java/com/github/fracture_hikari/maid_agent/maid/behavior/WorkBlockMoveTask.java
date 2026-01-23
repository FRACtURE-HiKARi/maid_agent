package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;


// MSM imports for storage detection

import java.util.Optional;

/**
 * Behavior that makes the maid walk toward a storage block.
 * Uses MSM's MaidStorage for storage detection.
 * 
 * Activated when there's a TASK_QUEUE with tasks in PENDING status AND no TARGET_POS set.
 */
public class WorkBlockMoveTask extends AbstractMoveTask {

    protected PendingTask currentMove;
    public WorkBlockMoveTask() {
        super(20);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        Optional<WorkBlockTarget> target = taskOpt.get().getTarget();
        return target.isPresent() && pos.equals(target.get().getPos());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid))
            return false;

        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        currentMove = taskOpt.get();

        if (currentMove.getTarget().isEmpty()) return false;
        BlockPos pos = currentMove.getTarget().get().getPos();
        MaidAgent.LOGGER.info("TaskMoveTask: Setting up movement for {} to {}", currentMove.getType(), pos);
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        this.searchForDestination(level, maid);
        taskOpt.ifPresent(task -> {
            if (task.getTarget().isPresent()) {
                MaidAgent.LOGGER.info("StorageMoveTask: move to storage at {}", task.getTarget().get().getPos());
            } else {
                MaidAgent.LOGGER.info("StorageMoveTask: task target is null type={}", task.getType());
            }
        });
    }
}

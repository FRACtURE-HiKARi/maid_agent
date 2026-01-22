package com.github.fracture_hikari.maid_agent.maid.behavior;

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

    public WorkBlockMoveTask() {
        super();
        this.setMaxCheckRate(20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid))
            return false;

        if (currentMove.getType() == PendingTask.TaskType.PROCESS) {
            return false;
        }

        // For CRAFT and PROCESS tasks, target is already set by CraftItemFunction
        // For FETCH/STORE, we may need to search for storage
        BlockPos pos = currentMove.getTarget().getPos();
        MaidAgent.LOGGER.info("TaskMoveTask: Setting up movement for {} to {}", currentMove.getType(), pos);
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        Optional<PendingTask> taskOpt = PendingTask.maidPeekTask(maid);
        taskOpt.ifPresent(task -> {
            if (task.getTarget() != null) {
                MaidAgent.LOGGER.info("StorageMoveTask: move to storage at {}", task.getTarget().getPos());
            } else {
                MaidAgent.LOGGER.info("StorageMoveTask: task target is null type={}", task.getType());
            }
        });
    }
}

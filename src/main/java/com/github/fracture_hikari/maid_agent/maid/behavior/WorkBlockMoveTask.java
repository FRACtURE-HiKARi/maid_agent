package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.ExploreAllTask;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
        return taskOpt.get().isValidWorkBlock(level, pos);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid))
            return false;

        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        currentMove = taskOpt.get();
        
        // Skip EXPLORE_ALL tasks - those are handled by ExploreStoragesTask
        if (currentMove instanceof ExploreAllTask) {
            return false;
        }

        if (currentMove.getTarget().isEmpty()) return false;
        
        // Log intent (target pos might be null, which is fine for generic search)
        BlockPos pos = currentMove.getTarget().get().getPos();
        String targetStr = (pos != null) ? pos.toShortString() : "generic:" + currentMove.getTarget().get().getType();
        MaidAgent.LOGGER.info("TaskMoveTask: Setting up movement for {} to {}", currentMove.getTypeName(), targetStr);
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        this.searchForDestination(level, maid);
    }
}

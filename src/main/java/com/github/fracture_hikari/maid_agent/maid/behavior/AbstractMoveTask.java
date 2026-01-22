package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Abstract base class for movement behaviors.
 * Handles common logic for setting movement targets and checking arrival.
 * 
 * Subclasses:
 * - TaskMoveTask: Consumes TaskQueue for FETCH/STORE/CRAFT/PROCESS
 * - ProcessingMoveTask: Consumes ProcessingMemory for collection
 */
public abstract class AbstractMoveTask extends MaidMoveToBlockTask {
    protected static final float WALK_SPEED = 0.6f;
    protected PendingTask currentMove;
    public AbstractMoveTask() {
        super(WALK_SPEED);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Optional<PendingTask> taskOpt = PendingTask.maidPeekTask(maid);
        if (taskOpt.isEmpty()) return false;
        WorkBlockTarget target = taskOpt.get().getTarget();
        return target != null && pos.equals(target.getPos());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = PendingTask.maidPeekTask(maid);
        if (taskOpt.isEmpty()) return false;
        currentMove = taskOpt.get();
        return currentMove.getTarget() != null;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTimeIn) {
        Optional<PendingTask> taskOpt = PendingTask.maidPeekTask(maid);
        taskOpt.ifPresent(task -> task.setStatus(PendingTask.TaskStatus.MOVING));
        this.searchForDestination(level, maid);
    }

}

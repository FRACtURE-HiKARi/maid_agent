package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;

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
    public AbstractMoveTask() {
        super(WALK_SPEED);
    }
    public AbstractMoveTask(int maxCheckRate) {
        this();
        setMaxCheckRate(maxCheckRate);
    }

}

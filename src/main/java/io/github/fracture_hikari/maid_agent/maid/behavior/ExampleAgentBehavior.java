package io.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.Map;

/**
 * Example behavior class for the Agent task.
 * 
 * Behaviors are the core AI building blocks in Minecraft's Brain system.
 * Each behavior has:
 * - checkExtraStartConditions: Should this behavior start?
 * - start: Called when behavior begins
 * - tick: Called each tick while running
 * - stop: Called when behavior ends
 * 
 * TouhouLittleMaid provides some base classes you can extend:
 * - MaidMoveToBlockTask: For behaviors that move to a block
 * - MaidCheckRateTask: For behaviors with rate-limiting
 */
public class ExampleAgentBehavior extends Behavior<EntityMaid> {

    public ExampleAgentBehavior() {
        // Constructor takes a map of required memory states
        // An empty map means no memory requirements
        super(Map.of());
    }

    /**
     * Check if this behavior should start running.
     * Called every tick when the behavior is not active.
     */
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        // TODO: Add your start conditions
        // Example: Check if maid has certain items, is near a workstation, etc.
        return false; // Disabled for now
    }

    /**
     * Called when the behavior starts.
     */
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        // TODO: Initialize behavior state
    }

    /**
     * Called every tick while the behavior is running.
     */
    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        // TODO: Main behavior logic
    }

    /**
     * Check if the behavior should continue running.
     */
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        // TODO: Check if behavior should keep running
        return false;
    }

    /**
     * Called when the behavior stops.
     */
    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        super.stop(level, maid, gameTime);
        // TODO: Cleanup behavior state
    }
}

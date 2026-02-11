package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

/**
 * Abstract base class for exploration behaviors with a "boredom timeout".
 *
 * <p>Manages the walk-arrive-explore-repeat loop and a boredom deadline that resets
 * each time a valid target is successfully reached. If no progress is made within
 * the boredom timeout, exploration ends.</p>
 *
 * <p>Subclasses implement:</p>
 * <ul>
 *   <li>{@link #configureFromTask} — validate task type and extract config (e.g., radius)</li>
 *   <li>{@link #shouldMoveTo} — filter candidate blocks during BFS search</li>
 *   <li>{@link #onArrival} — do work when the maid arrives at a target block</li>
 *   <li>{@link #onExplorationComplete} — report results when exploration ends</li>
 * </ul>
 */
public abstract class AbstractExploreTask extends MaidMoveToBlockTask {
    private static final float WALK_SPEED = 0.6f;
    private static final double ARRIVAL_DISTANCE_SQ = 2.5 * 2.5;
    private static final int DEFAULT_BOREDOM_TIMEOUT_TICKS = 100; // 5 seconds

    private final int boredomTimeoutTicks;

    private BlockPos explorationCenter;
    private int explorationRadius;
    private long boredomDeadline;
    private boolean inSession;

    protected AbstractExploreTask() {
        this(DEFAULT_BOREDOM_TIMEOUT_TICKS);
    }

    protected AbstractExploreTask(int boredomTimeoutTicks) {
        super(WALK_SPEED, 4);  // verticalSearchRange = 4
        setMaxCheckRate(20);
        this.boredomTimeoutTicks = boredomTimeoutTicks;
    }

    // ---- Template methods for subclasses ----

    /**
     * Get the default exploration radius if not overridden by the task.
     */
    protected int getDefaultExplorationRadius() {
        return 16;
    }

    /**
     * Configure this behavior from the pending task.
     * Called during {@link #checkExtraStartConditions}.
     * Subclasses should validate the task type and extract any configuration (e.g., radius).
     *
     * @param task the task at the head of the queue
     * @return true if this behavior should handle the task, false to reject
     */
    protected abstract boolean configureFromTask(PendingTask task);

    /**
     * Called when the maid has arrived at a target block.
     * Subclasses should perform exploration work here (e.g., read storage contents).
     *
     * @param level    the server level
     * @param maid     the maid entity
     * @param pos      the block position arrived at
     */
    protected abstract void onArrival(ServerLevel level, EntityMaid maid, BlockPos pos);

    /**
     * Called when exploration ends, either because all targets are exhausted
     * or the boredom timeout fired.
     * Subclasses should report results and clean up task state here.
     *
     * @param level the server level
     * @param maid  the maid entity
     */
    protected abstract void onExplorationComplete(ServerLevel level, EntityMaid maid);

    /**
     * Set the exploration radius. Typically called from {@link #configureFromTask}.
     */
    protected void setExplorationRadius(int radius) {
        this.explorationRadius = radius;
    }

    /**
     * Get the current exploration center position.
     */
    protected BlockPos getExplorationCenter() {
        return explorationCenter;
    }

    /**
     * Get the current exploration radius.
     */
    protected int getExplorationRadius() {
        return explorationRadius;
    }

    // ---- Lifecycle managed by abstract class ----

    @Override
    protected int getHorizontalSearchRange(EntityMaid maid) {
        return explorationRadius;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }

        // If mid-session and boredom has expired, end the session
        if (inSession && level.getGameTime() > boredomDeadline) {
            MaidAgent.LOGGER.debug("{}: Boredom timeout reached, stopping exploration", debugString());
            onExplorationComplete(level, maid);
            inSession = false;
            return false;
        }

        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;

        explorationRadius = getDefaultExplorationRadius();
        return configureFromTask(taskOpt.get());
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!inSession) {
            // First start of a new exploration session
            explorationCenter = maid.blockPosition();
            inSession = true;
            resetBoredom(gameTime);

            MaidAgent.LOGGER.debug("{}: Starting exploration from {} with radius {}",
                    debugString(), explorationCenter.toShortString(), explorationRadius);
        }

        // Search for next target
        searchForDestination(level, maid);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        // Check if we've arrived at current target
        Optional<BlockPos> targetPosOpt = maid.getBrain()
                .getMemory(InitEntities.TARGET_POS.get())
                .map(tracker -> BlockPos.containing(tracker.currentPosition()));

        if (targetPosOpt.isEmpty()) {
            // No target — stop, let the behavior restart naturally
            return false;
        }

        BlockPos targetPos = targetPosOpt.get();
        double distSq = maid.distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (distSq < ARRIVAL_DISTANCE_SQ) {
            // Arrived — do work and reset boredom
            onArrival(level, maid, targetPos);
            resetBoredom(gameTime);

            // Clear movement targets
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

            // Search for next target
            searchForDestination(level, maid);
        }

        return true;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // Just clear movement memories — don't report completion.
        // Boredom check in checkExtraStartConditions handles session end.
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ---- Internal helpers ----

    private void resetBoredom(long gameTime) {
        boredomDeadline = gameTime + boredomTimeoutTicks;
    }
}

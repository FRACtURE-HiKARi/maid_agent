package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.maid.memory.ExploreAllTask;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;
import java.util.Optional;

// MSM imports
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;

/**
 * Behavior task for EXPLORE_ALL task type.
 * 
 * Dynamically finds and explores all nearby storage blocks:
 * 1. On start(), searches for nearest unvisited storage using BFS
 * 2. If found, walks to it; if not found, completes exploration
 * 3. On arrival (detected via canStillUse), reads contents and searches for next
 * 4. Repeats until no more unvisited storages in range
 * 
 * Uses parent class's searchForDestination() and getHorizontalSearchRange().
 */
public class ExploreStoragesTask extends MaidMoveToBlockTask {
    private static final float WALK_SPEED = 0.6f;
    private static final double ARRIVAL_DISTANCE_SQ = 2.5 * 2.5;
    
    private ExploreAllTask currentExploreTask;
    private BlockPos explorationCenter;
    private int explorationRadius = 16;
    
    public ExploreStoragesTask() {
        super(WALK_SPEED, 4);  // verticalSearchRange = 4
        setMaxCheckRate(20);
    }
    
    @Override
    protected int getHorizontalSearchRange(EntityMaid maid) {
        return explorationRadius;  // Use task's radius instead of maid.getRestrictRadius()
    }
    
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        // Check for EXPLORE_ALL task
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        
        PendingTask task = taskOpt.get();
        if (!(task instanceof ExploreAllTask exploreAllTask)) return false;
        
        currentExploreTask = exploreAllTask;
        explorationRadius = exploreAllTask.getExplorationRadius();
        return true;
    }
    
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        explorationCenter = maid.blockPosition();
        
        MaidAgent.LOGGER.info("ExploreStoragesTask: Starting exploration from {} with radius {}", 
                explorationCenter.toShortString(), explorationRadius);
        
        // Search for first storage
        searchForDestination(level, maid);
        
        // Check if search found anything - if TARGET_POS is absent, no valid storage found
        if (maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).isEmpty()) {
            MaidAgent.LOGGER.info("ExploreStoragesTask: No storages found in range, completing immediately");
            completeExploration(level, maid, "No storage containers found in range.");
        }
    }
    
    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        // Enforce exploration center constraint
        if (explorationCenter != null && 
            pos.distManhattan(explorationCenter) > explorationRadius) {
            return false;
        }
        
        // Check if this is a valid storage using MSM
        Target msmTarget = MaidStorage.getInstance().isValidTarget(level, maid, pos, null);
        if (msmTarget == null) {
            return false;
        }
        
        // Skip furnaces and processing blocks
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
            return false;
        }
        
        // Check if already visited in memory
        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
        WorkBlockTarget ourTarget = new WorkBlockTarget(msmTarget);
        
        if (memory.hasVisited(ourTarget)) {
            return false;
        }
        
        return true;
    }
    
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        // Check if we've arrived at target
        Optional<BlockPos> targetPosOpt = maid.getBrain()
                .getMemory(InitEntities.TARGET_POS.get())
                .map(tracker -> BlockPos.containing(tracker.currentPosition()));
        
        if (targetPosOpt.isEmpty()) {
            // No target - should not happen during normal operation
            return false;
        }
        
        BlockPos targetPos = targetPosOpt.get();
        double distSq = maid.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        
        if (distSq < ARRIVAL_DISTANCE_SQ) {
            // Arrived at storage - explore it and search for next
            exploreStorage(level, maid, targetPos);
            
            // Clear movement targets
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            
            // Search for next storage
            searchForDestination(level, maid);
            
            // If no more targets found, we're done
            if (maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).isEmpty()) {
                ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
                completeExploration(level, maid, memory.getStorageContentsSummary());
                return false;  // Stop behavior
            }
        }
        
        return true;  // Continue running
    }
    
    /**
     * Read storage contents at the given position and update memory.
     */
    private void exploreStorage(ServerLevel level, EntityMaid maid, BlockPos pos) {
        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
        
        // Get storage target from MSM
        Target msmTarget = MaidStorage.getInstance().isValidTarget(level, maid, pos, null);
        if (msmTarget == null) {
            MaidAgent.LOGGER.warn("ExploreStoragesTask: Storage at {} no longer valid", pos.toShortString());
            return;
        }
        
        WorkBlockTarget target = new WorkBlockTarget(msmTarget);
        
        // Read contents
        BlockEntity be = level.getBlockEntity(pos);
        String name = level.getBlockState(pos).getBlock().getName().getString();
        if (be == null) {
            MaidAgent.LOGGER.warn("ExploreStoragesTask: No block entity at {}", pos.toShortString());
            memory.markVisited(target);
            return;
        }
        
        be.getCapability(ForgeCapabilities.ITEM_HANDLER, msmTarget.getSide().orElse(null)).ifPresent(storage -> {
            List<ItemStack> contents = InventoryUtils.aggregate(storage);
            memory.addStorage(target, contents, name);
            memory.markVisited(target);
            
            MaidAgent.LOGGER.info("ExploreStoragesTask: Explored {} at {} - found {} item types",
                    target.getType().getPath(), pos.toShortString(), contents.size());
        });
    }
    
    /**
     * Complete the exploration task and report results to AI.
     */
    private void completeExploration(ServerLevel level, EntityMaid maid, String summary) {
        MaidAgent.LOGGER.info("ExploreStoragesTask: Exploration complete");
        
        // Clear movement memories
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        
        // Complete the task with summary
        TaskQueue queue = TaskQueueHelper.getOrCreateQueue(maid);
        if (currentExploreTask != null) {
            ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
            queue.completeTask(currentExploreTask, true, summary, memory.getStorageCount());
        }
        
        currentExploreTask = null;
    }
}

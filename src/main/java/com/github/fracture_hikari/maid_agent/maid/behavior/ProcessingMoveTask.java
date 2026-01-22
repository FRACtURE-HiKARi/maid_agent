package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingMemory;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Movement behavior for processing collection.
 * Periodically (200 tick cooldown) checks ProcessingMemory and moves to collect outputs.
 * 
 * Triggers when:
 * - Cooldown expired
 * - No active TaskQueue tasks
 * - ProcessingMemory has active jobs with output available
 * 
 * Priority 6 (lower than TaskMoveTask at 5).
 */
public class ProcessingMoveTask extends Behavior<EntityMaid> {
    private static final double CLOSE_ENOUGH = 2.5;
    private static final float WALK_SPEED = 0.6f;
    private static final int COOLDOWN_TICKS = 200;  // 10 seconds
    
    private ProcessingJob targetJob = null;
    private long lastCheckTime = 0;  // Internal cooldown

    public ProcessingMoveTask() {
        super(Map.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED,
                MemoryModuleRegistry.PROCESSING_JOBS.get(), MemoryStatus.REGISTERED
        ), 20, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        long currentTime = level.getGameTime();
        
        // Internal cooldown check
        if (currentTime - lastCheckTime < COOLDOWN_TICKS) {
            return false;
        }
        
        // Don't activate if task queue has pending work
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isPresent() && !queueOpt.get().isEmpty()) {
            return false;
        }
        
        // Already moving
        Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (walkTarget.isPresent()) {
            return false;
        }
        
        // Check for processing jobs with output
        Optional<ProcessingMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) {
            return false;
        }
        
        ProcessingMemory memory = memoryOpt.get();
        List<ProcessingJob> activeJobs = memory.getActiveJobs();
        
        // Find first job with output available
        for (ProcessingJob job : activeJobs) {
            if (hasOutputAvailable(level, job)) {
                targetJob = job;
                lastCheckTime = currentTime;  // Reset cooldown
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (targetJob == null) return;
        
        BlockPos target = targetJob.getOutputEndpoint();
        maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(target));
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, WALK_SPEED, 1);
        
        MaidAgent.LOGGER.info("ProcessingMoveTask: Moving to collect from {} (job: {})", 
                target, targetJob.getExpectedOutput().getItem().getDescription().getString());
    }
    
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (targetJob == null) return false;
        
        // Stop when arrived
        return !hasReachedTarget(maid, targetJob.getOutputEndpoint());
    }
    
    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (targetJob == null) return;
        
        // Keep refreshing movement if needed
        Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (walkTarget.isEmpty()) {
            BlockPos target = targetJob.getOutputEndpoint();
            BehaviorUtils.setWalkAndLookTargetMemories(maid, target, WALK_SPEED, 1);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        targetJob = null;
    }
    
    private boolean hasReachedTarget(EntityMaid maid, BlockPos target) {
        double distSq = maid.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        return distSq < CLOSE_ENOUGH * CLOSE_ENOUGH;
    }
    
    private boolean hasOutputAvailable(ServerLevel level, ProcessingJob job) {
        BlockPos pos = job.getOutputEndpoint();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                .map(inv -> {
                    int slot = job.getOutputSlot();
                    if (slot >= 0 && slot < inv.getSlots()) {
                        return !inv.getStackInSlot(slot).isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }
}

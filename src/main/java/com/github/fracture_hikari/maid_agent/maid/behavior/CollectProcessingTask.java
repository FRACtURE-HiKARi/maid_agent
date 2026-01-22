package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import studio.fantasyit.maid_storage_manager.maid.behavior.base.MaidMoveToBlockTaskWithArrivalMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collection behavior for processing outputs.
 * Only activates when:
 * 1. Maid is within range of a ready processing job
 * 2. Output is available to collect
 * 
 * Partially collects if inventory is full, updates job status accordingly.
 * Movement is handled by ProcessingMoveTask.
 * 
 * Priority 6 (same as ProcessingMoveTask).
 */
public class CollectProcessingTask extends MaidCheckRateTask {
    private static final double CLOSE_ENOUGH = 2.5;
    
    private ProcessingJob currentJob = null;
    private boolean workDone = false;

    public CollectProcessingTask() {
        super(Map.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED,
                MemoryModuleRegistry.PROCESSING_JOBS.get(), MemoryStatus.REGISTERED
        ), 20, 100);  // Short duration - just collect
        super.setMaxCheckRate(100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        // Get processing memory
        Optional<ProcessingMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) {
            return false;
        }
        
        ProcessingMemory memory = memoryOpt.get();
        List<ProcessingJob> readyJobs = memory.getActiveJobs();
        
        // Check if maid is within range of any ready job with output
        for (ProcessingJob job : readyJobs) {
            BlockPos endpoint = job.getOutputEndpoint();
            
            // Check distance
            Vec3 targetV3d = Vec3.atCenterOf(endpoint);
            double distSq = maid.distanceToSqr(targetV3d);
            if (distSq > Math.pow(CLOSE_ENOUGH, 2)) {
                continue;  // Not close enough
            }
            
            // Check if output available
            if (hasOutputAvailable(level, job)) {
                currentJob = job;
                MaidAgent.LOGGER.debug("CollectProcessingTask: Ready to collect from {}", endpoint);
                return true;
            }
        }
        
        return false;
    }
    
    // Helper used in checkExtraStartConditions
    private boolean hasOutputAvailable(ServerLevel level, ProcessingJob job) {
        BlockPos pos = job.getOutputEndpoint();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
        return cap.map(inv -> {
            int slot = job.getOutputSlot();
            if (slot >= 0 && slot < inv.getSlots()) {
                return !inv.getStackInSlot(slot).isEmpty();
            }
            return false;
        }).orElse(false);
    }
    
    // ... inside tick ...
    // Note: I need to replace the whole file or large chunks to fix hasOutputAvailable usage 
    // and the hardcoded slot in tick() and cleanupJobs logic.
    // I will use a larger replacement chunk strategy.

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (workDone || currentJob == null) {
            workDone = true;
            return;
        }
        
        BlockPos endpoint = currentJob.getOutputEndpoint();
        BlockEntity be = level.getBlockEntity(endpoint);
        
        if (be == null) {
            workDone = true;
            return;
        }
        
        // Use capability for generic insert/extract
        be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(inv -> {
            int outputSlot = currentJob.getOutputSlot();
            if (outputSlot < 0 || outputSlot >= inv.getSlots()) return;
            
            ItemStack output = inv.getStackInSlot(outputSlot);
            if (output.isEmpty()) {
                if (currentJob.isFullyCollected()) {
                    currentJob.markCollected();
                    cleanupJobs(maid);
                }
                workDone = true;
                return;
            }
            
            // Try to insert into maid inventory
            IItemHandler maidInv = maid.getAvailableInv(false);
            ItemStack remaining = output.copy();
            
            // Limit to expected count? No, take all available (up to stack limit)
            // But we track against expected count
            
            for (int i = 0; i < maidInv.getSlots(); i++) {
                remaining = maidInv.insertItem(i, remaining, false);
                if (remaining.isEmpty()) break;
            }
            
            int collected = output.getCount() - remaining.getCount();
            
            if (collected > 0) {
                // Update machine slot (extract)
                inv.extractItem(outputSlot, collected, false);
                
                // Track collection
                boolean fullyCollected = currentJob.addCollected(collected);
                
                MaidAgent.LOGGER.info("CollectProcessingTask: Collected {} {} from {} slot {} ({}/{})", 
                        collected, output.getItem().getDescription().getString(), endpoint.toShortString(), outputSlot,
                        currentJob.getCollectedCount(), currentJob.getExpectedCount());
                
                if (fullyCollected) {
                    currentJob.markCollected();
                    cleanupJobs(maid);
                }
            } else {
                MaidAgent.LOGGER.info("CollectProcessingTask: Inventory full, couldn't collect");
            }
        });
        
        workDone = true;
    }
    
    private void cleanupJobs(EntityMaid maid) {
        maid.getBrain().getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get())
                .ifPresent(memory -> {
                    memory.cleanupCompleted();
                    
                    // Check if all jobs done - if so, trigger delayed notification
                    if (!memory.hasActiveJobs()) {
                        maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                                .ifPresent(queue -> {
                                    if (queue.isBatchComplete()) {
                                        queue.notifyBatchComplete();
                                    }
                                });
                    }
                });
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // Clear movement memories
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        
        currentJob = null;
        workDone = false;
    }
}

package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

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
public class CollectMoveTask extends AbstractMoveTask {

    public CollectMoveTask() {
        super(200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid))
            return false;
        Set<ProcessingJob> jobs = MemoryUtil.getJobs(maid);
        return !jobs.isEmpty();
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Set<BlockPos> targets = MemoryUtil.getCollectTarget(maid);
        return targets.contains(pos.immutable());
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTimeIn) {
        this.searchForDestination(level, maid);
    }

    
    // ... inside tick ...
    // Note: I need to replace the whole file or large chunks to fix hasOutputAvailable usage 
    // and the hardcoded slot in tick() and cleanupJobs logic.
    // I will use a larger replacement chunk strategy.

    /*
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
     */
}

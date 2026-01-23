package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

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
@Deprecated
public class ProcessingMoveTask extends AbstractMoveTask {

    public ProcessingMoveTask() {
        super(200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid))
            return false;
        // Check for processing jobs with output
        Optional<JobMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) {
            return false;
        }
        
        JobMemory memory = memoryOpt.get();
        return !memory.getOutputEndpoints().isEmpty();
    }
    
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        searchForDestination(level, maid);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        return false;
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

package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import studio.fantasyit.maid_storage_manager.storage.ItemHandler.SimulateTargetInteractHelper;


import java.util.Optional;
import java.util.Set;

public class CollectWorkTask extends AbstractWorkTask {
    private ProcessingJob currentJob = null;
    public CollectWorkTask() {
        super();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        Set<ProcessingJob> jobs = MemoryUtil.getJobs(maid);
        Optional<ProcessingJob> jobOpt = jobs.stream().filter(job ->
            hasReached(maid, job.getOutputEndpoint().getCenter())).findAny();
        if (jobOpt.isEmpty()) return false;
        currentJob = jobOpt.get();
        helper = new SimulateTargetInteractHelper(maid, currentJob.getOutputEndpoint(), null, level);
        return true;
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        if (currentJob == null) return;
        if (currentJob.isFullyCollected()) {
            MemoryUtil.updateJobs(maid, true, "job done: fully collected.", currentJob);
            return;
        }

        BlockEntity be = level.getBlockEntity(currentJob.getOutputEndpoint());
        if (be == null) return;
        IItemHandler outputInv = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseGet(null);
        int outputSlot = currentJob.getOutputSlot();
        if (outputSlot < 0 || outputSlot >= outputInv.getSlots()) {
            MemoryUtil.updateJobs(maid, false, "job fail: invalid output slot.", currentJob);
            currentJob = null;
            return;
        }

        ItemStack output = outputInv.extractItem(outputSlot, currentJob.getRemainingToCollect(), false);
        IItemHandler maidInv = maid.getAvailableInv(false);
        InventoryUtils.mergeSameStack(maidInv);
        ItemStack remains = InventoryUtils.insertAll(maidInv, output);
        currentJob.addCollected(output.getCount() - remains.getCount());
        if (!remains.isEmpty()) {
            outputInv.insertItem(outputSlot, remains, false);
            MemoryUtil.updateJobs(maid, false, "job fail: maid inventory full", currentJob);
        }
    }


}

package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.ExploreTask;
import com.github.fracture_hikari.maid_agent.maid.memory.FetchTask;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.StoreTask;
import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;

/**
 * Behavior for FETCH, STORE, and EXPLORE tasks.
 * Handles item transfer between maid inventory and storage containers.
 * EXPLORE task reads storage contents and updates ViewedStorageMemory.
 */
public class StorageWorkTask extends AbstractWorkTask {

    public StorageWorkTask() {
        super();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        return checkTaskMemory(
                level,
                maid,
                task -> task instanceof FetchTask
                        || task instanceof StoreTask
        );
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return;

        PendingTask task = taskOpt.get();
        WorkBlockTarget target = task.getTarget().orElse(null);
        if (target == null) {
            MemoryUtil.updateTasks(maid, task, false, "no target storage found");
            return;
        }

        BlockPos pos = target.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            MemoryUtil.updateTasks(maid, task, false, "no storage at " + pos);
            return;
        }

        IItemHandler storage = be.getCapability(ForgeCapabilities.ITEM_HANDLER, target.getSideOrNull()).orElse(null);
        if (storage == null) {
            MemoryUtil.updateTasks(maid, task, false, "storage has no inventory");
            return;
        }

        // Handle FETCH/STORE tasks
        handleTransfer(maid, task, storage);
    }
    
    /**
     * Handle FETCH/STORE task - transfer items between maid and storage.
     */
    private void handleTransfer(EntityMaid maid, PendingTask task, IItemHandler storage) {
        ItemStack targetItem = task.getRequestedItem().copy();
        String targetItemId = ItemIdUtils.getId(targetItem);
        if (targetItem.isEmpty()) {
            MemoryUtil.updateTasks(maid, task, false, "unknown item " + targetItemId);
            return;
        }

        IItemHandler maidInv = maid.getAvailableInv(false);
        boolean isFetch = (task instanceof FetchTask);

        // 1. Determine Source and Destination based on task type
        IItemHandler source = isFetch ? storage : maidInv;
        IItemHandler destination = isFetch ? maidInv : storage;

        // 2. Run the unified transfer logic
        int amountModified = InventoryUtils.transferItems(source, destination, targetItem, task.getCount());

        // 3. Post-processing (Updating task status and returning strings)
        String itemName = getItemName(targetItem);

        if (amountModified > 0) {
            // Specific logic for STORE tasks from your original code
            if (!isFetch) {
                task.setActualCount(amountModified);
            }
            String action = isFetch ? "Fetched" : "Stored";
            String location = isFetch ? "from storage" : "in storage";
            String msg = String.format("%s %d %s %s", action, amountModified, itemName, location);
            MemoryUtil.updateTasks(maid, task, true, msg);
        } else {
            // Generalized failure message
            String reason = isFetch ? "(not found in storage)" : "(item not in inventory or storage full)";
            String msg = String.format("Could not %s any %s %s", isFetch ? "fetch" : "store", itemName, reason);
            MemoryUtil.updateTasks(maid, task, false, msg);
        }
    }
}

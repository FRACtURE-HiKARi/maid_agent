package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;

/**
 * Behavior for FETCH and STORE tasks.
 * Handles item transfer between maid inventory and storage containers.
 */
public class StorageWorkTask extends AbstractWorkTask {

    public StorageWorkTask() {
        super();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        PendingTask task = taskOpt.get();
        return task.getType() == PendingTask.TaskType.FETCH ||
                task.getType() == PendingTask.TaskType.STORE;
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return;

        PendingTask task = taskOpt.get();
        WorkBlockTarget target = task.getTarget().orElse(null);
        if (target == null) {
            MemoryUtil.updateTasks(maid, false, "no target storage found");
            return;
        }

        ItemStack targetItem = ItemIdUtils.createStack(task.getItemId());
        if (targetItem.isEmpty()) {
            MemoryUtil.updateTasks(maid, false, "Failed to fetch: unknown item " + task.getItemId());
            return;
        }

        BlockEntity be = level.getBlockEntity(target.getPos());
        if (be == null) {
            MemoryUtil.updateTasks(maid, false, "Failed to fetch: no storage at " + target.getPos() + task.getItemId());
            return;
        }

        IItemHandler storage = be.getCapability(ForgeCapabilities.ITEM_HANDLER, target.getSideOrNull()).orElse(null);
        if (storage == null) {
            MemoryUtil.updateTasks(maid, false, "Failed to fetch: storage has no inventory");
            return;
        }

        IItemHandler maidInv = maid.getAvailableInv(false);
        boolean isFetch = (task.getType() == PendingTask.TaskType.FETCH);

        // 1. Determine Source and Destination based on task type
        IItemHandler source = isFetch ? storage : maidInv;
        IItemHandler destination = isFetch ? maidInv : storage;

        // 2. Run the unified transfer logic
        int amountModified = transferItems(source, destination, targetItem, task.getCount());

        // 3. Post-processing (Updating task status and returning strings)
        String itemName = getItemName(targetItem);

        if (amountModified > 0) {
            // Specific logic for STORE tasks from your original code
            if (!isFetch) {
                task.setActualAccount(amountModified);
            }
            String action = isFetch ? "Fetched" : "Stored";
            String location = isFetch ? "from storage" : "in storage";
            String msg = String.format("%s %d %s %s", action, amountModified, itemName, location);
            MemoryUtil.updateTasks(maid, true, msg);
        } else {
            // Generalized failure message
            String reason = isFetch ? "(not found in storage)" : "(item not in inventory or storage full)";
            String msg =  String.format("Could not %s any %s %s", isFetch ? "fetch" : "store", itemName, reason);
            MemoryUtil.updateTasks(maid, false, msg);
        }
    }
}

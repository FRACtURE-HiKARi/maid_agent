package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Behavior for FETCH and STORE tasks.
 * Handles item transfer between maid inventory and storage containers.
 */
public class StorageWorkTask extends AbstractWorkTask {

    public StorageWorkTask() {
        super();
    }

    @Override
    protected boolean canHandle(PendingTask task) {
        return task.getType() == PendingTask.TaskType.FETCH 
            || task.getType() == PendingTask.TaskType.STORE;
    }

    @Override
    protected String performWork(ServerLevel level, EntityMaid maid, PendingTask task) {
        if (task.getType() == PendingTask.TaskType.FETCH) {
            return performFetch(level, maid, task);
        } else {
            return performStore(level, maid, task);
        }
    }

    private String performFetch(ServerLevel level, EntityMaid maid, PendingTask task) {
        WorkBlockTarget target = task.getTarget();
        if (target == null) {
            task.fail("No target");
            return "Failed to fetch: no target storage found";
        }

        ItemStack targetItem = ItemIdUtils.createStack(task.getItemId());
        if (targetItem.isEmpty()) {
            task.fail("Unknown item");
            return "Failed to fetch: unknown item " + task.getItemId();
        }

        BlockEntity be = level.getBlockEntity(target.getPos());
        if (be == null) {
            task.fail("No block entity");
            return "Failed to fetch: no storage at " + target.getPos();
        }

        IItemHandler storage = be.getCapability(ForgeCapabilities.ITEM_HANDLER, target.getSideOrNull())
                .orElse(null);
        if (storage == null) {
            task.fail("No inventory capability");
            return "Failed to fetch: storage has no inventory";
        }

        IItemHandler maidInv = maid.getAvailableInv(false);
        int fetched = 0;
        int needed = task.getCount();

        // Extract from storage
        for (int slot = 0; slot < storage.getSlots() && fetched < needed; slot++) {
            ItemStack inSlot = storage.getStackInSlot(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItem(inSlot, targetItem)) continue;

            int toExtract = Math.min(needed - fetched, inSlot.getCount());
            ItemStack extracted = storage.extractItem(slot, toExtract, false);
            if (extracted.isEmpty()) continue;

            // Insert into maid inventory
            for (int i = 0; i < maidInv.getSlots(); i++) {
                extracted = maidInv.insertItem(i, extracted, false);
                if (extracted.isEmpty()) break;
            }

            fetched += toExtract - extracted.getCount();
        }

        if (fetched > 0) {
            task.complete("Fetched items", fetched);
            return String.format("Fetched %d %s from storage", fetched, getItemName(targetItem));
        } else {
            task.fail("No items found");
            return "Could not fetch any " + getItemName(targetItem) + " (not found in storage)";
        }
    }

    private String performStore(ServerLevel level, EntityMaid maid, PendingTask task) {
        WorkBlockTarget target = task.getTarget();
        if (target == null) {
            task.fail("No target");
            return "Failed to store: no target storage found";
        }

        ItemStack targetItem = ItemIdUtils.createStack(task.getItemId());
        if (targetItem.isEmpty()) {
            task.fail("Unknown item");
            return "Failed to store: unknown item " + task.getItemId();
        }

        BlockEntity be = level.getBlockEntity(target.getPos());
        if (be == null) {
            task.fail("No block entity");
            return "Failed to store: no storage at " + target.getPos();
        }

        IItemHandler storage = be.getCapability(ForgeCapabilities.ITEM_HANDLER, target.getSideOrNull())
                .orElse(null);
        if (storage == null) {
            task.fail("No inventory capability");
            return "Failed to store: storage has no inventory";
        }

        IItemHandler maidInv = maid.getAvailableInv(false);
        int stored = 0;
        int toStore = task.getCount();

        // Extract from maid, insert to storage
        for (int slot = 0; slot < maidInv.getSlots() && stored < toStore; slot++) {
            ItemStack inSlot = maidInv.getStackInSlot(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItem(inSlot, targetItem)) continue;

            int toExtract = Math.min(toStore - stored, inSlot.getCount());
            ItemStack extracted = maidInv.extractItem(slot, toExtract, false);
            if (extracted.isEmpty()) continue;

            // Insert into storage
            for (int i = 0; i < storage.getSlots(); i++) {
                extracted = storage.insertItem(i, extracted, false);
                if (extracted.isEmpty()) break;
            }

            int actuallyStored = toExtract - extracted.getCount();
            stored += actuallyStored;

            // Put back leftover
            if (!extracted.isEmpty()) {
                for (int i = 0; i < maidInv.getSlots(); i++) {
                    extracted = maidInv.insertItem(i, extracted, false);
                    if (extracted.isEmpty()) break;
                }
            }
        }

        if (stored > 0) {
            task.complete("Stored items", stored);
            return String.format("Stored %d %s in storage", stored, getItemName(targetItem));
        } else {
            task.fail("No items stored");
            return "Could not store any " + getItemName(targetItem) + 
                    " (item not in inventory or storage full)";
        }
    }
}

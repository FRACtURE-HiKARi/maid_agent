package com.github.fracture_hikari.maid_agent.maid.memory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Task to store items into storage.
 */
public class StoreTask extends PendingTask {
    public static final String TYPE_NAME = "STORE";

    public StoreTask(ItemStack requestedItem) {
        super(requestedItem);
    }

    public StoreTask(ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        super(requestedItem, target);
    }

    public StoreTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, @Nullable String toolCallId) {
        super(requestedItem, target, toolCallId);
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

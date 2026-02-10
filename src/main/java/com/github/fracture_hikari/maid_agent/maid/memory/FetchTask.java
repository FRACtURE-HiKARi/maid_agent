package com.github.fracture_hikari.maid_agent.maid.memory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Task to fetch items from storage.
 */
public class FetchTask extends PendingTask {
    public static final String TYPE_NAME = "FETCH";

    public FetchTask(ItemStack requestedItem) {
        super(requestedItem);
    }

    public FetchTask(ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        super(requestedItem, target);
    }

    public FetchTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, @Nullable String toolCallId) {
        super(requestedItem, target, toolCallId);
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

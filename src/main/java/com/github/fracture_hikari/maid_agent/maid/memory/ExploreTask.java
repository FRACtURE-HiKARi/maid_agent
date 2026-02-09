package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Task to explore a single storage (legacy).
 */
public class ExploreTask extends PendingTask {
    public static final String TYPE_NAME = "EXPLORE";

    public ExploreTask(ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        super(requestedItem, target);
    }

    public ExploreTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, @Nullable String toolCallId) {
        super(requestedItem, target, toolCallId);
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

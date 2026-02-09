package com.github.fracture_hikari.maid_agent.maid.memory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Task to dynamically explore all nearby storage containers.
 */
public class ExploreAllTask extends PendingTask {
    public static final String TYPE_NAME = "EXPLORE_ALL";

    private int explorationRadius = 16;

    public ExploreAllTask(@Nullable String toolCallId) {
        super(ItemStack.EMPTY, null, toolCallId);
    }

    public ExploreAllTask(int radius, @Nullable String toolCallId) {
        super(ItemStack.EMPTY, null, toolCallId);
        this.explorationRadius = Math.min(radius, 32);
    }

    public int getExplorationRadius() {
        return explorationRadius;
    }

    public void setExplorationRadius(int radius) {
        this.explorationRadius = Math.min(radius, 32);
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

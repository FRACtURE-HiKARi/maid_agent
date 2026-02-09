package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Task for instant crafting (crafting table).
 */
public class CraftTask extends PendingTask {
    public static final String TYPE_NAME = "CRAFT";

    public enum WorkstationType {
        CRAFTING_TABLE
    }

    private WorkstationType workstationType = WorkstationType.CRAFTING_TABLE;
    @Nullable
    private String recipeId;

    public CraftTask(ItemStack requestedItem) {
        super(requestedItem);
    }

    public CraftTask(ItemStack requestedItem, @Nullable WorkBlockTarget target) {
        super(requestedItem, target);
    }

    public CraftTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, @Nullable String recipeId) {
        super(requestedItem, target);
        this.recipeId = recipeId;
    }

    public WorkstationType getWorkstationType() {
        return workstationType;
    }

    public void setWorkstationType(WorkstationType workstationType) {
        this.workstationType = workstationType;
    }

    @Nullable
    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(@Nullable String recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

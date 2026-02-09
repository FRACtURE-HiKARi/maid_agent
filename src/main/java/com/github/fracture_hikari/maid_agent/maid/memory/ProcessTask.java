package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Task for processing items (furnace, smoker, blast furnace).
 * Two-phase: insert ingredients → wait → collect output.
 */
public class ProcessTask extends PendingTask {
    public static final String TYPE_NAME = "PROCESS";

    public enum WorkstationType {
        FURNACE,
        SMOKER,
        BLAST_FURNACE
    }

    private WorkstationType workstationType;
    @Nullable
    private String recipeId;
    @Nullable
    private List<SlotMapping> slotMappings;

    public ProcessTask(ItemStack requestedItem, WorkstationType workstationType) {
        super(requestedItem);
        this.workstationType = workstationType;
    }

    public ProcessTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, WorkstationType workstationType) {
        super(requestedItem, target);
        this.workstationType = workstationType;
    }

    public ProcessTask(ItemStack requestedItem, @Nullable WorkBlockTarget target, 
                       WorkstationType workstationType, @Nullable String recipeId) {
        super(requestedItem, target);
        this.workstationType = workstationType;
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

    @Nullable
    public List<SlotMapping> getSlotMappings() {
        return slotMappings;
    }

    public void setSlotMappings(@Nullable List<SlotMapping> slotMappings) {
        this.slotMappings = slotMappings;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}

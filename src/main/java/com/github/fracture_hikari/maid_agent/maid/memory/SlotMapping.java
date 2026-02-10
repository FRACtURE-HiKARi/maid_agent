package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a single item-to-slot mapping for processing recipes.
 * Used to tell InsertProcessingTask exactly where each item should go.
 * 
 * For furnaces:
 * - slot 0 = input (ingredient to smelt)
 * - slot 1 = fuel
 * - slot 2 = output (read-only)
 */
public record SlotMapping(
    BlockPos workstation,  // Machine position
    int slot,              // Target slot index
    ItemStack item         // Item to insert (includes count)
) {
    /**
     * Copy constructor to prevent mutation.
     */
    public SlotMapping {
        item = item.copy();
    }
    
    /**
     * Get item ID for display/logging.
     */
    public String getItemId() {
        return ItemIdUtils.getId(item);
    }
    
    /**
     * Get item count.
     */
    public int getCount() {
        return item.getCount();
    }
    
    @Override
    public @NotNull String toString() {
        return String.format("SlotMapping{slot=%d, item=%s x%d, at=%s}", 
                slot, getItemId(), getCount(), workstation.toShortString());
    }
}

package com.github.fracture_hikari.maid_agent.util;

import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for inventory operations.
 * Provides methods to aggregate and analyze inventories.
 */
public final class InventoryUtils {
    
    private InventoryUtils() {} // Prevent instantiation
    
    /**
     * Aggregate IItemHandler contents to Map<itemId, count>.
     * Merges stacks of the same item type.
     */
    public static Map<String, Integer> aggregate(IItemHandler handler) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String id = ItemIdUtils.getId(stack);
                result.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return result;
    }
    
    /**
     * Aggregate IItemHandler with display names.
     */
    public static Map<String, ItemInfo> aggregateWithNames(IItemHandler handler) {
        Map<String, ItemInfo> result = new HashMap<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String id = ItemIdUtils.getId(stack);
                String name = stack.getHoverName().getString();
                int count = stack.getCount();
                
                result.merge(id, new ItemInfo(name, id, count),
                        (existing, newInfo) -> new ItemInfo(existing.displayName, existing.itemId, 
                                existing.count + newInfo.count));
            }
        }
        return result;
    }
    
    /**
     * Get maid's available inventory as Map<itemId, count>.
     * @param includeHand if true, includes main hand item
     */
    public static Map<String, Integer> getMaidInventory(EntityMaid maid, boolean includeHand) {
        IItemHandler handler = maid.getAvailableInv(includeHand);
        return aggregate(handler);
    }
    
    /**
     * Get maid's available inventory (including hand).
     */
    public static Map<String, Integer> getMaidInventory(EntityMaid maid) {
        return getMaidInventory(maid, true);
    }
    
    /**
     * Get storage inventory from maid's brain memory.
     * Aggregates all viewed storages into single map.
     */
    public static Map<String, Integer> getStorageInventory(EntityMaid maid) {
        Map<String, Integer> inventory = new HashMap<>();
        maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .ifPresent(memory -> {
                    for (int i = 0; i < memory.getStorageCount(); i++) {
                        memory.getStorageByIndex(i).ifPresent(target -> {
                            for (ViewedStorageMemory.ItemCount ic : memory.getContents(target)) {
                                String id = ItemIdUtils.getId(ic.item());
                                inventory.merge(id, ic.count(), Integer::sum);
                            }
                        });
                    }
                });
        return inventory;
    }
    
    /**
     * Get staleness of storage memory in seconds.
     * @return seconds since last update, or 999 if no memory
     */
    public static int getStorageStaleness(EntityMaid maid, ServerLevel level) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .map(m -> m.getStalenessSeconds(level.getGameTime()))
                .orElse(999);
    }
    
    /**
     * Count total items of a specific type in handler.
     */
    public static int countItem(IItemHandler handler, String itemId) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && ItemIdUtils.getId(stack).equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    /**
     * Item information with display name and count.
     */
    public record ItemInfo(String displayName, String itemId, int count) {}
}

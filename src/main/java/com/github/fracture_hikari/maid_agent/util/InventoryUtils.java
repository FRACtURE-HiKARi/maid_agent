package com.github.fracture_hikari.maid_agent.util;

import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studio.fantasyit.maid_storage_manager.util.InvUtil;

/**
 * Utility class for inventory operations.
 * Provides methods to aggregate and analyze inventories using List<ItemStack>.
 */
public final class InventoryUtils extends InvUtil {
    
    private InventoryUtils() {} // Prevent instantiation
    
    /**
     * Merge an ItemStack into a list, aggregating counts for same item types.
     */
    private static void mergeStack(List<ItemStack> list, ItemStack stack) {
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        list.add(stack.copy());
    }
    
    /**
     * Aggregate IItemHandler contents to List<ItemStack>.
     * Each ItemStack in the result has merged count for that item type.
     */
    public static List<ItemStack> aggregate(IItemHandler handler) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                mergeStack(result, stack);
            }
        }
        return result;
    }
    
    /**
     * Convert List<ItemStack> to Map<itemId, count> for backward compatibility.
     */
    public static Map<String, Integer> toItemCountMap(List<ItemStack> stacks) {
        Map<String, Integer> map = new HashMap<>();
        for (ItemStack stack : stacks) {
            map.merge(ItemIdUtils.getId(stack), stack.getCount(), Integer::sum);
        }
        return map;
    }
    
    /**
     * Aggregate IItemHandler contents to Map<itemId, count>.
     * @deprecated Use aggregate() and toItemCountMap() for new code.
     */
    @Deprecated
    public static Map<String, Integer> aggregateToMap(IItemHandler handler) {
        return toItemCountMap(aggregate(handler));
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
     * Get maid's available inventory as List<ItemStack>.
     * @param includeHand if true, includes main hand item
     */
    public static List<ItemStack> getMaidInventory(EntityMaid maid, boolean includeHand) {
        IItemHandler handler = maid.getAvailableInv(includeHand);
        return aggregate(handler);
    }
    
    /**
     * Get maid's available inventory (including hand).
     */
    public static List<ItemStack> getMaidInventory(EntityMaid maid) {
        return getMaidInventory(maid, true);
    }
    
    /**
     * Get maid's available inventory as Map<itemId, count>.
     * @deprecated Use getMaidInventory() and toItemCountMap() for new code.
     */
    @Deprecated
    public static Map<String, Integer> getMaidInventoryAsMap(EntityMaid maid, boolean includeHand) {
        return toItemCountMap(getMaidInventory(maid, includeHand));
    }
    
    /**
     * Get maid's available inventory as Map<itemId, count>.
     * @deprecated Use getMaidInventory() and toItemCountMap() for new code.
     */
    @Deprecated
    public static Map<String, Integer> getMaidInventoryAsMap(EntityMaid maid) {
        return getMaidInventoryAsMap(maid, true);
    }
    
    /**
     * Get storage inventory from maid's brain memory as List<ItemStack>.
     * Aggregates all viewed storages into single list.
     */
    public static List<ItemStack> getStorageInventory(EntityMaid maid) {
        List<ItemStack> inventory = new ArrayList<>();
        maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .ifPresent(memory -> {
                    for (int i = 0; i < memory.getStorageCount(); i++) {
                        memory.getStorageByIndex(i).ifPresent(target -> {
                            for (ItemStack stack : memory.getContents(target)) {
                                mergeStack(inventory, stack);
                            }
                        });
                    }
                });
        return inventory;
    }
    
    /**
     * Get storage inventory from maid's brain memory as Map<itemId, count>.
     * @deprecated Use getStorageInventory() and toItemCountMap() for new code.
     */
    @Deprecated
    public static Map<String, Integer> getStorageInventoryAsMap(EntityMaid maid) {
        return toItemCountMap(getStorageInventory(maid));
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

    public static ItemStack insertAll(IItemHandler handler, ItemStack stack) {
        ItemStack remains = stack;
        for (int s = 0; s < handler.getSlots(); s++) {
            remains = handler.insertItem(s, remains, false);
            if (remains.isEmpty()) return ItemStack.EMPTY;
        }
        return remains;
    }

    /**
     * Moves items from source to destination safely.
     * Logic: Extract from Source -> Insert into Dest -> Return leftovers to Source if Dest is full.
     */
    public static int transferItems(IItemHandler source, IItemHandler dest, ItemStack matcher, int maxAmount) {
        int totalMoved = 0;

        for (int i = 0; i < source.getSlots() && totalMoved < maxAmount; i++) {
            ItemStack inSlot = source.getStackInSlot(i);
            if (inSlot.isEmpty() || !ItemStack.isSameItem(inSlot, matcher)) continue;

            // Calculate how much we want to move from this slot
            int wantToMove = Math.min(maxAmount - totalMoved, inSlot.getCount());

            // 1. Extract from Source
            ItemStack extracted = source.extractItem(i, wantToMove, false);
            if (extracted.isEmpty()) continue;

            int originalExtractedCount = extracted.getCount();

            // 2. Insert into Destination
            for (int j = 0; j < dest.getSlots(); j++) {
                extracted = dest.insertItem(j, extracted, false);
                if (extracted.isEmpty()) break;
            }

            // 3. Calculate actual success amount
            int successfullyMoved = originalExtractedCount - extracted.getCount();
            totalMoved += successfullyMoved;

            // 4. Return leftovers to Source (Safety check)
            if (!extracted.isEmpty()) {
                for (int k = 0; k < source.getSlots(); k++) {
                    extracted = source.insertItem(k, extracted, false);
                    if (extracted.isEmpty()) break;
                }
            }
        }
        return totalMoved;
    }


    /**
     * Item information with display name and count.
     */
    public record ItemInfo(String displayName, String itemId, int count) {}
}

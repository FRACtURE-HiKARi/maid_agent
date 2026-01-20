package com.github.fracture_hikari.maid_agent.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * Utility class for item ID operations.
 * Centralizes the common patterns of parsing and formatting item IDs.
 */
public final class ItemIdUtils {
    
    private ItemIdUtils() {} // Prevent instantiation
    
    /**
     * Get item ID string from ItemStack.
     * Example: "minecraft:diamond"
     */
    public static String getId(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().key().location().toString();
    }
    
    /**
     * Get item ID string from Item.
     * Example: "minecraft:diamond"
     */
    public static String getId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
    
    /**
     * Parse item ID string to ResourceLocation.
     * @return ResourceLocation or null if invalid
     */
    @Nullable
    public static ResourceLocation parse(String itemId) {
        return ResourceLocation.tryParse(itemId);
    }
    
    /**
     * Parse item ID and get the Item.
     * @return Item or null if invalid/not found
     */
    @Nullable
    public static Item getItem(String itemId) {
        ResourceLocation rl = parse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(rl);
    }
    
    /**
     * Create ItemStack from ID string.
     * @return ItemStack or EMPTY if invalid
     */
    public static ItemStack createStack(String itemId, int count) {
        Item item = getItem(itemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }
    
    /**
     * Create ItemStack with count 1.
     */
    public static ItemStack createStack(String itemId) {
        return createStack(itemId, 1);
    }
    
    /**
     * Check if item ID string is valid and exists in registry.
     */
    public static boolean isValid(String itemId) {
        ResourceLocation rl = parse(itemId);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }
    
    /**
     * Get display name for an item ID.
     * @return Display name or the itemId itself if not found
     */
    public static String getDisplayName(String itemId) {
        Item item = getItem(itemId);
        if (item == null) {
            return itemId;
        }
        return new ItemStack(item).getHoverName().getString();
    }
}

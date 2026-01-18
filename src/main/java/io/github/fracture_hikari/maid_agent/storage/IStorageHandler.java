package io.github.fracture_hikari.maid_agent.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for storage type handlers.
 * Implementations handle different types of storage blocks (chests, AE2, RS, etc.)
 * 
 * Other mods can register their own implementations via StorageManager.
 */
public interface IStorageHandler {
    
    /**
     * Get the unique type identifier for this storage handler.
     */
    ResourceLocation getType();

    /**
     * Check if this handler can handle the given block position.
     * 
     * @param level The server level
     * @param pos The block position to check
     * @param side Optional side to access from (null for default/all sides)
     * @return true if this handler can work with the block at the given position
     */
    boolean isValidTarget(ServerLevel level, BlockPos pos, @Nullable Direction side);

    /**
     * Get all items currently in the storage at the given position.
     * 
     * @param level The server level
     * @param pos The storage block position
     * @param side Optional side to access from
     * @return List of ItemStacks in the storage (may include empty stacks)
     */
    List<ItemStack> getContents(ServerLevel level, BlockPos pos, @Nullable Direction side);

    /**
     * Extract items from the storage.
     * 
     * @param level The server level
     * @param pos The storage block position
     * @param side Optional side to access from
     * @param target The item to extract (only type and NBT are matched)
     * @param maxCount Maximum number of items to extract
     * @return The extracted ItemStack (may be empty if nothing was extracted)
     */
    ItemStack extractItem(ServerLevel level, BlockPos pos, @Nullable Direction side, 
                          ItemStack target, int maxCount);

    /**
     * Insert items into the storage.
     * 
     * @param level The server level
     * @param pos The storage block position
     * @param side Optional side to access from
     * @param stack The items to insert
     * @return The remaining items that couldn't be inserted (empty if all were inserted)
     */
    ItemStack insertItem(ServerLevel level, BlockPos pos, @Nullable Direction side, 
                         ItemStack stack);

    /**
     * Check if this storage supports extracting items.
     */
    default boolean supportsExtract() {
        return true;
    }

    /**
     * Check if this storage supports inserting items.
     */
    default boolean supportsInsert() {
        return true;
    }
}

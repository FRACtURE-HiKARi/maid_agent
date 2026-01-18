package com.github.fracture_hikari.maid_agent.storage.handler;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.storage.IStorageHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Default storage handler for vanilla IItemHandler capability.
 * Handles chests, barrels, hoppers, and any block with IItemHandler capability.
 */
public class ItemHandlerStorageHandler implements IStorageHandler {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "item_handler");

    @Override
    public ResourceLocation getType() {
        return TYPE;
    }

    @Override
    public boolean isValidTarget(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        LazyOptional<IItemHandler> cap = side == null
                ? blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        return cap.isPresent();
    }

    @Override
    public List<ItemStack> getContents(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        List<ItemStack> contents = new ArrayList<>();
        getItemHandler(level, pos, side).ifPresent(handler -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    contents.add(stack.copy());
                }
            }
        });
        return contents;
    }

    @Override
    public ItemStack extractItem(ServerLevel level, BlockPos pos, @Nullable Direction side,
                                 ItemStack target, int maxCount) {
        return getItemHandler(level, pos, side).map(handler -> {
            int remaining = maxCount;
            ItemStack result = ItemStack.EMPTY;

            for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
                ItemStack slotStack = handler.getStackInSlot(i);
                if (ItemStack.isSameItemSameTags(slotStack, target)) {
                    int toExtract = Math.min(remaining, slotStack.getCount());
                    ItemStack extracted = handler.extractItem(i, toExtract, false);
                    if (!extracted.isEmpty()) {
                        if (result.isEmpty()) {
                            result = extracted;
                        } else {
                            result.grow(extracted.getCount());
                        }
                        remaining -= extracted.getCount();
                    }
                }
            }
            return result;
        }).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack insertItem(ServerLevel level, BlockPos pos, @Nullable Direction side,
                                ItemStack stack) {
        return getItemHandler(level, pos, side).map(handler -> {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            return remaining;
        }).orElse(stack);
    }

    private LazyOptional<IItemHandler> getItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return LazyOptional.empty();
        }
        return side == null
                ? blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
    }
}

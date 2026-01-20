package com.github.fracture_hikari.maid_agent.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a storage block location with type, position, and optional access direction.
 * Similar to maid_storage_manager's Target class.
 */
public class StorageTarget {
    private final ResourceLocation type;
    private final BlockPos pos;
    @Nullable
    private final Direction side;

    public StorageTarget(ResourceLocation type, BlockPos pos) {
        this(type, pos, (Direction) null);
    }

    public StorageTarget(ResourceLocation type, BlockPos pos, @Nullable Direction side) {
        this.type = type;
        this.pos = pos;
        this.side = side;
    }
    
    public StorageTarget(ResourceLocation type, BlockPos pos, Optional<Direction> side) {
        this(type, pos, side.orElse(null));
    }

    public ResourceLocation getType() {
        return type;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Optional<Direction> getSide() {
        return Optional.ofNullable(side);
    }

    @Nullable
    public Direction getSideOrNull() {
        return side;
    }

    /**
     * Create a new target with the same type but different position/side.
     */
    public StorageTarget withPos(BlockPos newPos, @Nullable Direction newSide) {
        return new StorageTarget(type, newPos, newSide);
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", type.toString());
        nbt.putLong("pos", pos.asLong());
        if (side != null) {
            nbt.putString("side", side.getName());
        }
        return nbt;
    }

    public static StorageTarget fromNbt(CompoundTag nbt) {
        ResourceLocation type = ResourceLocation.parse(nbt.getString("type"));
        BlockPos pos = BlockPos.of(nbt.getLong("pos"));
        Direction side = nbt.contains("side") ? Direction.byName(nbt.getString("side")) : null;
        return new StorageTarget(type, pos, side);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageTarget that = (StorageTarget) o;
        return Objects.equals(type, that.type) &&
               Objects.equals(pos, that.pos) &&
               side == that.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, pos, side);
    }

    @Override
    public String toString() {
        return String.format("Storage[%s] at [%d, %d, %d]%s",
                type,
                pos.getX(), pos.getY(), pos.getZ(),
                side != null ? " (" + side.getName() + ")" : "");
    }
}

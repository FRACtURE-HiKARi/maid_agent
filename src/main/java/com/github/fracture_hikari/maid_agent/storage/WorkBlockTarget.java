package com.github.fracture_hikari.maid_agent.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import studio.fantasyit.maid_storage_manager.storage.Target;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a storage block location with type, position, and optional access direction.
 * Similar to maid_storage_manager's Target class.
 */
public class WorkBlockTarget {
    private final ResourceLocation type;
    private final BlockPos pos;
    @Nullable
    private final Direction side;

    public WorkBlockTarget(ResourceLocation type, @Nullable BlockPos pos) {
        this(type, pos, null);
    }

    public WorkBlockTarget(ResourceLocation type, @Nullable BlockPos pos, @Nullable Direction side) {
        this.type = type;
        this.pos = pos;
        this.side = side;
    }

    public WorkBlockTarget(Target msmTarget) {
        this(msmTarget.getType(), msmTarget.getPos(), msmTarget.getSide().orElse(null));
    }

    public ResourceLocation getType() {
        return type;
    }

    @Nullable
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
    public WorkBlockTarget withPos(@Nullable BlockPos newPos, @Nullable Direction newSide) {
        return new WorkBlockTarget(type, newPos, newSide);
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", type.toString());
        if (pos != null) {
            nbt.putLong("pos", pos.asLong());
        }
        if (side != null) {
            nbt.putString("side", side.getName());
        }
        return nbt;
    }

    public static WorkBlockTarget fromNbt(CompoundTag nbt) {
        ResourceLocation type = ResourceLocation.parse(nbt.getString("type"));
        BlockPos pos = nbt.contains("pos") ? BlockPos.of(nbt.getLong("pos")) : null;
        Direction side = nbt.contains("side") ? Direction.byName(nbt.getString("side")) : null;
        return new WorkBlockTarget(type, pos, side);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkBlockTarget that = (WorkBlockTarget) o;
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
        String posStr = (pos != null) ? String.format(" at [%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()) : " (any)";
        return String.format("Storage[%s]%s%s",
                type,
                posStr,
                side != null ? " (" + side.getName() + ")" : "");
    }
}

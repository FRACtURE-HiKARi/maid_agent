package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for work tasks.
 * Handles common movement, targeting, and task queue management.
 * Subclasses implement canHandle() to filter task types and performWork() for actual work.
 */
public abstract class AbstractWorkTask extends MaidCheckRateTask {
    protected static final double CLOSE_ENOUGH = 2.5;
    protected static final float WALK_SPEED = 0.6f;

    public AbstractWorkTask() {
        this(100, 200);
    }
    
    public AbstractWorkTask(int minDuration, int maxDuration) {
        super(Map.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED,
                MemoryModuleRegistry.PROCESSING_JOBS.get(), MemoryStatus.REGISTERED
        ), minDuration, maxDuration);
    }
    
    /**
     * Perform the actual work. Called when maid has reached target.
     * @return Result message describing what happened
     */
    protected abstract void handle(ServerLevel level, EntityMaid maid);

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        return hasReachedTarget(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long pTime) {
        handle(level, maid);
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    protected boolean hasReached(EntityMaid maid, Vec3 targetV3d) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            double distSq = maid.distanceToSqr(targetV3d);
            boolean arrived = distSq < Math.pow(CLOSE_ENOUGH, 2);

            if (!arrived) {
                Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty()) {
                    BlockPos targetBlockPos = BlockPos.containing(targetV3d);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, targetBlockPos, WALK_SPEED, 1);
                }
            }
            return arrived;
        }).orElse(false);
    }

    protected boolean hasReachedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            return hasReached(maid, targetV3d);
        }).orElse(false);
    }
    
    /**
     * Helper to get item display name.
     */
    protected String getItemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    /**
     * Moves items from source to destination safely.
     * Logic: Extract from Source -> Insert into Dest -> Return leftovers to Source if Dest is full.
     */
    protected int transferItems(IItemHandler source, IItemHandler dest, ItemStack matcher, int maxAmount) {
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
}

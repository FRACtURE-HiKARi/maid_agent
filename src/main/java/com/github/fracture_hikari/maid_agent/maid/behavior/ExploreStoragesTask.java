package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.maid.memory.ExploreAllTask;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.maid.memory.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;

// MSM imports
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;

/**
 * Behavior task for EXPLORE_ALL task type.
 *
 * Dynamically finds and explores all nearby storage blocks.
 * Uses boredom timeout from {@link AbstractExploreTask} — if no new storage
 * is reached within the timeout, exploration ends.
 */
public class ExploreStoragesTask extends AbstractExploreTask {

    private ExploreAllTask currentExploreTask;

    public ExploreStoragesTask() {
        super(); // default boredom timeout (400 ticks / 20 seconds)
    }

    @Override
    protected boolean configureFromTask(PendingTask task) {
        if (!(task instanceof ExploreAllTask exploreAllTask)) {
            return false;
        }
        currentExploreTask = exploreAllTask;
        setExplorationRadius(exploreAllTask.getExplorationRadius());
        return true;
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        // Enforce exploration center constraint
        BlockPos center = getExplorationCenter();
        if (center != null && pos.distManhattan(center) > getExplorationRadius()) {
            return false;
        }

        // Check if this is a valid storage using MSM
        Target msmTarget = MaidStorage.getInstance().isValidTarget(level, maid, pos, null);
        if (msmTarget == null) {
            return false;
        }

        // Skip furnaces and processing blocks
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
            return false;
        }

        // Check if already visited in memory
        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
        WorkBlockTarget ourTarget = new WorkBlockTarget(msmTarget);

        return !memory.hasVisited(ourTarget);
    }

    @Override
    protected void onArrival(ServerLevel level, EntityMaid maid, BlockPos pos) {
        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);

        // Get storage target from MSM
        Target msmTarget = MaidStorage.getInstance().isValidTarget(level, maid, pos, null);
        if (msmTarget == null) {
            MaidAgent.LOGGER.warn("ExploreStoragesTask: Storage at {} no longer valid", pos.toShortString());
            return;
        }

        WorkBlockTarget target = new WorkBlockTarget(msmTarget);

        // Read contents
        BlockEntity be = level.getBlockEntity(pos);
        String name = level.getBlockState(pos).getBlock().getName().getString();
        if (be == null) {
            MaidAgent.LOGGER.warn("ExploreStoragesTask: No block entity at {}", pos.toShortString());
            memory.markVisited(target);
            return;
        }

        be.getCapability(ForgeCapabilities.ITEM_HANDLER, msmTarget.getSide().orElse(null)).ifPresent(storage -> {
            List<ItemStack> contents = InventoryUtils.aggregate(storage);
            memory.addStorage(target, contents, name);
            memory.markVisited(target);

            MaidAgent.LOGGER.debug("ExploreStoragesTask: Explored {} at {} - found {} item types",
                    target.getType().getPath(), pos.toShortString(), contents.size());
        });
    }

    @Override
    protected void onExplorationComplete(ServerLevel level, EntityMaid maid) {
        MaidAgent.LOGGER.debug("ExploreStoragesTask: Exploration complete");

        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
        String summary = memory.getStorageContentsSummary();

        TaskQueue queue = TaskQueueHelper.getOrCreateQueue(maid);
        if (currentExploreTask != null) {
            queue.completeTask(currentExploreTask, true, summary, memory.getStorageCount());
        }

        currentExploreTask = null;
    }
}

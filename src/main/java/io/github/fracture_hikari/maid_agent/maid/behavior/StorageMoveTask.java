package io.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import io.github.fracture_hikari.maid_agent.storage.StorageManager;
import io.github.fracture_hikari.maid_agent.storage.StorageTarget;
import io.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

/**
 * Behavior that makes the maid walk toward a storage block.
 * Extends MaidMoveToBlockTask from TouhouLittleMaid for proper pathfinding.
 * 
 * Now that StorageItemsFunction sets TARGET_POS directly, this behavior
 * is only used as fallback when target is not pre-set.
 * 
 * Activated when there's a PENDING_TASK with PENDING status AND no TARGET_POS set.
 */
public class StorageMoveTask extends MaidMoveToBlockTask {
    private static final float WALK_SPEED = 0.6f;
    private static final int VERTICAL_RANGE = 3;
    
    private StorageTarget foundTarget = null;

    public StorageMoveTask() {
        super(WALK_SPEED, VERTICAL_RANGE);
        this.setMaxCheckRate(20); // Check every ~1 second
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        // If TARGET_POS is already set (by StorageItemsFunction), don't need to search
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            return false;
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        }
        
        // Check if we have a pending storage task that needs movement search
        Optional<PendingTask> taskOpt = maid.getBrain().getMemory(MemoryModuleRegistry.PENDING_TASK.get());
        if (taskOpt.isEmpty()) {
            return false;
        }
        
        PendingTask task = taskOpt.get();
        
        // Only handle FETCH or STORE tasks in PENDING status (not yet moving)
        // If status is MOVING, the target was already set by StorageItemsFunction
        if (task.getType() != PendingTask.TaskType.FETCH && 
            task.getType() != PendingTask.TaskType.STORE) {
            return false;
        }
        
        if (task.getStatus() != PendingTask.TaskStatus.PENDING) {
            return false;
        }
        
        // If task already has a target, StorageItemsFunction should have set TARGET_POS
        // This behavior is only for fallback when target wasn't specified
        if (task.getTarget() != null) {
            MaidAgent.LOGGER.debug("StorageMoveTask: Task has target but no TARGET_POS - setting memories");
            // Target exists but memories weren't set - fix it
            BlockPos pos = task.getTarget().getPos();
            maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), 
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(maid, pos, WALK_SPEED, 1);
            task.setStatus(PendingTask.TaskStatus.MOVING);
            return false; // Don't search, we just set the target
        }
        
        // Reset found target for search
        foundTarget = null;
        
        // Call parent check (handles rate limiting) - this will trigger searchForDestination
        return super.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        // Parent calls searchForDestination() which finds and sets target
        super.start(level, maid, gameTime);
        
        // If we found a target through search, update task status
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()) && foundTarget != null) {
            maid.getBrain().getMemory(MemoryModuleRegistry.PENDING_TASK.get())
                    .ifPresent(task -> {
                        task.setStatus(PendingTask.TaskStatus.MOVING);
                        task.setTarget(foundTarget);
                        MaidAgent.LOGGER.info("StorageMoveTask: Found storage via search at {}", foundTarget.getPos());
                    });
        }
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        // This is called by parent's searchForDestination()
        // Check if this position is adjacent to a valid storage
        for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            Optional<StorageTarget> targetOpt = StorageManager.getInstance()
                    .isValidTarget(level, checkPos, null);
            if (targetOpt.isPresent()) {
                foundTarget = targetOpt.get();
                return true;
            }
        }
        return false;
    }
}

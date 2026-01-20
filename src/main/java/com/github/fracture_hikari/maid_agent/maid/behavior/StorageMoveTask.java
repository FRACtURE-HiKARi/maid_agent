package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.StorageTarget;
import com.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.memory.TaskQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Optional;

// MSM imports for storage detection
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;

/**
 * Behavior that makes the maid walk toward a storage block.
 * Uses MSM's MaidStorage for storage detection.
 * 
 * Activated when there's a TASK_QUEUE with tasks in PENDING status AND no TARGET_POS set.
 */
public class StorageMoveTask extends MaidMoveToBlockTask {
    private static final float WALK_SPEED = 0.6f;
    private static final int VERTICAL_RANGE = 3;
    
    private StorageTarget foundTarget = null;

    public StorageMoveTask() {
        super(WALK_SPEED, VERTICAL_RANGE);
        this.setMaxCheckRate(20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        // This behavior requires MSM
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            return false;
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        }
        
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isEmpty() || queueOpt.get().isEmpty()) {
            return false;
        }
        
        PendingTask task = queueOpt.get().peek();
        if (task == null) {
            return false;
        }
        
        if (task.getType() != PendingTask.TaskType.FETCH && 
            task.getType() != PendingTask.TaskType.STORE) {
            return false;
        }
        
        if (task.getStatus() != PendingTask.TaskStatus.PENDING) {
            return false;
        }
        
        if (task.getTarget() != null) {
            MaidAgent.LOGGER.debug("StorageMoveTask: Task has target but no TARGET_POS - setting memories");
            BlockPos pos = task.getTarget().getPos();
            maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), 
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(maid, pos, WALK_SPEED, 1);
            task.setStatus(PendingTask.TaskStatus.MOVING);
            return false;
        }
        
        foundTarget = null;
        
        return super.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()) && foundTarget != null) {
            maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                    .map(TaskQueue::peek)
                    .ifPresent(task -> {
                        task.setStatus(PendingTask.TaskStatus.MOVING);
                        task.setTarget(foundTarget);
                        MaidAgent.LOGGER.info("StorageMoveTask: Found storage via search at {}", foundTarget.getPos());
                    });
        }
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        // Check adjacent blocks for valid storage using MSM
        for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            Target msmTarget = MaidStorage.getInstance().isValidTarget(level, maid, checkPos, null);
            if (msmTarget != null) {
                // Convert MSM Target to our StorageTarget
                foundTarget = new StorageTarget(
                        msmTarget.getType(), 
                        msmTarget.getPos(), 
                        msmTarget.getSide());
                return true;
            }
        }
        return false;
    }
}

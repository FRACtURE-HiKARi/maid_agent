package io.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import io.github.fracture_hikari.maid_agent.ai.AIChatCallback;
import io.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import io.github.fracture_hikari.maid_agent.storage.IStorageHandler;
import io.github.fracture_hikari.maid_agent.storage.StorageManager;
import io.github.fracture_hikari.maid_agent.storage.StorageTarget;
import io.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

/**
 * Behavior that executes storage interaction when maid arrives at target.
 * 
 * Pattern from maid_storage_manager (PlaceBehavior):
 * - checkExtraStartConditions: check hasReachedValidTargetOrReset()
 * - canStillUse: return false when work is done
 * - start: initialize work state
 * - tick: do incremental work
 * - stop: cleanup and clear memories
 * 
 * Performs fetch/store operation and triggers AI chat callback with result.
 */
public class StorageWorkTask extends Behavior<EntityMaid> {
    private static final double CLOSE_ENOUGH = 2.5;
    
    private boolean workDone = false;
    private String resultMessage = null;

    public StorageWorkTask() {
        super(Map.of(
                InitEntities.TARGET_POS.get(), net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED
        ), 100, 200); // Run for max 200 ticks
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        // Check if we have a task in MOVING status with a target
        Optional<PendingTask> taskOpt = maid.getBrain().getMemory(MemoryModuleRegistry.PENDING_TASK.get());
        if (taskOpt.isEmpty()) {
            return false;
        }
        
        PendingTask task = taskOpt.get();
        if (task.getStatus() != PendingTask.TaskStatus.MOVING) {
            return false;
        }
        if (task.getTarget() == null) {
            return false;
        }
        
        // Check if arrived at target (similar to hasReachedValidTargetOrReset)
        return hasReachedTarget(maid);
    }
    
    private boolean hasReachedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            double distSq = maid.distanceToSqr(targetV3d);
            boolean arrived = distSq < Math.pow(CLOSE_ENOUGH, 2);
            
            // Also check if not arrived but walk target is gone (path failed)
            if (!arrived) {
                Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty()) {
                    // Path failed, reset target
                    maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
                    return false;
                }
            }
            return arrived;
        }).orElse(false);
    }
    
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        // Stop once work is done
        return !workDone;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        workDone = false;
        resultMessage = null;
        
        // Set task to WORKING status
        maid.getBrain().getMemory(MemoryModuleRegistry.PENDING_TASK.get())
                .ifPresent(task -> task.setStatus(PendingTask.TaskStatus.WORKING));
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (workDone) return;
        
        // Perform the storage operation
        maid.getBrain().getMemory(MemoryModuleRegistry.PENDING_TASK.get())
                .ifPresent(task -> {
                    if (task.getType() == PendingTask.TaskType.FETCH) {
                        resultMessage = performFetch(level, maid, task);
                    } else if (task.getType() == PendingTask.TaskType.STORE) {
                        resultMessage = performStore(level, maid, task);
                    } else {
                        resultMessage = "Unknown task type";
                        task.fail(resultMessage);
                    }
                    workDone = true;
                });
    }
    
    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // Clear movement memories
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        
        // Clear task from memory
        maid.getBrain().eraseMemory(MemoryModuleRegistry.PENDING_TASK.get());
        
        // Trigger AI chat callback with result
        if (resultMessage != null) {
            AIChatCallback.notifyTaskComplete(maid, resultMessage);
        }
    }

    private String performFetch(ServerLevel level, EntityMaid maid, PendingTask task) {
        StorageTarget target = task.getTarget();
        if (target == null) {
            return "Failed to fetch: no target storage found";
        }

        ItemStack targetItem = resolveItem(task.getItemId());
        if (targetItem.isEmpty()) {
            return "Failed to fetch: unknown item " + task.getItemId();
        }

        IStorageHandler handler = StorageManager.getInstance()
                .getHandler(target.getType())
                .orElse(null);
        
        if (handler == null) {
            return "Failed to fetch: storage type not supported";
        }

        // Extract items from storage
        ItemStack extracted = handler.extractItem(level, target.getPos(), 
                target.getSideOrNull(), targetItem, task.getCount());

        if (extracted.isEmpty()) {
            task.fail("Item not found");
            return "Could not find " + getItemName(targetItem) + " in the storage";
        }

        // Put items in maid's inventory
        IItemHandler maidInv = maid.getAvailableInv(false);
        ItemStack remaining = extracted.copy();
        for (int i = 0; i < maidInv.getSlots() && !remaining.isEmpty(); i++) {
            remaining = maidInv.insertItem(i, remaining, false);
        }

        int fetchedCount = extracted.getCount() - remaining.getCount();
        
        if (fetchedCount > 0) {
            task.complete("Fetched items", fetchedCount);
            return String.format("Successfully fetched %d %s from storage", 
                    fetchedCount, getItemName(targetItem));
        } else {
            task.fail("Inventory full");
            return "My inventory is full, couldn't store the fetched items";
        }
    }

    private String performStore(ServerLevel level, EntityMaid maid, PendingTask task) {
        StorageTarget target = task.getTarget();
        if (target == null) {
            return "Failed to store: no target storage found";
        }

        ItemStack targetItem = resolveItem(task.getItemId());
        if (targetItem.isEmpty()) {
            return "Failed to store: unknown item " + task.getItemId();
        }

        IStorageHandler handler = StorageManager.getInstance()
                .getHandler(target.getType())
                .orElse(null);
        
        if (handler == null) {
            return "Failed to store: storage type not supported";
        }

        // Find and transfer items from maid's inventory
        IItemHandler maidInv = maid.getAvailableInv(false);
        int toStore = task.getCount();
        int stored = 0;

        for (int i = 0; i < maidInv.getSlots() && (toStore == Integer.MAX_VALUE || stored < toStore); i++) {
            ItemStack slotStack = maidInv.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(slotStack, targetItem)) {
                int extractCount = toStore == Integer.MAX_VALUE ? slotStack.getCount() : 
                        Math.min(slotStack.getCount(), toStore - stored);
                ItemStack toInsert = maidInv.extractItem(i, extractCount, false);
                
                if (!toInsert.isEmpty()) {
                    ItemStack leftover = handler.insertItem(level, target.getPos(), 
                            target.getSideOrNull(), toInsert);
                    
                    int insertedCount = toInsert.getCount() - leftover.getCount();
                    stored += insertedCount;
                    
                    // Put back what couldn't be stored
                    if (!leftover.isEmpty()) {
                        maidInv.insertItem(i, leftover, false);
                    }
                }
            }
        }

        if (stored > 0) {
            task.complete("Stored items", stored);
            return String.format("Successfully stored %d %s in storage", 
                    stored, getItemName(targetItem));
        } else {
            task.fail("Nothing stored");
            return "Could not store any " + getItemName(targetItem) + 
                    " (item not in inventory or storage full)";
        }
    }

    private ItemStack resolveItem(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Exception e) {
            // Invalid item ID
        }
        return ItemStack.EMPTY;
    }

    private String getItemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }
}

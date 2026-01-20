package com.github.fracture_hikari.maid_agent.maid.behavior;

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
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Behavior that executes storage interaction when maid arrives at target.
 * Uses item handler capabilities for storage interaction.
 * 
 * Triggers LLM callback when batch completes.
 */
public class StorageWorkTask extends Behavior<EntityMaid> {
    private static final double CLOSE_ENOUGH = 2.5;
    private static final float WALK_SPEED = 0.6f;
    
    private boolean workDone = false;
    private String resultMessage = null;

    public StorageWorkTask() {
        super(Map.of(
                InitEntities.TARGET_POS.get(), net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED
        ), 100, 200); // Run for max 200 ticks
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        // This behavior requires MSM
        if (!Integrations.maidStorageManager()) {
            return false;
        }
        
        // Check if we have a task queue with a current task ready to work
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isEmpty() || queueOpt.get().isEmpty()) {
            return false;
        }
        
        PendingTask task = queueOpt.get().peek();
        if (task == null || task.getStatus() != PendingTask.TaskStatus.MOVING) {
            return false;
        }
        if (task.getTarget() == null) {
            return false;
        }
        
        // Check if arrived at target
        return hasReachedTarget(maid);
    }
    
    private boolean hasReachedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            double distSq = maid.distanceToSqr(targetV3d);
            boolean arrived = distSq < Math.pow(CLOSE_ENOUGH, 2);
            
            if (!arrived) {
                Optional<WalkTarget> walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty()) {
                    // Walk target was cleared - re-set it to continue moving
                    BlockPos targetBlockPos = BlockPos.containing(targetV3d);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, targetBlockPos, WALK_SPEED, 1);
                    MaidAgent.LOGGER.debug("StorageWorkTask: Re-setting walk target to {}", targetBlockPos);
                }
            }
            return arrived;
        }).orElse(false);
    }
    
    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !workDone;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        workDone = false;
        resultMessage = null;
        
        maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .map(TaskQueue::peek)
                .ifPresent(task -> task.setStatus(PendingTask.TaskStatus.WORKING));
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (workDone) return;
        
        maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .map(TaskQueue::peek)
                .ifPresent(task -> {
                    if (task.getType() == PendingTask.TaskType.FETCH) {
                        resultMessage = performFetch(level, maid, task);
                    } else if (task.getType() == PendingTask.TaskType.STORE) {
                        resultMessage = performStore(level, maid, task);
                    } else if (task.getType() == PendingTask.TaskType.CRAFT) {
                        resultMessage = performCraft(level, maid, task);
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
        
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isEmpty()) return;
        
        TaskQueue queue = queueOpt.get();
        PendingTask completedTask = queue.peek();
        
        if (completedTask != null) {
            boolean success = completedTask.getStatus() == PendingTask.TaskStatus.COMPLETED;
            queue.completeCurrentTask(success, completedTask.getResultMessage(), completedTask.getActualCount());
            MaidAgent.LOGGER.info("Task completed: {} - {}", completedTask.getType(), resultMessage);
        }
        
        if (queue.isBatchComplete()) {
            queue.notifyBatchComplete();
            maid.getBrain().eraseMemory(MemoryModuleRegistry.TASK_QUEUE.get());
            MaidAgent.LOGGER.info("Batch complete, notified LLM");
        } else if (!queue.isEmpty()) {
            PendingTask nextTask = queue.peek();
            if (nextTask != null && nextTask.getTarget() != null) {
                nextTask.setStatus(PendingTask.TaskStatus.MOVING);
                BlockPos targetPos = nextTask.getTarget().getPos();
                maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(targetPos));
                BehaviorUtils.setWalkAndLookTargetMemories(maid, targetPos, WALK_SPEED, 1);
                MaidAgent.LOGGER.info("Starting next task: {} at {}", nextTask.getType(), targetPos);
            }
        }
    }

    private String performFetch(ServerLevel level, EntityMaid maid, PendingTask task) {
        StorageTarget target = task.getTarget();
        if (target == null) {
            task.fail("No target");
            return "Failed to fetch: no target storage found";
        }

        ItemStack targetItem = resolveItem(task.getItemId());
        if (targetItem.isEmpty()) {
            task.fail("Unknown item");
            return "Failed to fetch: unknown item " + task.getItemId();
        }

        // Get IItemHandler from block entity
        BlockEntity be = level.getBlockEntity(target.getPos());
        if (be == null) {
            task.fail("No storage");
            return "Failed to fetch: storage block not found";
        }
        
        IItemHandler storageHandler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, 
                target.getSideOrNull()).orElse(null);
        if (storageHandler == null) {
            task.fail("No handler");
            return "Failed to fetch: storage type not supported";
        }

        // Extract items from storage
        IItemHandler maidInv = maid.getAvailableInv(false);
        int fetchedCount = 0;
        int toFetch = task.getCount();

        for (int i = 0; i < storageHandler.getSlots() && fetchedCount < toFetch; i++) {
            ItemStack slotStack = storageHandler.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(slotStack, targetItem)) {
                int extractCount = Math.min(slotStack.getCount(), toFetch - fetchedCount);
                ItemStack extracted = storageHandler.extractItem(i, extractCount, false);
                
                if (!extracted.isEmpty()) {
                    // Try to insert into maid inventory
                    ItemStack remaining = extracted.copy();
                    for (int j = 0; j < maidInv.getSlots() && !remaining.isEmpty(); j++) {
                        remaining = maidInv.insertItem(j, remaining, false);
                    }
                    fetchedCount += extracted.getCount() - remaining.getCount();
                    
                    // Put back what couldn't fit
                    if (!remaining.isEmpty()) {
                        storageHandler.insertItem(i, remaining, false);
                    }
                }
            }
        }

        if (fetchedCount > 0) {
            task.complete("Fetched items", fetchedCount);
            return String.format("Successfully fetched %d %s from storage", 
                    fetchedCount, getItemName(targetItem));
        } else {
            task.fail("Item not found");
            return "Could not find " + getItemName(targetItem) + " in the storage";
        }
    }

    private String performStore(ServerLevel level, EntityMaid maid, PendingTask task) {
        StorageTarget target = task.getTarget();
        if (target == null) {
            task.fail("No target");
            return "Failed to store: no target storage found";
        }

        ItemStack targetItem = resolveItem(task.getItemId());
        if (targetItem.isEmpty()) {
            task.fail("Unknown item");
            return "Failed to store: unknown item " + task.getItemId();
        }

        BlockEntity be = level.getBlockEntity(target.getPos());
        if (be == null) {
            task.fail("No storage");
            return "Failed to store: storage block not found";
        }
        
        IItemHandler storageHandler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, 
                target.getSideOrNull()).orElse(null);
        if (storageHandler == null) {
            task.fail("No handler");
            return "Failed to store: storage type not supported";
        }

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
                    // Try to insert into storage
                    ItemStack remaining = toInsert.copy();
                    for (int j = 0; j < storageHandler.getSlots() && !remaining.isEmpty(); j++) {
                        remaining = storageHandler.insertItem(j, remaining, false);
                    }
                    
                    int insertedCount = toInsert.getCount() - remaining.getCount();
                    stored += insertedCount;
                    
                    // Put back what couldn't be stored
                    if (!remaining.isEmpty()) {
                        maidInv.insertItem(i, remaining, false);
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
    
    /**
     * Perform crafting at a workstation using maid's inventory.
     */
    private String performCraft(ServerLevel level, EntityMaid maid, PendingTask task) {
        ItemStack targetItem = resolveItem(task.getItemId());
        if (targetItem.isEmpty()) {
            task.fail("Unknown item");
            return "Failed to craft: unknown item " + task.getItemId();
        }
        
        // Get pre-computed crafting steps (sub-recipes first, main recipe last)
        java.util.LinkedHashMap<String, Integer> craftingSteps = task.getCraftingSteps();
        if (craftingSteps == null || craftingSteps.isEmpty()) {
            // Fallback: just use the main recipe
            craftingSteps = new java.util.LinkedHashMap<>();
            craftingSteps.put(task.getRecipeId(), task.getCount());
        }
        
        IItemHandler maidInv = maid.getAvailableInv(false);
        int finalCraftCount = 0;
        
        // Execute each step in order (sub-recipes first)
        for (java.util.Map.Entry<String, Integer> step : craftingSteps.entrySet()) {
            String stepRecipeId = step.getKey();
            int neededCrafts = step.getValue();
            
            ResourceLocation recipeRL = ResourceLocation.tryParse(stepRecipeId);
            if (recipeRL == null) continue;
            
            Optional<net.minecraft.world.item.crafting.CraftingRecipe> stepRecipe = 
                    level.getRecipeManager()
                    .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                    .stream()
                    .filter(r -> r.getId().equals(recipeRL))
                    .findFirst();
            
            if (stepRecipe.isEmpty()) {
                MaidAgent.LOGGER.warn("Recipe not found: {}", stepRecipeId);
                continue;
            }
            
            net.minecraft.world.item.crafting.CraftingRecipe recipe = stepRecipe.get();
            boolean isMainRecipe = stepRecipeId.equals(task.getRecipeId());
            
            // Craft exactly the needed amount
            int craftCountForStep = 0;
            
            while (craftCountForStep < neededCrafts && hasIngredients(maidInv, recipe)) {
                consumeIngredients(maidInv, recipe);
                ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                for (int i = 0; i < maidInv.getSlots(); i++) {
                    result = maidInv.insertItem(i, result, false);
                    if (result.isEmpty()) break;
                }
                craftCountForStep++;
                
                if (isMainRecipe) {
                    finalCraftCount++;
                }
            }
            
            if (craftCountForStep > 0) {
                MaidAgent.LOGGER.info("Crafted {} of {} (step: {}, needed: {})", 
                        craftCountForStep, recipe.getResultItem(level.registryAccess()).getItem(), 
                        stepRecipeId, neededCrafts);
            }
        }
        
        if (finalCraftCount > 0) {
            task.complete("Crafted items", finalCraftCount);
            return String.format("Successfully crafted %d %s", finalCraftCount, getItemName(targetItem));
        } else {
            task.fail("Missing ingredients");
            return "Could not craft " + getItemName(targetItem) + " (missing ingredients)";
        }
    }
    
    private boolean hasIngredients(IItemHandler inv, net.minecraft.world.item.crafting.CraftingRecipe recipe) {
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        Map<Integer, Integer> usedSlots = new java.util.HashMap<>();
        
        for (net.minecraft.world.item.crafting.Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            boolean found = false;
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack inSlot = inv.getStackInSlot(i);
                int alreadyUsed = usedSlots.getOrDefault(i, 0);
                if (ingredient.test(inSlot) && inSlot.getCount() > alreadyUsed) {
                    usedSlots.merge(i, 1, Integer::sum);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
    
    private void consumeIngredients(IItemHandler inv, net.minecraft.world.item.crafting.CraftingRecipe recipe) {
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        
        for (net.minecraft.world.item.crafting.Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack inSlot = inv.getStackInSlot(i);
                if (ingredient.test(inSlot) && !inSlot.isEmpty()) {
                    inv.extractItem(i, 1, false);
                    break;
                }
            }
        }
    }
}

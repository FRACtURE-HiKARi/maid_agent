package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.fracture_hikari.maid_agent.util.RecipeLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;
import studio.fantasyit.maid_storage_manager.storage.ItemHandler.SimulateTargetInteractHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Behavior for instant CRAFT tasks (crafting table).
 * Executes pre-calculated crafting steps from maid's inventory.
 */
public class CraftingWorkTask extends AbstractWorkTask {

    public CraftingWorkTask() {
        super();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        return checkTaskMemory(
                level,
                maid,
                task -> task.getType() == PendingTask.TaskType.CRAFT
                        && task.getWorkstationType() == PendingTask.WorkstationType.CRAFTING_TABLE
        );
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return;
        PendingTask task = taskOpt.get();

        ItemStack targetItem = task.getRequestedItem().copy();
        if (targetItem.isEmpty()) {
            MemoryUtil.updateTasks(maid, task, false, "Failed to craft: unknown item " + ItemIdUtils.getId(targetItem));
            return;
        }
        
        String recipeId = task.getRecipeId();
        if (recipeId == null || recipeId.isEmpty()) {
            MemoryUtil.updateTasks(maid, task, false, "Failed to craft: no recipe ID specified");
            return;
        }

        // Lookup specific recipe
        Optional<CraftingRecipe> recipeOpt = RecipeLookup.findById(level, recipeId);
        if (recipeOpt.isEmpty()) {
            MemoryUtil.updateTasks(maid, task, false, "Recipe not found: " + recipeId);
            return;
        }
        
        CraftingRecipe recipe = recipeOpt.get();
        IItemHandler maidInv = maid.getAvailableInv(false);
        int finalCraftCount = 0;
        int neededCrafts = task.getCount();
        
        // Craft exactly the needed amount
        // Note: For task graph, inputs should already be available from dependencies.
        
        while (finalCraftCount < neededCrafts && hasIngredients(maidInv, recipe)) {
            consumeIngredients(maidInv, recipe);
            ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
            
            // Handle output count (e.g. recipe produces 4 planks)
            // But task.getCount() usually refers to number of OUTPUT items needed?
            // "count (integer, optional): Number of items to craft."
            // In CraftItemFunction logic: "opsNeeded = ceil(count / outputCount)"
            // PendingTask count is 'count' requested.
            // If recipe output is > 1, we might overproduce.
            // The loop condition should probably track output items produced?
            // But logic says `targetItem.setCount(result.getCount() * count)` in CraftItemFunction (createSingleTask).
            // Actually, in generateTaskGraph: `ItemStack targetItem = ItemIdUtils.createStack(tree.getOutput(), tree.getCount());`
            // `tree.getCount()` is the TOTAL needed.
            // So PendingTask count is TOTAL items.
            // Recipe output count matters.
            
            // Optimization: check how many items this craft produces
            int outputPerCraft = result.getCount();
            
            // Add to inventory
            for (int i = 0; i < maidInv.getSlots(); i++) {
                result = maidInv.insertItem(i, result, false);
                if (result.isEmpty()) break;
            }
            
            finalCraftCount += outputPerCraft;
        }
        
        if (finalCraftCount > 0) {
            task.setActualAccount(finalCraftCount);
            MemoryUtil.updateTasks(maid, task, true, "craft successful.");
        } else {
            String msg = "Could not craft " + getItemName(targetItem) + " (missing ingredients)";
            MemoryUtil.updateTasks(maid, task, false, msg);
        }
    }
    
    private boolean hasIngredients(IItemHandler inv, CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        Map<Integer, Integer> usedSlots = new HashMap<>();
        
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            boolean found = false;
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack inSlot = inv.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                
                int used = usedSlots.getOrDefault(slot, 0);
                int available = inSlot.getCount() - used;
                
                if (available > 0 && ingredient.test(inSlot)) {
                    usedSlots.put(slot, used + 1);
                    found = true;
                    break;
                }
            }
            
            if (!found) return false;
        }
        return true;
    }
    
    private void consumeIngredients(IItemHandler inv, CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack inSlot = inv.getStackInSlot(slot);
                if (!inSlot.isEmpty() && ingredient.test(inSlot)) {
                    inv.extractItem(slot, 1, false);
                    break;
                }
            }
        }
    }
}

package com.github.fracture_hikari.maid_agent.maid.behavior;

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
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        PendingTask task = taskOpt.get();
        return task.getType() == PendingTask.TaskType.CRAFT
            && task.getWorkstationType() == PendingTask.WorkstationType.CRAFTING_TABLE;
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return;
        PendingTask task = taskOpt.get();

        ItemStack targetItem = ItemIdUtils.createStack(task.getItemId());
        if (targetItem.isEmpty()) {
            MemoryUtil.updateTasks(maid, false, "Failed to craft: unknown item " + task.getItemId());
            return;
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
        for (Map.Entry<String, Integer> step : craftingSteps.entrySet()) {
            String stepRecipeId = step.getKey();
            int neededCrafts = step.getValue();
            
            // Use pre-calculated recipe ID - no new recipe discovery
            Optional<CraftingRecipe> stepRecipe = RecipeLookup.findById(level, stepRecipeId);
            
            if (stepRecipe.isEmpty()) {
                MemoryUtil.updateTasks(maid, false, "Recipe not found: " + stepRecipeId);
                return;
            }
            
            CraftingRecipe recipe = stepRecipe.get();
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
            task.setActualAccount(finalCraftCount);
            MemoryUtil.updateTasks(maid, true, "craft successful.");
        } else {
            String msg = "Could not craft " + getItemName(targetItem) + " (missing ingredients)";
            MemoryUtil.updateTasks(maid, false, msg);
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

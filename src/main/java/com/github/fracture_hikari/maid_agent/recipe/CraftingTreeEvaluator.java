package com.github.fracture_hikari.maid_agent.recipe;

import com.github.fracture_hikari.maid_agent.config.CraftConfig;
import com.github.fracture_hikari.maid_agent.util.RecipeLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.*;

/**
 * Evaluates crafting trees to find the best path based on available inventory.
 * Prioritizes trees where all ingredients are available.
 */
public class CraftingTreeEvaluator {
    
    private final ServerLevel level;
    private final Map<String, Integer> availableItems; // Combined maid + storage inventory
    private final int maxDepth;
    
    public CraftingTreeEvaluator(ServerLevel level, Map<String, Integer> maidInv, Map<String, Integer> storageInv) {
        this.level = level;
        this.maxDepth = CraftConfig.MAX_CRAFT_TREE_DEPTH.get();
        
        // Combine inventories
        this.availableItems = new HashMap<>(maidInv);
        storageInv.forEach((k, v) -> availableItems.merge(k, v, Integer::sum));
    }
    
    /**
     * Evaluate all recipes for an item and return ranked results.
     * Recipes with complete ingredient trees are ranked first.
     */
    public List<EvaluatedTree> evaluateRecipes(ItemStack target, int count) {
        List<EvaluatedTree> results = new ArrayList<>();
        
        // Get all crafting recipes using RecipeLookup
        List<CraftingRecipe> craftingRecipes = RecipeLookup.findByOutput(level, target, 20);
        
        for (CraftingRecipe recipe : craftingRecipes) {
            EvaluatedTree tree = evaluateTree(recipe, count, 0, new HashMap<>(availableItems));
            results.add(tree);
        }
        
        // Get ALL smelting recipes (e.g., iron_ore AND raw_iron → iron_ingot)
        List<SmeltingRecipe> smeltingRecipes = RecipeLookup.findAllSmeltingByOutput(level, target, 10);
        for (SmeltingRecipe recipe : smeltingRecipes) {
            EvaluatedTree tree = evaluateSmeltingTree(recipe, count, 0, new HashMap<>(availableItems));
            results.add(tree);
        }
        
        // Sort: valid trees first, then by missing count
        results.sort((a, b) -> {
            if (a.isComplete() && !b.isComplete()) return -1;
            if (!a.isComplete() && b.isComplete()) return 1;
            return Integer.compare(a.getMissingCount(), b.getMissingCount());
        });
        
        return results;
    }
    
    private EvaluatedTree evaluateTree(CraftingRecipe recipe, int count, int depth, Map<String, Integer> remaining) {
        String recipeId = recipe.getId().toString();
        ItemStack output = recipe.getResultItem(level.registryAccess());
        int outputCount = output.getCount();
        int craftsNeeded = (int) Math.ceil((double) count / outputCount);
        
        // Collect ingredients, then merge duplicates
        Map<String, IngredientResult> mergedIngredients = new LinkedHashMap<>();
        int totalMissing = 0;
        
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) continue;
            
            // Find best matching item for this ingredient slot
            IngredientResult result = evaluateIngredient(ingredient, 1, depth, remaining);
            
            // Merge with existing if same item
            String itemId = result.getItemId();
            if (mergedIngredients.containsKey(itemId)) {
                IngredientResult existing = mergedIngredients.get(itemId);
                mergedIngredients.put(itemId, existing.merge(result));
            } else {
                mergedIngredients.put(itemId, result);
            }
        }
        
        // Scale by crafts needed and calculate total missing
        List<IngredientResult> ingredients = new ArrayList<>();
        for (IngredientResult ir : mergedIngredients.values()) {
            IngredientResult scaled = ir.scale(craftsNeeded);
            ingredients.add(scaled);
            totalMissing += scaled.getMissing();
        }
        
        return new EvaluatedTree(recipeId, "minecraft:crafting_table", 
                output.getItem().builtInRegistryHolder().key().location().toString(),
                count, ingredients, totalMissing);
    }
    
    private EvaluatedTree evaluateSmeltingTree(SmeltingRecipe recipe, int count, int depth, Map<String, Integer> remaining) {
        String recipeId = recipe.getId().toString();
        ItemStack output = recipe.getResultItem(level.registryAccess());
        
        // Collect and merge ingredients
        Map<String, IngredientResult> mergedIngredients = new LinkedHashMap<>();
        
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            
            IngredientResult result = evaluateIngredient(ingredient, 1, depth, remaining);
            String itemId = result.getItemId();
            if (mergedIngredients.containsKey(itemId)) {
                mergedIngredients.put(itemId, mergedIngredients.get(itemId).merge(result));
            } else {
                mergedIngredients.put(itemId, result);
            }
        }
        
        // Scale by count, assign slot 0 (input), and calculate total missing
        List<IngredientResult> ingredients = new ArrayList<>();
        int totalMissing = 0;
        for (IngredientResult ir : mergedIngredients.values()) {
            IngredientResult scaled = ir.scale(count);
            // Assign slot 0 (furnace input) to all smelting ingredients
            IngredientResult withSlot = new IngredientResult(
                    scaled.getItemId(), scaled.getNeed(), scaled.getHave(), scaled.getMissing(), 
                    scaled.getSubTree(), 0);  // slot 0 = input
            ingredients.add(withSlot);
            totalMissing += withSlot.getMissing();
        }
        
        // Calculate fuel needed and add as ingredient
        IngredientResult fuelResult = calculateFuelNeeded(count, remaining);
        if (fuelResult != null) {
            ingredients.add(fuelResult);
            totalMissing += fuelResult.getMissing();
        }
        
        return new EvaluatedTree(recipeId, "minecraft:furnace",
                output.getItem().builtInRegistryHolder().key().location().toString(),
                count, ingredients, totalMissing);
    }
    
    /**
     * Calculate fuel needed for smelting. Finds best fuel in inventory (longest burn time).
     * Returns an IngredientResult representing fuel requirement.
     */
    private IngredientResult calculateFuelNeeded(int itemsToSmelt, Map<String, Integer> remaining) {
        // 200 ticks per smelt operation
        int ticksNeeded = itemsToSmelt * 200;
        
        // Find best fuel available (longest burn time = min items needed)
        String bestFuelId = null;
        int bestBurnTime = 0;
        int bestHave = 0;
        
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            String itemId = entry.getKey();
            int have = entry.getValue();
            if (have <= 0) continue;
            
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null);
            
            if (burnTime > bestBurnTime) {
                bestBurnTime = burnTime;
                bestFuelId = itemId;
                bestHave = have;
            }
        }
        
        if (bestFuelId == null || bestBurnTime <= 0) {
            // No fuel found - return a placeholder showing coal is needed
            int coalNeeded = (int) Math.ceil((double) ticksNeeded / 1600); // Coal burns 1600 ticks
        return new IngredientResult("minecraft:coal", coalNeeded, 0, coalNeeded, null, 1);  // slot 1 = fuel
        }
        
        // Calculate how many of this fuel we need
        int fuelNeeded = (int) Math.ceil((double) ticksNeeded / bestBurnTime);
        int missing = Math.max(0, fuelNeeded - bestHave);
        
        // Consume fuel from remaining
        if (fuelNeeded <= bestHave) {
            remaining.merge(bestFuelId, -fuelNeeded, Integer::sum);
        }
        
        return new IngredientResult(bestFuelId, fuelNeeded, bestHave, missing, null, 1);  // slot 1 = fuel
    }
    
    private IngredientResult evaluateIngredient(Ingredient ingredient, int needed, int depth, Map<String, Integer> remaining) {
        ItemStack[] items = ingredient.getItems();
        
        // Try each possible item for this ingredient
        IngredientResult bestResult = null;
        
        for (ItemStack item : items) {
            String itemId = item.getItem().builtInRegistryHolder().key().location().toString();
            int have = remaining.getOrDefault(itemId, 0);
            int missing = Math.max(0, needed - have);
            
            if (missing == 0) {
                // We have enough - consume and return
                remaining.merge(itemId, -needed, Integer::sum);
                return new IngredientResult(itemId, needed, have, 0, null);
            }
            
            // Try to craft the missing amount if we have depth left
            EvaluatedTree subTree = null;
            if (depth < maxDepth && missing > 0) {
                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    ItemStack targetStack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                    
                    // Find ALL craftable recipes and pick the best one
                    List<CraftingRecipe> subRecipes = RecipeLookup.findByOutput(level, targetStack, 10);
                    
                    // Evaluate each and pick the best (most complete)
                    EvaluatedTree bestSubTree = null;
                    for (CraftingRecipe subRecipe : subRecipes) {
                        EvaluatedTree evaluated = evaluateTree(subRecipe, missing, depth + 1, new HashMap<>(remaining));
                        if (bestSubTree == null || 
                            (evaluated.isComplete() && !bestSubTree.isComplete()) ||
                            (!evaluated.isComplete() && !bestSubTree.isComplete() && 
                             evaluated.getMissingCount() < bestSubTree.getMissingCount())) {
                            bestSubTree = evaluated;
                        }
                        // Stop if we found a complete solution
                        if (evaluated.isComplete()) break;
                    }
                    subTree = bestSubTree;
                }
            }
            
            // Calculate effective missing (considering sub-tree)
            int effectiveMissing = (subTree != null && subTree.isComplete()) ? 0 : missing;
            
            IngredientResult result = new IngredientResult(itemId, needed, have, effectiveMissing, subTree);
            
            if (bestResult == null || result.getMissing() < bestResult.getMissing()) {
                bestResult = result;
            }
            
            // If we found a complete solution, use it
            if (effectiveMissing == 0) {
                remaining.merge(itemId, -have, Integer::sum); // Use what we have
                return result;
            }
        }
        
        return bestResult != null ? bestResult : new IngredientResult(
                items[0].getItem().builtInRegistryHolder().key().location().toString(), 
                needed, 0, needed, null);
    }
    
    /**
     * Represents an evaluated crafting tree with completeness info.
     */
    public static class EvaluatedTree {
        private final String recipeId;
        private final String workstation;
        private final String output;
        private final int count;
        private final List<IngredientResult> ingredients;
        private final int missingCount;
        
        public EvaluatedTree(String recipeId, String workstation, String output, 
                             int count, List<IngredientResult> ingredients, int missingCount) {
            this.recipeId = recipeId;
            this.workstation = workstation;
            this.output = output;
            this.count = count;
            this.ingredients = ingredients;
            this.missingCount = missingCount;
        }
        
        public boolean isComplete() { return missingCount == 0; }
        public int getMissingCount() { return missingCount; }
        public String getRecipeId() { return recipeId; }
        public String getWorkstation() { return workstation; }
        public List<IngredientResult> getIngredients() { return ingredients; }
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"recipe_id\": \"%s\", ", recipeId));
            sb.append(String.format("\"workstation\": \"%s\", ", workstation));
            sb.append(String.format("\"complete\": %s, ", isComplete()));
            if (!isComplete()) {
                sb.append(String.format("\"missing_total\": %d, ", missingCount));
            }
            sb.append("\"ingredients\": [");
            for (int i = 0; i < ingredients.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(ingredients.get(i).toJson());
            }
            sb.append("], ");
            sb.append(String.format("\"output\": \"%s\", \"count\": %d}", output, count));
            return sb.toString();
        }
        /**
         * Flatten tree to ordered map of recipe ID -> craft count (sub-recipes first, main recipe last).
         */
        public java.util.LinkedHashMap<String, Integer> toFlattenedSteps() {
            java.util.LinkedHashMap<String, Integer> steps = new java.util.LinkedHashMap<>();
            collectSteps(steps);
            return steps;
        }
        
        private void collectSteps(java.util.LinkedHashMap<String, Integer> steps) {
            // First, collect sub-recipes from ingredients
            for (IngredientResult ingredient : ingredients) {
                if (ingredient.hasSubTree()) {
                    ingredient.getSubTree().collectSteps(steps);
                }
            }
            // Add this recipe with the count (merge if already present)
            steps.merge(recipeId, count, Integer::sum);
        }
    }
    
    /**
     * Result of evaluating an ingredient.
     * Includes slot assignment for PROCESS tasks (0=input, 1=fuel).
     */
    public static class IngredientResult {
        private final String itemId;
        private final int need;
        private final int have;
        private final int missing;
        private final EvaluatedTree subTree;
        private final int targetSlot;  // -1 for crafting, 0=input, 1=fuel for furnace
        
        public IngredientResult(String itemId, int need, int have, int missing, EvaluatedTree subTree) {
            this(itemId, need, have, missing, subTree, -1);  // Default no slot
        }
        
        public IngredientResult(String itemId, int need, int have, int missing, EvaluatedTree subTree, int targetSlot) {
            this.itemId = itemId;
            this.need = need;
            this.have = have;
            this.missing = missing;
            this.subTree = subTree;
            this.targetSlot = targetSlot;
        }
        
        public String getItemId() { return itemId; }
        public int getNeed() { return need; }
        public int getHave() { return have; }
        public int getMissing() { return missing; }
        public boolean hasSubTree() { return subTree != null; }
        public EvaluatedTree getSubTree() { return subTree; }
        public int getTargetSlot() { return targetSlot; }
        
        /**
         * Merge with another result for the same item.
         */
        public IngredientResult merge(IngredientResult other) {
            // Keep subtree from whichever has one (prefer complete)
            EvaluatedTree mergedTree = this.subTree != null ? this.subTree : other.subTree;
            return new IngredientResult(
                    this.itemId,
                    this.need + other.need,
                    this.have, // have doesn't stack
                    this.missing + other.missing,
                    mergedTree
            );
        }
        
        /**
         * Scale this result by a multiplier.
         */
        public IngredientResult scale(int multiplier) {
            return new IngredientResult(
                    this.itemId,
                    this.need * multiplier,
                    this.have,
                    this.missing * multiplier,
                    this.subTree
            );
        }
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"item\": \"%s\", \"need\": %d, \"have\": %d, \"missing\": %d",
                    itemId, need, have, missing));
            if (subTree != null) {
                sb.append(", \"craft_from\": ").append(subTree.toJson());
            }
            sb.append("}");
            return sb.toString();
        }
    }
}

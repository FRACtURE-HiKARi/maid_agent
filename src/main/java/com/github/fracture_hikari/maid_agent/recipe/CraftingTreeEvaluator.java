package com.github.fracture_hikari.maid_agent.recipe;

import com.github.fracture_hikari.maid_agent.config.CraftConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import studio.fantasyit.maid_storage_manager.util.RecipeUtil;

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
        
        // Get all crafting recipes
        List<CraftingRecipe> craftingRecipes = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), target))
                .limit(20) // Reasonable limit
                .toList();
        
        for (CraftingRecipe recipe : craftingRecipes) {
            EvaluatedTree tree = evaluateTree(recipe, count, 0, new HashMap<>(availableItems));
            results.add(tree);
        }
        
        // Check smelting
        Optional<SmeltingRecipe> smeltingRecipe = RecipeUtil.getSmeltingRecipe(level, target);
        smeltingRecipe.ifPresent(recipe -> {
            EvaluatedTree tree = evaluateSmeltingTree(recipe, count, 0, new HashMap<>(availableItems));
            results.add(tree);
        });
        
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
                count, ingredients, totalMissing, false);
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
        
        // Scale by count and calculate total missing
        List<IngredientResult> ingredients = new ArrayList<>();
        int totalMissing = 0;
        for (IngredientResult ir : mergedIngredients.values()) {
            IngredientResult scaled = ir.scale(count);
            ingredients.add(scaled);
            totalMissing += scaled.getMissing();
        }
        
        // TODO: Check fuel availability
        
        return new EvaluatedTree(recipeId, "minecraft:furnace",
                output.getItem().builtInRegistryHolder().key().location().toString(),
                count, ingredients, totalMissing, true);
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
                    List<CraftingRecipe> subRecipes = level.getRecipeManager()
                            .getAllRecipesFor(RecipeType.CRAFTING)
                            .stream()
                            .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), targetStack))
                            .limit(10) // Reasonable limit
                            .toList();
                    
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
        private final boolean requiresFuel;
        
        public EvaluatedTree(String recipeId, String workstation, String output, 
                             int count, List<IngredientResult> ingredients, int missingCount, boolean requiresFuel) {
            this.recipeId = recipeId;
            this.workstation = workstation;
            this.output = output;
            this.count = count;
            this.ingredients = ingredients;
            this.missingCount = missingCount;
            this.requiresFuel = requiresFuel;
        }
        
        public boolean isComplete() { return missingCount == 0; }
        public int getMissingCount() { return missingCount; }
        public String getRecipeId() { return recipeId; }
        public String getWorkstation() { return workstation; }
        public List<IngredientResult> getIngredients() { return ingredients; }
        public boolean requiresFuel() { return requiresFuel; }
        
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
            if (requiresFuel) {
                sb.append("\"requires_fuel\": true, ");
            }
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
     */
    public static class IngredientResult {
        private final String itemId;
        private final int need;
        private final int have;
        private final int missing;
        private final EvaluatedTree subTree;
        
        public IngredientResult(String itemId, int need, int have, int missing, EvaluatedTree subTree) {
            this.itemId = itemId;
            this.need = need;
            this.have = have;
            this.missing = missing;
            this.subTree = subTree;
        }
        
        public String getItemId() { return itemId; }
        public int getMissing() { return missing; }
        public boolean hasSubTree() { return subTree != null; }
        public EvaluatedTree getSubTree() { return subTree; }
        
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

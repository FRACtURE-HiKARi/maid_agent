package com.github.fracture_hikari.maid_agent.recipe;

import com.github.fracture_hikari.maid_agent.config.CraftConfig;
import com.github.fracture_hikari.maid_agent.util.RecipeLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.*;

/**
 * Evaluates crafting trees to find the best path based on available inventory.
 * Prioritizes trees where all ingredients are available.
 * Unifies search for both CraftingTable and Furnace recipes.
 */
public class CraftingTreeEvaluator {
    
    private final ServerLevel level;
    private final Map<String, Integer> availableItems;
    private final int maxDepth;
    
    public CraftingTreeEvaluator(ServerLevel level, Map<String, Integer> availableItems) {
        this.level = level;
        this.maxDepth = CraftConfig.MAX_CRAFT_TREE_DEPTH.get();
        // Copy to avoid modifying original
        this.availableItems = new HashMap<>(availableItems);
    }
    
    /**
     * Evaluate all recipes (crafting & smelting) for an item and return ranked results.
     */
    public List<EvaluatedTree> evaluateRecipes(ItemStack target, int count) {
        List<EvaluatedTree> results = new ArrayList<>();
        
        // 1. Gather all candidate recipes (Crafting + Smelting)
        List<RecipeData> candidates = new ArrayList<>();
        
        // Crafting recipes
        for (CraftingRecipe r : RecipeLookup.findByOutput(level, target, 20)) {
            candidates.add(new RecipeData(r));
        }
        
        // Smelting recipes
        for (SmeltingRecipe r : RecipeLookup.findAllSmeltingByOutput(level, target, 10)) {
            candidates.add(new RecipeData(r));
        }
        
        // 2. Evaluate each candidate
        for (RecipeData recipe : candidates) {
            EvaluatedTree tree = evaluateTree(recipe, count, 0, new HashMap<>(availableItems));
            results.add(tree);
        }
        
        // 3. Sort: valid trees first, then by missing count
        results.sort((a, b) -> {
            if (a.isComplete() && !b.isComplete()) return -1;
            if (!a.isComplete() && b.isComplete()) return 1;
            return Integer.compare(a.getMissingCount(), b.getMissingCount());
        });
        
        return results;
    }
    
    /**
     * Unified evaluation for any recipe type.
     */
    private EvaluatedTree evaluateTree(RecipeData recipe, int count, int depth, Map<String, Integer> remaining) {
        // Calculate how many operations we need
        // For crafting (e.g. 1 log -> 4 planks), need = ceil(target / output_per_craft)
        // For smelting (e.g. 1 ore -> 1 ingot), usually 1 to 1, but generic formula works.
        ItemStack outputStack = recipe.getResultItem(level);
        int outputCount = outputStack.getCount();
        int opsNeeded = (int) Math.ceil((double) count / outputCount);
        
        // Determine workstation
        String workstation = recipe.isSmelting ? "minecraft:furnace" : "minecraft:crafting_table";
        
        // Collect ingredients
        Map<String, IngredientResult> mergedIngredients = new LinkedHashMap<>();
        int totalMissing = 0;
        
        // 1. Regular Ingredients
        List<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            // For smelting, we usually have 1 ingredient. For crafting, multiple.
            // We need 1 item per op per ingredient slot usually (unless custom recipe, but standard is 1)
            // Smelting recipes in MC are 1 input -> 1 output usually.
            
            IngredientResult bestForSlot = evaluateIngredient(ingredient, 1, depth, remaining);
             
            // Merge results by item ID
            String itemId = bestForSlot.getItemId();
            if (mergedIngredients.containsKey(itemId)) {
                mergedIngredients.put(itemId, mergedIngredients.get(itemId).merge(bestForSlot));
            } else {
                mergedIngredients.put(itemId, bestForSlot);
            }
        }
        
        List<IngredientResult> finalIngredients = new ArrayList<>();
        
        // Scale ingredients by operations needed
        for (IngredientResult ir : mergedIngredients.values()) {
            IngredientResult scaled = ir.scale(opsNeeded);
            
            // If smelting, assign slot 0 (input)
            if (recipe.isSmelting) {
                // Accessing private field or recreating? created constructor for copy
                scaled = new IngredientResult(scaled.getItemId(), scaled.getNeed(), scaled.getHave(),
                        scaled.getMissing(), scaled.getSubTree(), 0);
            }
            
            finalIngredients.add(scaled);
            totalMissing += scaled.getMissing();
        }
        
        // 2. Fuel (Smelting Only)
        if (recipe.isSmelting) {
            IngredientResult fuel = calculateFuelNeeded(opsNeeded, remaining);
            if (fuel != null) {
                finalIngredients.add(fuel);
                totalMissing += fuel.getMissing();
            }
        }
        
        return new EvaluatedTree(
                recipe.getId(), 
                workstation, 
                outputStack.getItem().builtInRegistryHolder().key().location().toString(),
                count, 
                finalIngredients, 
                totalMissing
        );
    }
    
    private IngredientResult evaluateIngredient(Ingredient ingredient, int neededPerOp, int depth, Map<String, Integer> remaining) {
        ItemStack[] possibleItems = ingredient.getItems();
        IngredientResult bestResult = null;
        
        for (ItemStack itemStack : possibleItems) {
            String itemId = itemStack.getItem().builtInRegistryHolder().key().location().toString();
            int have = remaining.getOrDefault(itemId, 0);
            
            // Logic: We need 'neededPerOp' for THIS single step of recursion.
            // But wait, 'evaluateTree' calls this with needed=1 usually.
            // The scaling happens in 'evaluateTree'.
            
            int needed = neededPerOp;
            int missing = Math.max(0, needed - have);
            
            if (missing == 0) {
                // We have it
                remaining.merge(itemId, -needed, Integer::sum);
                return new IngredientResult(itemId, needed, have, 0, null);
            }
            
            // We are missing some. Can we craft/smelt it?
            EvaluatedTree subTree = null;
            if (depth < maxDepth && missing > 0) {
                // RECURSIVE SEARCH
                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    ItemStack targetForSub = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                    
                    // Find ALL recipes (Crafting + Smelting) for this missing item
                    List<RecipeData> subCandidates = new ArrayList<>();
                    RecipeLookup.findByOutput(level, targetForSub, 10).forEach(r -> subCandidates.add(new RecipeData(r)));
                    RecipeLookup.findAllSmeltingByOutput(level, targetForSub, 10).forEach(r -> subCandidates.add(new RecipeData(r)));
                    
                    EvaluatedTree bestSub = null;
                    for (RecipeData subRecipe : subCandidates) {
                        // We need to produce 'missing' amount
                        EvaluatedTree evaluated = evaluateTree(subRecipe, missing, depth + 1, new HashMap<>(remaining));
                        
                        if (bestSub == null || 
                           (evaluated.isComplete() && !bestSub.isComplete()) ||
                           (!evaluated.isComplete() && !bestSub.isComplete() && evaluated.getMissingCount() < bestSub.getMissingCount())) {
                            bestSub = evaluated;
                        }
                        if (bestSub.isComplete()) break;
                    }
                    subTree = bestSub;
                }
            }
            
            int effectiveMissing = (subTree != null && subTree.isComplete()) ? 0 : missing;
            IngredientResult result = new IngredientResult(itemId, needed, have, effectiveMissing, subTree);
            
            if (bestResult == null || result.getMissing() < bestResult.getMissing()) {
                bestResult = result;
            }
            
            // Optimistic prune
            if (effectiveMissing == 0) {
                remaining.merge(itemId, -have, Integer::sum);
                return result; 
            }
        }
        
        // Fallback if no items matched (shouldn't happen with valid ingredients)
        if (bestResult == null && possibleItems.length > 0) {
             String defaultId = possibleItems[0].getItem().builtInRegistryHolder().key().location().toString();
             return new IngredientResult(defaultId, neededPerOp, 0, neededPerOp, null);
        }
        
        return bestResult;
    }
    
    private IngredientResult calculateFuelNeeded(int opsNeeded, Map<String, Integer> remaining) {
        // Standard furnace logic: 200 ticks per op
        int totalTicks = opsNeeded * 200;
        
        // Find best fuel in inventory
        String bestFuelId = null;
        int bestBurnTime = 0;
        int bestHave = 0;
        
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() <= 0) continue;
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(
                    new ItemStack(BuiltInRegistries.ITEM.get(rl)), null);
            
            if (burnTime > 0) {
                // Heuristic: Prefer fuel that we have enough of, or longest burn time
                if (burnTime > bestBurnTime) {
                    bestBurnTime = burnTime;
                    bestFuelId = entry.getKey();
                    bestHave = entry.getValue();
                }
            }
        }
        
        if (bestFuelId == null) {
             // Fallback to coal calc
             int coalBurn = 1600;
             int coalNeeded = (int) Math.ceil((double) totalTicks / coalBurn);
             return new IngredientResult("minecraft:coal", coalNeeded, 0, coalNeeded, null, 1);
        }
        
        int needed = (int) Math.ceil((double) totalTicks / bestBurnTime);
        int missing = Math.max(0, needed - bestHave);
        
        if (needed <= bestHave) {
            remaining.merge(bestFuelId, -needed, Integer::sum);
        }
        
        return new IngredientResult(bestFuelId, needed, bestHave, missing, null, 1);
    }
    
    // ================== Data Classes ================== //
    
    /**
     * Unified wrapper for CraftingRecipe and SmeltingRecipe.
     */
    private static class RecipeData {
        final String id;
        final boolean isSmelting;
        final CraftingRecipe craftingRecipe;
        final SmeltingRecipe smeltingRecipe;
        
        RecipeData(CraftingRecipe r) {
            this.id = r.getId().toString();
            this.isSmelting = false;
            this.craftingRecipe = r;
            this.smeltingRecipe = null;
        }
        
        RecipeData(SmeltingRecipe r) {
            this.id = r.getId().toString();
            this.isSmelting = true;
            this.craftingRecipe = null;
            this.smeltingRecipe = r;
        }
        
        String getId() { return id; }
        
        ItemStack getResultItem(ServerLevel level) {
            if (isSmelting) return smeltingRecipe.getResultItem(level.registryAccess());
            return craftingRecipe.getResultItem(level.registryAccess());
        }
        
        List<Ingredient> getIngredients() {
            if (isSmelting) return smeltingRecipe.getIngredients();
            return craftingRecipe.getIngredients();
        }
    }
    
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
        
        public java.util.LinkedHashMap<String, Integer> toFlattenedSteps() {
            java.util.LinkedHashMap<String, Integer> steps = new java.util.LinkedHashMap<>();
            collectSteps(steps);
            return steps;
        }
        
        private void collectSteps(java.util.LinkedHashMap<String, Integer> steps) {
            for (IngredientResult ingredient : ingredients) {
                if (ingredient.hasSubTree()) {
                    ingredient.getSubTree().collectSteps(steps);
                }
            }
            steps.merge(recipeId, count, Integer::sum);
        }
    }
    
    public static class IngredientResult {
        private final String itemId;
        private final int need;
        private final int have;
        private final int missing;
        private final EvaluatedTree subTree;
        private final int targetSlot;  // -1=mix, 0=input, 1=fuel
        
        public IngredientResult(String itemId, int need, int have, int missing, EvaluatedTree subTree) {
            this(itemId, need, have, missing, subTree, -1);
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
        
        public IngredientResult merge(IngredientResult other) {
            EvaluatedTree mergedTree = this.subTree != null ? this.subTree : other.subTree;
            return new IngredientResult(
                    this.itemId,
                    this.need + other.need,
                    this.have, 
                    this.missing + other.missing,
                    mergedTree,
                    this.targetSlot // Preserve slot if possible
            );
        }
        
        public IngredientResult scale(int multiplier) {
            return new IngredientResult(
                    this.itemId,
                    this.need * multiplier,
                    this.have,
                    this.missing * multiplier,
                    this.subTree,
                    this.targetSlot
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

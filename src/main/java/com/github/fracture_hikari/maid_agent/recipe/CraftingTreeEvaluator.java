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
        List<EvaluationResult> results = new ArrayList<>();

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
        Map<String, Integer> initialInv = new HashMap<>(availableItems);
        for (RecipeData recipe : candidates) {
            EvaluationResult result = evaluateTree(recipe, count, 0, initialInv, new HashSet<>());
            results.add(result);
        }

        // 3. Sort: valid trees first, then by missing count, then cost, then complexity
        results.sort((a, b) -> compareTrees(a, b, initialInv));

        List<EvaluatedTree> finalTrees = new ArrayList<>();
        for (EvaluationResult r : results) {
            finalTrees.add(r.tree());
        }
        return finalTrees;
    }

    private int compareTrees(EvaluationResult resA, EvaluationResult resB, Map<String, Integer> initialInv) {
        EvaluatedTree a = resA.tree();
        EvaluatedTree b = resB.tree();

        boolean aComplete = a.isComplete();
        boolean bComplete = b.isComplete();

        // 1. Completeness
        if (aComplete && !bComplete) return -1;
        if (!aComplete && bComplete) return 1;

        // 2. Missing Count (if incomplete)
        if (!aComplete) {
            return Integer.compare(a.getMissingCount(), b.getMissingCount());
        }

        // 3. Consumption Cost (Inventory Efficiency)
        int costA = calculateConsumed(initialInv, resA.remainingInventory());
        int costB = calculateConsumed(initialInv, resB.remainingInventory());
        if (costA != costB) {
            return Integer.compare(costA, costB);
        }

        // 4. Tree Complexity (Size) - Avoids loops like Ingot -> Block -> Ingot
        return Integer.compare(a.getTreeSize(), b.getTreeSize());
    }

    private int calculateConsumed(Map<String, Integer> start, Map<String, Integer> end) {
        int consumed = 0;
        for (Map.Entry<String, Integer> entry : start.entrySet()) {
            int startCount = entry.getValue();
            int endCount = end.getOrDefault(entry.getKey(), 0);
            if (endCount < startCount) {
                consumed += (startCount - endCount);
            }
        }
        return consumed;
    }

    private record EvaluationResult(EvaluatedTree tree, Map<String, Integer> remainingInventory) {}

    /**
     * Unified evaluation for any recipe type.
     */
    private EvaluationResult evaluateTree(RecipeData recipe, int count, int depth,
                                          Map<String, Integer> inputInventory, Set<String> visitedRecipes) {
        // Cycle detection
        if (visitedRecipes.contains(recipe.getId())) {
            return createIncompleteResult(recipe, count, inputInventory);
        }
        Set<String> newVisited = new HashSet<>(visitedRecipes);
        newVisited.add(recipe.getId());

        // Working inventory (mutable)
        Map<String, Integer> currentInventory = new HashMap<>(inputInventory);

        ItemStack outputStackTemplate = recipe.getResultItem(level);
        int outputPerCraft = outputStackTemplate.getCount();
        int opsNeeded = (count + outputPerCraft - 1) / outputPerCraft;

        String workstation = recipe.isSmelting ? "minecraft:furnace" : "minecraft:crafting_table";

        // 1. Merge Ingredients Strategy
        Map<String, Ingredient> mergedIngredients = new LinkedHashMap<>();
        Map<String, Integer> mergedCounts = new HashMap<>();

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) continue;
            String key = items[0].getItem().builtInRegistryHolder().key().location().toString();
            if (!mergedIngredients.containsKey(key)) mergedIngredients.put(key, ingredient);
            mergedCounts.merge(key, 1, Integer::sum);
        }

        List<IngredientResult> finalIngredients = new ArrayList<>();
        int totalMissing = 0;
        int currentTreeSize = 1; // Count self

        // Evaluate merged requirements
        for (Map.Entry<String, Ingredient> entry : mergedIngredients.entrySet()) {
            String key = entry.getKey();
            Ingredient ingredient = entry.getValue();
            int slotsUsing = mergedCounts.get(key);

            // Total items needed = ops * slots_using_ingredient
            int totalNeed = opsNeeded * slotsUsing;

            IngredientResult result = evaluateIngredient(ingredient, totalNeed, depth, currentInventory, newVisited);

            if (recipe.isSmelting) {
                result = new IngredientResult(result.getItemStack(), result.getNeed(), result.getHave(),
                        result.getMissing(), result.getSubTree(), 0);
            }

            finalIngredients.add(result);
            totalMissing += result.getMissing();
            if (result.hasSubTree()) {
                currentTreeSize += result.getSubTree().getTreeSize();
            }
        }

        // 2. Fuel (Smelting Only)
        if (recipe.isSmelting) {
            IngredientResult fuel = calculateFuelNeeded(opsNeeded, currentInventory, depth, newVisited);
            if (fuel != null) {
                finalIngredients.add(fuel);
                totalMissing += fuel.getMissing();
                // Fuel usually doesn't have a subtree, but if we add complex fuel logic later, update size here
                if (fuel.hasSubTree()) {
                   currentTreeSize += fuel.getSubTree().getTreeSize();
                }
            }
        }

        ItemStack resultStack = outputStackTemplate.copy();
        resultStack.setCount(opsNeeded * outputPerCraft);

        EvaluatedTree tree = new EvaluatedTree(
                recipe.getId(),
                workstation,
                resultStack,
                finalIngredients,
                totalMissing,
                currentTreeSize
        );
        return new EvaluationResult(tree, currentInventory);
    }

    private EvaluationResult createIncompleteResult(RecipeData recipe, int count, Map<String, Integer> inventory) {
        ItemStack res = recipe.getResultItem(level).copy();
        res.setCount(count);
        return new EvaluationResult(
                new EvaluatedTree(recipe.getId(), "unknown", res, Collections.emptyList(), count, 1),
                inventory
        );
    }

    private IngredientResult evaluateIngredient(Ingredient ingredient, int needed, int depth,
                                                Map<String, Integer> inventory, Set<String> visitedRecipes) {
        ItemStack[] possibleItems = ingredient.getItems();
        IngredientResult bestResult = null;
        Map<String, Integer> bestResultInventory = null;

        for (ItemStack itemStack : possibleItems) {
            // Try this candidate with a COPY of inventory
            Map<String, Integer> candidateInv = new HashMap<>(inventory);

            String itemId = itemStack.getItem().builtInRegistryHolder().key().location().toString();
            int have = candidateInv.getOrDefault(itemId, 0);

            int take = Math.min(have, needed);
            int missing = needed - take;

            if (take > 0) {
                candidateInv.merge(itemId, -take, Integer::sum);
            }

            EvaluatedTree subTree = null;
            if (depth < maxDepth && missing > 0) {
                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    ItemStack targetForSub = new ItemStack(BuiltInRegistries.ITEM.get(rl));

                    // Find ALL recipes (Crafting + Smelting) for this missing item
                    List<RecipeData> subCandidates = new ArrayList<>();
                    RecipeLookup.findByOutput(level, targetForSub, 10).forEach(r -> subCandidates.add(new RecipeData(r)));
                    RecipeLookup.findAllSmeltingByOutput(level, targetForSub, 10).forEach(r -> subCandidates.add(new RecipeData(r)));

                    EvaluationResult bestSubEval = null;
                    for (RecipeData subRecipe : subCandidates) {
                        EvaluationResult subEval = evaluateTree(subRecipe, missing, depth + 1, candidateInv, visitedRecipes);

                        // --- SELECTION LOGIC UPDATE START ---
                        if (bestSubEval == null) {
                            bestSubEval = subEval;
                        } else {
                            int comparison = compareTrees(subEval, bestSubEval, candidateInv);
                            if (comparison < 0) {
                                bestSubEval = subEval;
                            }
                        }
                        // --- SELECTION LOGIC UPDATE END ---
                    }

                    if (bestSubEval != null) {
                        subTree = bestSubEval.tree();
                        candidateInv = bestSubEval.remainingInventory();
                    }
                }
            }

            int effectiveMissing = (subTree != null && subTree.isComplete()) ? 0 : missing;

            ItemStack resultStack = itemStack.copy();
            resultStack.setCount(needed);

            IngredientResult result = new IngredientResult(resultStack, needed, have, effectiveMissing, subTree);

            if (bestResult == null) {
                bestResult = result;
                bestResultInventory = candidateInv;
            } else {
                // Same comparison logic as fuel
                if (compareIngredientResults(result, bestResult, inventory, candidateInv, bestResultInventory) < 0) {
                    bestResult = result;
                    bestResultInventory = candidateInv;
                }
            }

            // Optimistic prune: Perfect result found (Complete, minimal size)
            if (effectiveMissing == 0 && (subTree == null || subTree.getTreeSize() <= 1)) {
                break;
            }
        }

        // Fallback if no items matched
        if (bestResult == null && possibleItems.length > 0) {
            ItemStack defaultStack = possibleItems[0].copy();
            defaultStack.setCount(needed);
            return new IngredientResult(defaultStack, needed, 0, needed, null);
        }

        if (bestResult != null && bestResultInventory != null) {
            // Commit the inventory changes from the best path
            inventory.clear();
            inventory.putAll(bestResultInventory);
        }

        return bestResult;
    }
    
    private int compareIngredientResults(IngredientResult resA, IngredientResult resB, 
                                         Map<String, Integer> startInv, 
                                         Map<String, Integer> invA, Map<String, Integer> invB) {
         // 1. Minimize Missing
         if (resA.getMissing() < resB.getMissing()) return -1;
         if (resA.getMissing() > resB.getMissing()) return 1;
         
         // 2. Minimize Cost (Inventory Consumption)
         int costA = calculateConsumed(startInv, invA);
         int costB = calculateConsumed(startInv, invB);
         if (costA != costB) return Integer.compare(costA, costB);
         
         // 3. Minimize Tree Size
         int sizeA = resA.hasSubTree() ? resA.getSubTree().getTreeSize() : 0;
         int sizeB = resB.hasSubTree() ? resB.getSubTree().getTreeSize() : 0;
         return Integer.compare(sizeA, sizeB);
    }

    private IngredientResult calculateFuelNeeded(int opsNeeded, Map<String, Integer> remaining, int depth, Set<String> visitedRecipes) {
        int totalTicks = opsNeeded * 200;

        Set<ItemStack> candidates = new HashSet<>();
        
        // 1. Existing burnables in inventory
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() <= 0) continue;
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                 ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                 if (net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0) {
                     candidates.add(stack);
                 }
            }
        }
        
        // 2. Common craftable fuels (recurse check)
        // Add safe defaults to check if we don't have direct fuel
        addCommonFuel(candidates, net.minecraft.world.item.Items.COAL_BLOCK);
        addCommonFuel(candidates, net.minecraft.world.item.Items.DRIED_KELP_BLOCK);
        addCommonFuel(candidates, net.minecraft.world.item.Items.CHARCOAL);
        addCommonFuel(candidates, net.minecraft.world.item.Items.COAL);
        // Add all planks via tag
        BuiltInRegistries.ITEM.getTag(net.minecraft.tags.ItemTags.PLANKS).ifPresent(opt -> opt.stream().forEach(holder -> {
            candidates.add(new ItemStack(holder.value()));
        }));
        addCommonFuel(candidates, net.minecraft.world.item.Items.BLAZE_ROD);
        addCommonFuel(candidates, net.minecraft.world.item.Items.LAVA_BUCKET);
        addCommonFuel(candidates, net.minecraft.world.item.Items.STICK); // Last resort

        IngredientResult bestFuelResult = null;
        Map<String, Integer> bestFuelInv = null;

        for (ItemStack fuelStack : candidates) {
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuelStack, null);
            if (burnTime <= 0) continue;
            
            // Calculate needed for THIS fuel type
            int needed = (int) Math.ceil((double) totalTicks / burnTime);
            
            // Check if we can supply this fuel (recursively)
            // Reuse evaluateIngredient logic by creating a dummy ingredient
            Ingredient dummyIng = Ingredient.of(fuelStack);
            
            // Sandbox
            Map<String, Integer> candidateInv = new HashMap<>(remaining);
            IngredientResult res = evaluateIngredient(dummyIng, needed, depth, candidateInv, visitedRecipes);
            // Fix slot for fuel (1)
            res = new IngredientResult(res.getItemStack(), res.getNeed(), res.getHave(), res.getMissing(), res.getSubTree(), 1);
            
            if (bestFuelResult == null) {
                bestFuelResult = res;
                bestFuelInv = candidateInv;
            } else {
                // Compare!
                if (compareIngredientResults(res, bestFuelResult, remaining, candidateInv, bestFuelInv) < 0) {
                    bestFuelResult = res;
                    bestFuelInv = candidateInv;
                }
            }
        }
        
        // Fallback default
        if (bestFuelResult == null) {
            int coalBurn = 1600;
            int coalNeeded = (int) Math.ceil((double) totalTicks / coalBurn);
            ItemStack coal = new ItemStack(net.minecraft.world.item.Items.COAL, coalNeeded);
            return new IngredientResult(coal, coalNeeded, 0, coalNeeded, null, 1);
        }
        
        if (bestFuelInv != null) {
            remaining.clear();
            remaining.putAll(bestFuelInv);
        }
        return bestFuelResult;
    }
    
    private void addCommonFuel(Set<ItemStack> set, net.minecraft.world.item.Item item) {
        set.add(new ItemStack(item));
    }

    // ================== Data Classes ================== //

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
        private final ItemStack output;
        private final List<IngredientResult> ingredients;
        private final int missingCount;
        private final int treeSize; // New field

        public EvaluatedTree(String recipeId, String workstation, ItemStack output,
                             List<IngredientResult> ingredients, int missingCount) {
            this(recipeId, workstation, output, ingredients, missingCount, 1);
        }

        public EvaluatedTree(String recipeId, String workstation, ItemStack output,
                             List<IngredientResult> ingredients, int missingCount, int treeSize) {
            this.recipeId = recipeId;
            this.workstation = workstation;
            this.output = output.copy();
            this.ingredients = ingredients;
            this.missingCount = missingCount;
            this.treeSize = treeSize;
        }

        public boolean isComplete() { return missingCount == 0; }
        public int getMissingCount() { return missingCount; }
        public int getTreeSize() { return treeSize; }
        public String getRecipeId() { return recipeId; }
        public String getWorkstation() { return workstation; }
        public ItemStack getOutput() { return output; }
        public int getCount() { return output.getCount(); }
        public List<IngredientResult> getIngredients() { return ingredients; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"recipe_id\": \"%s\", ", recipeId));
            sb.append(String.format("\"workstation\": \"%s\", ", workstation));
            sb.append(String.format("\"complete\": %s, ", isComplete()));
            sb.append(String.format("\"size\": %d, ", treeSize));
            if (!isComplete()) {
                sb.append(String.format("\"missing_total\": %d, ", missingCount));
            }
            sb.append("\"ingredients\": [");
            for (int i = 0; i < ingredients.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(ingredients.get(i).toJson());
            }
            sb.append("], ");
            sb.append(String.format("\"output\": \"%s\", \"count\": %d}",
                    output.getItem().builtInRegistryHolder().key().location().toString(), output.getCount()));
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
            steps.merge(recipeId, output.getCount(), Integer::sum);
        }
    }

    public static class IngredientResult {
        private final ItemStack itemStack;
        private final int need;
        private final int have;
        private final int missing;
        private final EvaluatedTree subTree;
        private final int targetSlot;

        public IngredientResult(ItemStack itemStack, int need, int have, int missing, EvaluatedTree subTree) {
            this(itemStack, need, have, missing, subTree, -1);
        }

        public IngredientResult(ItemStack itemStack, int need, int have, int missing, EvaluatedTree subTree, int targetSlot) {
            this.itemStack = itemStack.copy();
            this.itemStack.setCount(need);
            this.need = need;
            this.have = have;
            this.missing = missing;
            this.subTree = subTree;
            this.targetSlot = targetSlot;
        }

        public String getItemId() { return itemStack.getItem().builtInRegistryHolder().key().location().toString(); }
        public ItemStack getItemStack() { return itemStack; }
        public int getNeed() { return need; }
        public int getHave() { return have; }
        public int getMissing() { return missing; }
        public boolean hasSubTree() { return subTree != null; }
        public EvaluatedTree getSubTree() { return subTree; }
        public int getTargetSlot() { return targetSlot; }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"item\": \"%s\", \"need\": %d, \"have\": %d, \"missing\": %d",
                    getItemId(), need, have, missing));
            if (subTree != null) {
                sb.append(", \"craft_from\": ").append(subTree.toJson());
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
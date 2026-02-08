package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.config.CraftConfig;
import com.github.fracture_hikari.maid_agent.config.JeiConfig;
import com.github.fracture_hikari.maid_agent.maid.memory.SlotMapping;
import com.github.fracture_hikari.maid_agent.recipe.CraftingTreeEvaluator;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.fracture_hikari.maid_agent.util.RecipeLookup;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * LLM function to craft items.
 * Searches recipes, checks ingredients, and queues crafting work.
 * With dry_run=true, only analyzes without executing.
 */
public class CraftItemFunction implements IFunctionCall<CraftItemFunction.Params> {
    private static final String FUNCTION_ID = "craft_item";
    private static final String FUNCTION_DESC = """
            Craft an item using available recipes. Checks inventory for ingredients.
            Use item_search first to find valid item IDs.
            Set dry_run=true to only analyze recipe without executing.
            Use recursive_search=true to find full crafting tree for missing ingredients.
            Returns: completion summary with [CRAFT] item_id xCount - Success/Failed.""";
    
    private static final String ITEM_PARAM_ID = "item_id";
    private static final String ITEM_PARAM_DESC = """
            item_id (string, required): The item to craft in namespace:name format.
            Example: minecraft:stick, minecraft:iron_pickaxe""";
    
    private static final String COUNT_PARAM_ID = "count";
    private static final String COUNT_PARAM_DESC = """
            count (integer, optional): Number of items to craft. Default: 1.""";
    
    private static final String RECURSIVE_PARAM_ID = "recursive_search";
    private static final String RECURSIVE_PARAM_DESC = """
            recursive_search (boolean, optional): If true, recursively find recipes for missing ingredients. Default: false.""";
    
    private static final String DRY_RUN_PARAM_ID = "dry_run";
    private static final String DRY_RUN_PARAM_DESC = """
            dry_run (boolean, optional): If true, only analyze recipe without executing. Default: false.""";
    
    private static final String RECIPE_ID_PARAM_ID = "recipe_id";
    private static final String RECIPE_ID_PARAM_DESC = """
            recipe_id (string, optional): Specific recipe to use. If omitted, shows all available recipes.""";

    @Override
    public String getId() {
        return FUNCTION_ID;
    }

    @Override
    public String getDescription(EntityMaid maid) {
        return FUNCTION_DESC;
    }

    @Override
    public Parameter addParameters(ObjectParameter root, EntityMaid maid) {
        StringParameter itemParam = StringParameter.create();
        itemParam.setDescription(ITEM_PARAM_DESC);
        itemParam.setMinLength(1);
        root.addProperties(ITEM_PARAM_ID, itemParam);

        IntegerParameter countParam = IntegerParameter.create();
        countParam.setDescription(COUNT_PARAM_DESC);
        countParam.setMinimum(1);
        countParam.setMaximum(CraftConfig.MAX_CRAFT_OUTPUT.get());
        root.addProperties(COUNT_PARAM_ID, countParam, false);

        BoolParameter recursiveParam = BoolParameter.create();
        recursiveParam.setDescription(RECURSIVE_PARAM_DESC);
        root.addProperties(RECURSIVE_PARAM_ID, recursiveParam, false);
        
        BoolParameter dryRunParam = BoolParameter.create();
        dryRunParam.setDescription(DRY_RUN_PARAM_DESC);
        root.addProperties(DRY_RUN_PARAM_ID, dryRunParam, false);
        
        StringParameter recipeIdParam = StringParameter.create();
        recipeIdParam.setDescription(RECIPE_ID_PARAM_DESC);
        root.addProperties(RECIPE_ID_PARAM_ID, recipeIdParam, false);

        return root;
    }

    @Override
    public Codec<Params> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf(ITEM_PARAM_ID).forGetter(Params::itemId),
                        Codec.INT.optionalFieldOf(COUNT_PARAM_ID, 1).forGetter(Params::count),
                        Codec.BOOL.optionalFieldOf(RECURSIVE_PARAM_ID, false).forGetter(Params::recursiveSearch),
                        Codec.BOOL.optionalFieldOf(DRY_RUN_PARAM_ID, false).forGetter(Params::dryRun),
                        Codec.STRING.optionalFieldOf(RECIPE_ID_PARAM_ID, "").forGetter(Params::recipeId)
                ).apply(instance, Params::new));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) { return null; }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid, String toolCallId) {
        // Crafting requires maid_storage_manager
        if (!Integrations.maidStorageManager()) {
            return new ToolResponse(Integrations.getMsmRequiredMessage());
        }
        
        if (!(maid.level() instanceof ServerLevel level)) {
            return new ToolResponse("Error: Not on server");
        }
        
        // Parse item ID
        if (!ItemIdUtils.isValid(params.itemId())) {
            return new ToolResponse("Unknown item: " + params.itemId() + ". Use jei_item_search to find valid item IDs.");
        }
        
        ItemStack targetItem = ItemIdUtils.createStack(params.itemId());
        int count = Math.min(params.count(), CraftConfig.MAX_CRAFT_OUTPUT.get());
        
        // Get maid's inventory and storage
        Map<String, Integer> maidInventory = InventoryUtils.toItemCountMap(InventoryUtils.getMaidInventory(maid));
        Map<String, Integer> storageInventory = java.util.Collections.emptyMap();
        
        StringBuilder sb = new StringBuilder();
        
        // If recipe_id is specified, use that specific recipe

        
        // No recipe_id specified - list available recipes
        // If recursive_search, use smart tree evaluation
        ToolResponse faultResponse = new ToolResponse(String.format(
                "No recipe found for %s. Use jei_item_search to find valid item IDs.",
                params.itemId()));

        
        if (params.recursiveSearch()) {
            CraftingTreeEvaluator evaluator = new CraftingTreeEvaluator(level, maidInventory);
            List<CraftingTreeEvaluator.EvaluatedTree> trees = evaluator.evaluateRecipes(targetItem, count);
            
            if (trees.isEmpty()) {
                return faultResponse;
            }
            
            // Check for valid (complete) trees
            List<CraftingTreeEvaluator.EvaluatedTree> validTrees = trees.stream()
                    .filter(CraftingTreeEvaluator.EvaluatedTree::isComplete)
                    .toList();
            
            if (!validTrees.isEmpty()) {
                // Return the first valid tree
                CraftingTreeEvaluator.EvaluatedTree best = validTrees.get(0);
                sb.append("Found valid crafting path (all materials available):\n\n");
                sb.append(best.toJson());
                
                if (!params.dryRun()) {
                    List<PendingTask> allTasks = new ArrayList<>();
                    // Generate graph directly from the evaluated tree
                    generateTaskGraph(best, maid, level, allTasks);
                    
                    // Enqueue all tasks with toolCallId tracking
                    TaskQueueHelper.getOrCreateQueue(maid).enqueue(allTasks, toolCallId);
                    
                    // Return PENDING to wait for async completion
                    return ToolResponse.PENDING;
                } else {
                    sb.append("\n\n[dry_run=true: Analysis only, no task queued]");
                }
            } else {
                // No valid trees - show top N alternatives with missing items
                int maxAlt = CraftConfig.MAX_ALTERNATE_TREES.get();
                List<CraftingTreeEvaluator.EvaluatedTree> alternatives = trees.stream()
                        .limit(maxAlt)
                        .toList();
                
                sb.append(String.format("No complete crafting path found. Showing %d best option(s):\n\n", alternatives.size()));
                for (int i = 0; i < alternatives.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(alternatives.get(i).toJson());
                }
                sb.append("\n\nMissing items must be obtained before crafting.");
            }
            
            return new ToolResponse(sb.toString());
        } else if (!params.recipeId().isEmpty()) {
            // Specific recipe requesting without recursive search
            Optional<CraftingRecipe> specificRecipe = RecipeLookup.findById(level, params.recipeId());
            if (specificRecipe.isEmpty()) {
                return new ToolResponse("Recipe not found: " + params.recipeId());
            }
            CraftingRecipe recipe = specificRecipe.get();
            appendRecipeJson(sb, recipe, count, maidInventory, storageInventory, level, false);
            
            if (!params.dryRun()) {
                 List<PendingTask> allTasks = new ArrayList<>();
                 createSingleTask(maid, level, recipe, count, allTasks);
                 TaskQueueHelper.getOrCreateQueue(maid).enqueue(allTasks, toolCallId);
                 
                 // Return PENDING to wait for async completion
                 return ToolResponse.PENDING;
            } else {
                sb.append("\n\n[dry_run=true: Analysis only, no task queued]");
            }
             return new ToolResponse(sb.toString());
        }
        
        // Simple listing without tree evaluation (default fallback when no params)
        int maxRecipes = JeiConfig.RECIPE_SEARCH_MAX_RESULTS.get();
        List<CraftingRecipe> craftingRecipes = RecipeLookup.findByOutput(level, targetItem, maxRecipes);
        Optional<SmeltingRecipe> smeltingRecipe = RecipeLookup.findSmeltingByOutput(level, targetItem);
        
        if (craftingRecipes.isEmpty() && smeltingRecipe.isEmpty()) {
            return faultResponse;
        }
        
        sb.append(String.format("Found %d recipe(s) for %s:\n\n", 
                craftingRecipes.size() + (smeltingRecipe.isPresent() ? 1 : 0), params.itemId()));
        
        // List all crafting recipes
        for (int i = 0; i < craftingRecipes.size(); i++) {
            if (i > 0) sb.append("\n");
            appendRecipeJson(sb, craftingRecipes.get(i), count, maidInventory, storageInventory, level, false);
        }
        
        // Add smelting recipe if present
        if (smeltingRecipe.isPresent()) {
            SmeltingRecipe recipe = smeltingRecipe.get();
            String recipeIdStr = recipe.getId().toString();
            
            if (!craftingRecipes.isEmpty()) sb.append("\n");
            sb.append(String.format("{\"recipe_id\": \"%s\", ", recipeIdStr));
            sb.append("\"type\": \"smelting\", ");
            sb.append("\"workstation\": \"minecraft:furnace\", ");
            sb.append("\"ingredients\": ");
            
            List<IngredientNeed> needs = analyzeIngredients(recipe.getIngredients(), count);
            appendIngredientsJson(sb, needs, maidInventory, storageInventory, level, false, 0);
            sb.append(", ");
            
            sb.append("\"requires_fuel\": true, ");
            sb.append(String.format("\"output\": \"%s\", \"count\": %d}", params.itemId(), count));
        }
        
        sb.append("\n\nTo craft, call again with recipe_id parameter to select which recipe to use.");
        if (params.dryRun()) {
            sb.append("\n[dry_run=true: Analysis only]");
        }
        
        return new ToolResponse(sb.toString());
    }
    
    /**
     * Append a single recipe as JSON.
     */
    private void appendRecipeJson(StringBuilder sb, CraftingRecipe recipe, int count,
                                   Map<String, Integer> maidInv, Map<String, Integer> storageInv,
                                   ServerLevel level, boolean recursive) {
        String recipeIdStr = recipe.getId().toString();
        String type = recipe.getClass().getSimpleName().contains("Shapeless") ? "shapeless" : "shaped";
        
        sb.append(String.format("{\"recipe_id\": \"%s\", ", recipeIdStr));
        sb.append(String.format("\"type\": \"%s\", ", type));
        sb.append("\"workstation\": \"minecraft:crafting_table\", ");
        sb.append("\"ingredients\": ");
        
        List<IngredientNeed> needs = analyzeIngredients(recipe.getIngredients(), count);
        appendIngredientsJson(sb, needs, maidInv, storageInv, level, recursive, 0);
        sb.append(", ");
        
        ItemStack result = recipe.getResultItem(level.registryAccess());
        sb.append(String.format("\"output\": \"%s\", \"count\": %d}", 
                result.getItem().builtInRegistryHolder().key().location().toString(), 
                result.getCount() * count));
    }
    
    /**
     * Recursively generate task graph from evaluated tree.
     * @return The task created for the current tree node.
     */
    private PendingTask generateTaskGraph(CraftingTreeEvaluator.EvaluatedTree tree, EntityMaid maid, 
                                          ServerLevel level, List<PendingTask> accumulator) {
        // 1. Determine Workstation Type
        String wsStr = tree.getWorkstation();
        PendingTask.WorkstationType wsType = PendingTask.WorkstationType.CRAFTING_TABLE;
        if (wsStr.contains("furnace") || wsStr.contains("smoker") || wsStr.contains("blast")) {
            if (wsStr.contains("blast")) wsType = PendingTask.WorkstationType.BLAST_FURNACE;
            else if (wsStr.contains("smoker")) wsType = PendingTask.WorkstationType.SMOKER;
            else wsType = PendingTask.WorkstationType.FURNACE;
        }
        
        // 2. Create Task
        // tree.getOutput() returns ItemStack now
        ItemStack targetItem = tree.getOutput().copy();
        
        PendingTask.TaskType taskType = (wsType == PendingTask.WorkstationType.CRAFTING_TABLE) 
                ? PendingTask.TaskType.CRAFT 
                : PendingTask.TaskType.PROCESS;
        
        PendingTask currentTask = new PendingTask(maid, taskType, targetItem);
        currentTask.setWorkstationType(wsType);
        currentTask.setRecipeId(tree.getRecipeId());
        
        // Set generic target (type only, null pos)
        ResourceLocation wsBlockId = ResourceLocation.parse(wsStr); // Assume workstation string is valid block ID
        currentTask.setTarget(new WorkBlockTarget(wsBlockId, null));
        
        // 3. Handle Ingredients & Dependencies
        List<SlotMapping> slotMappings = new ArrayList<>();
        
        for (CraftingTreeEvaluator.IngredientResult ingredient : tree.getIngredients()) {
            if (ingredient.hasSubTree()) {
                // Dependency: Sub-task must complete first
                PendingTask dependency = generateTaskGraph(ingredient.getSubTree(), maid, level, accumulator);
                dependency.addDependent(currentTask);
            }
            
            // For processing tasks (furnace), we need slot mappings
            if (taskType == PendingTask.TaskType.PROCESS) {
                int slot = ingredient.getTargetSlot();
                if (slot >= 0) {
                    // ingredient.getItemStack() returns the stack with correct count
                    ItemStack inputStack = ingredient.getItemStack().copy(); 
                    slotMappings.add(new SlotMapping(null, slot, inputStack));
                }
            }
        }
        
        if (!slotMappings.isEmpty()) {
            currentTask.setSlotMappings(slotMappings);
        }
        
        accumulator.add(currentTask);
        return currentTask;
    }

    private void createSingleTask(EntityMaid maid, ServerLevel level, CraftingRecipe recipe, int count, List<PendingTask> accumulator) {
        String wsStr = "minecraft:crafting_table";
        PendingTask.WorkstationType wsType = PendingTask.WorkstationType.CRAFTING_TABLE;
        
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack targetItem = result.copy();
        targetItem.setCount(result.getCount() * count);
        
        PendingTask task = new PendingTask(maid, PendingTask.TaskType.CRAFT, targetItem);
        task.setWorkstationType(wsType);
        task.setRecipeId(recipe.getId().toString());
        task.setTarget(new WorkBlockTarget(ResourceLocation.parse(wsStr), null));
        
        accumulator.add(task);
    }
    
    private List<IngredientNeed> analyzeIngredients(List<Ingredient> ingredients, int multiplier) {
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                String id = ItemIdUtils.getId(items[0]);
                needs.merge(id, multiplier, Integer::sum);
            }
        }
        return needs.entrySet().stream()
                .map(e -> new IngredientNeed(e.getKey(), e.getValue()))
                .toList();
    }
    
    /**
     * Append ingredients in JSON format with availability status.
     */
    private void appendIngredientsJson(StringBuilder sb, List<IngredientNeed> needs,
                                       Map<String, Integer> maidInv, Map<String, Integer> storageInv,
                                       ServerLevel level, boolean recursive, int depth) {
        int maxDepth = CraftConfig.MAX_CRAFT_TREE_DEPTH.get();
        
        sb.append("[");
        boolean first = true;
        for (IngredientNeed need : needs) {
            if (!first) sb.append(", ");
            first = false;
            
            int inMaid = maidInv.getOrDefault(need.itemId, 0);
            int inStorage = storageInv.getOrDefault(need.itemId, 0);
            int total = inMaid + inStorage;
            int missing = Math.max(0, need.count - total);
            
            sb.append(String.format("{\"item\": \"%s\", \"need\": %d, \"have\": %d, \"missing\": %d",
                    need.itemId, need.count, total, missing));
            
            // Recursive search for missing items
            if (recursive && missing > 0 && depth < maxDepth) {
                ResourceLocation rl = ItemIdUtils.parse(need.itemId);
                if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                    ItemStack item = ItemIdUtils.createStack(need.itemId);
                    Optional<CraftingRecipe> subRecipe = RecipeLookup.findByOutput(level, item).stream().findFirst();
                    Optional<SmeltingRecipe> subSmelt = RecipeLookup.findSmeltingByOutput(level, item);
                    
                    if (subRecipe.isPresent()) {
                        String subType = subRecipe.get().getClass().getSimpleName().contains("Shapeless") 
                                ? "shapeless" : "shaped";
                        sb.append(String.format(", \"craft_from\": {\"type\": \"%s\", \"ingredients\": ", subType));
                        List<IngredientNeed> subNeeds = analyzeIngredients(subRecipe.get().getIngredients(), missing);
                        appendIngredientsJson(sb, subNeeds, maidInv, storageInv, level, true, depth + 1);
                        sb.append("}");
                    } else if (subSmelt.isPresent()) {
                        sb.append(", \"smelt_from\": {\"type\": \"smelting\", \"ingredients\": ");
                        List<IngredientNeed> subNeeds = analyzeIngredients(subSmelt.get().getIngredients(), missing);
                        appendIngredientsJson(sb, subNeeds, maidInv, storageInv, level, true, depth + 1);
                        sb.append(", \"requires_fuel\": true}");
                    }
                }
            }
            
            sb.append("}");
        }
        sb.append("]");
    }
    
    private record IngredientNeed(String itemId, int count) {}

    public record Params(String itemId, int count, boolean recursiveSearch, boolean dryRun, String recipeId) {}
}

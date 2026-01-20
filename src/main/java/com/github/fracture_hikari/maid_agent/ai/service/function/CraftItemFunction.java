package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.config.CraftConfig;
import com.github.fracture_hikari.maid_agent.recipe.CraftingTreeEvaluator;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.StorageTarget;
import com.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.items.IItemHandler;
import studio.fantasyit.maid_storage_manager.util.RecipeUtil;

import java.util.*;

/**
 * LLM function to craft items.
 * Searches recipes, checks ingredients, and queues crafting work.
 * With dry_run=true, only analyzes without executing.
 */
public class CraftItemFunction implements IFunctionCall<CraftItemFunction.Params> {
    private static final String FUNCTION_ID = "craft_item";
    private static final String FUNCTION_DESC = """
            Craft an item. Searches recipe, checks inventory for ingredients,
            and queues the maid to walk to workstation and perform crafting.
            
            By default executes the craft. Set dry_run=true to only analyze.
            Use recursive_search=true to find crafting tree for missing ingredients.""";
    
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
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        // Crafting requires maid_storage_manager
        if (!Integrations.maidStorageManager()) {
            return new ToolResponse(Integrations.getMsmRequiredMessage());
        }
        
        if (!(maid.level() instanceof ServerLevel level)) {
            return new ToolResponse("Error: Not on server");
        }
        
        // Parse item ID
        ResourceLocation itemId = ResourceLocation.tryParse(params.itemId());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return new ToolResponse("Unknown item: " + params.itemId() + ". Use jei_item_search to find valid item IDs.");
        }
        
        ItemStack targetItem = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        int count = Math.min(params.count(), CraftConfig.MAX_CRAFT_OUTPUT.get());
        
        // Get maid's inventory and storage
        Map<String, Integer> maidInventory = getMaidInventory(maid);
        Map<String, Integer> storageInventory = getStorageInventory(maid);
        
        StringBuilder sb = new StringBuilder();
        
        // If recipe_id is specified, use that specific recipe
        if (!params.recipeId().isEmpty()) {
            Optional<CraftingRecipe> specificRecipe = findRecipeById(level, params.recipeId());
            if (specificRecipe.isEmpty()) {
                return new ToolResponse("Recipe not found: " + params.recipeId());
            }
            
            CraftingRecipe recipe = specificRecipe.get();
            appendRecipeJson(sb, recipe, count, maidInventory, storageInventory, level, params.recursiveSearch());
            
            // Queue task if not dry run
            if (!params.dryRun()) {
                java.util.LinkedHashMap<String, Integer> singleStep = new java.util.LinkedHashMap<>();
                singleStep.put(recipe.getId().toString(), count);
                String result = queueCraftingTask(maid, params.itemId(), count, 
                        recipe.getId().toString(), singleStep,
                        PendingTask.WorkstationType.CRAFTING_TABLE, level);
                if (result != null) {
                    sb.append("\n\n").append(result);
                }
            } else {
                sb.append("\n\n[dry_run=true: Analysis only, no task queued]");
            }
            
            return new ToolResponse(sb.toString());
        }
        
        // No recipe_id specified - list available recipes
        // If recursive_search, use smart tree evaluation
        if (params.recursiveSearch()) {
            CraftingTreeEvaluator evaluator = new CraftingTreeEvaluator(level, maidInventory, storageInventory);
            List<CraftingTreeEvaluator.EvaluatedTree> trees = evaluator.evaluateRecipes(targetItem, count);
            
            if (trees.isEmpty()) {
                return new ToolResponse(String.format(
                        "No recipe found for %s. Use jei_item_search to find valid item IDs.",
                        params.itemId()));
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
                    java.util.LinkedHashMap<String, Integer> steps = best.toFlattenedSteps();
                    String result = queueCraftingTask(maid, params.itemId(), count, 
                            best.getRecipeId(), steps,
                            best.getWorkstation().contains("furnace") 
                                    ? PendingTask.WorkstationType.FURNACE 
                                    : PendingTask.WorkstationType.CRAFTING_TABLE, 
                            level);
                    if (result != null) {
                        sb.append("\n\n").append(result);
                    }
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
        }
        
        // Simple listing without tree evaluation
        List<CraftingRecipe> craftingRecipes = findCraftingRecipes(level, targetItem);
        Optional<SmeltingRecipe> smeltingRecipe = RecipeUtil.getSmeltingRecipe(level, targetItem);
        
        if (craftingRecipes.isEmpty() && smeltingRecipe.isEmpty()) {
            return new ToolResponse(String.format(
                    "No recipe found for %s. Use jei_item_search to find valid item IDs.",
                    params.itemId()));
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
     * Queue a crafting task and find nearby workstation.
     */
    private String queueCraftingTask(EntityMaid maid, String itemId, int count, 
                                      String recipeId, java.util.LinkedHashMap<String, Integer> craftingSteps,
                                      PendingTask.WorkstationType workstationType,
                                      ServerLevel level) {
        // Find nearby crafting table
        BlockPos maidPos = maid.blockPosition();
        int searchRadius = maid.hasRestriction() ? (int) maid.getRestrictRadius() : 16;
        
        BlockPos workstationPos = null;
        double minDist = Double.MAX_VALUE;
        
        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-searchRadius, -4, -searchRadius),
                maidPos.offset(searchRadius, 4, searchRadius))) {
            if (isWorkstation(level, pos, workstationType)) {
                double dist = maidPos.distSqr(pos);
                if (dist < minDist) {
                    minDist = dist;
                    workstationPos = pos.immutable();
                }
            }
        }
        
        if (workstationPos == null) {
            return "Error: No crafting table found nearby. Cannot queue crafting task.";
        }
        
        // Create task and queue it
        PendingTask task = new PendingTask(maid, PendingTask.TaskType.CRAFT, itemId, count);
        task.setWorkstationType(workstationType);
        task.setRecipeId(recipeId);
        task.setCraftingSteps(craftingSteps);  // Store the pre-computed steps
        ResourceLocation blockType = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(workstationPos).getBlock());
        task.setTarget(new StorageTarget(blockType, workstationPos, (net.minecraft.core.Direction) null));
        
        // Get or create task queue
        TaskQueue queue = maid.getBrain()
                .getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .orElseGet(() -> {
                    TaskQueue newQueue = new TaskQueue(maid);
                    maid.getBrain().setMemory(MemoryModuleRegistry.TASK_QUEUE.get(), newQueue);
                    return newQueue;
                });
        
        queue.enqueue(task);
        
        // Start the first task if this is the only one
        if (queue.size() == 1) {
            task.setStatus(PendingTask.TaskStatus.MOVING);
            maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), 
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(workstationPos));
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(
                    maid, workstationPos, 0.6f, 1);
        }
        
        return String.format("Queued crafting task: %dx %s at crafting table (%d,%d,%d)",
                count, itemId, workstationPos.getX(), workstationPos.getY(), workstationPos.getZ());
    }
    
    private boolean isWorkstation(ServerLevel level, BlockPos pos, PendingTask.WorkstationType type) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return switch (type) {
            case CRAFTING_TABLE -> state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
            case FURNACE -> state.is(net.minecraft.world.level.block.Blocks.FURNACE);
            case SMOKER -> state.is(net.minecraft.world.level.block.Blocks.SMOKER);
            case BLAST_FURNACE -> state.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE);
        };
    }
    
    private List<CraftingRecipe> findCraftingRecipes(ServerLevel level, ItemStack target) {
        int maxRecipes = com.github.fracture_hikari.maid_agent.config.JeiConfig.RECIPE_SEARCH_MAX_RESULTS.get();
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), target))
                .limit(maxRecipes)
                .toList();
    }
    
    private Optional<CraftingRecipe> findRecipeById(ServerLevel level, String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return Optional.empty();
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }
    
    private Map<String, Integer> getMaidInventory(EntityMaid maid) {
        Map<String, Integer> inventory = new HashMap<>();
        IItemHandler handler = maid.getAvailableInv(true);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String id = stack.getItem().builtInRegistryHolder().key().location().toString();
                inventory.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return inventory;
    }
    
    private Map<String, Integer> getStorageInventory(EntityMaid maid) {
        Map<String, Integer> inventory = new HashMap<>();
        Optional<ViewedStorageMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get());
        if (memoryOpt.isPresent()) {
            ViewedStorageMemory memory = memoryOpt.get();
            for (int i = 0; i < memory.getStorageCount(); i++) {
                memory.getStorageByIndex(i).ifPresent(target -> {
                    for (ViewedStorageMemory.ItemCount ic : memory.getContents(target)) {
                        String id = ic.item().getItem().builtInRegistryHolder().key().location().toString();
                        inventory.merge(id, ic.count(), Integer::sum);
                    }
                });
            }
        }
        return inventory;
    }
    
    private int getStorageStaleness(EntityMaid maid, ServerLevel level) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .map(m -> m.getStalenessSeconds(level.getGameTime()))
                .orElse(999);
    }
    
    private List<IngredientNeed> analyzeIngredients(List<Ingredient> ingredients, int multiplier) {
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                String id = items[0].getItem().builtInRegistryHolder().key().location().toString();
                needs.merge(id, multiplier, Integer::sum);
            }
        }
        return needs.entrySet().stream()
                .map(e -> new IngredientNeed(e.getKey(), e.getValue()))
                .toList();
    }
    
    private void appendIngredientAnalysis(StringBuilder sb, List<IngredientNeed> needs,
                                          Map<String, Integer> maidInv, Map<String, Integer> storageInv,
                                          ServerLevel level, boolean recursive, int depth) {
        int maxDepth = CraftConfig.MAX_CRAFT_TREE_DEPTH.get();
        String indent = "  ".repeat(depth);
        
        for (IngredientNeed need : needs) {
            int inMaid = maidInv.getOrDefault(need.itemId, 0);
            int inStorage = storageInv.getOrDefault(need.itemId, 0);
            int total = inMaid + inStorage;
            int missing = Math.max(0, need.count - total);
            
            String status;
            if (missing == 0) {
                status = "✓";
            } else if (total > 0) {
                status = String.format("PARTIAL (have %d, need %d more)", total, missing);
            } else {
                status = String.format("MISSING %d", need.count);
            }
            
            sb.append(String.format("%s- %dx %s: %s\\n", indent, need.count, need.itemId, status));
            
            // Recursive search for missing items
            if (recursive && missing > 0 && depth < maxDepth) {
                ResourceLocation rl = ResourceLocation.tryParse(need.itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                    Optional<CraftingRecipe> subRecipe = findCraftingRecipes(level, item).stream().findFirst();
                    Optional<SmeltingRecipe> subSmelt = RecipeUtil.getSmeltingRecipe(level, item);
                    
                    if (subRecipe.isPresent()) {
                        sb.append(String.format("%s  → Can craft from:\\n", indent));
                        List<IngredientNeed> subNeeds = analyzeIngredients(subRecipe.get().getIngredients(), missing);
                        appendIngredientAnalysis(sb, subNeeds, maidInv, storageInv, level, true, depth + 1);
                    } else if (subSmelt.isPresent()) {
                        sb.append(String.format("%s  → Can smelt from:\\n", indent));
                        List<IngredientNeed> subNeeds = analyzeIngredients(subSmelt.get().getIngredients(), missing);
                        appendIngredientAnalysis(sb, subNeeds, maidInv, storageInv, level, true, depth + 1);
                        sb.append(String.format("%s    + fuel\\n", indent));
                    }
                }
            }
        }
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
                ResourceLocation rl = ResourceLocation.tryParse(need.itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                    Optional<CraftingRecipe> subRecipe = findCraftingRecipes(level, item).stream().findFirst();
                    Optional<SmeltingRecipe> subSmelt = RecipeUtil.getSmeltingRecipe(level, item);
                    
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

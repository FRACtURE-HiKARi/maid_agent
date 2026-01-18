package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.util.JeiRuntimeHolder;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.config.JeiConfig;
import com.github.fracture_hikari.maid_agent.recipe.IRecipeFormatter;
import com.github.fracture_hikari.maid_agent.recipe.RecipeFormatterRegistry;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LLM function to search for recipes using JEI.
 * Supports searching by ingredient (input), product (output), or both.
 * 
 * Uses an extensible formatter system - other mods can register their own
 * recipe formatters via {@link RecipeFormatterRegistry}.
 */
public class RecipeSearchFunction implements IFunctionCall<RecipeSearchFunction.Result> {
    private static final String FUNCTION_ID = "jei_recipe_search";
    private static final String FUNCTION_DESC = """
            Search for recipes by ingredient (input item) and/or product (output item).
            Returns detailed recipe information including:
            - Workstation: the block needed to craft (in namespace:name format)
            - Ingredients: list with slot positions [x,y] for shaped recipes
            - Output: the result item(s)
            
            Recipes require specific workstations (blocks) to interact with.
            Use the workstation block ID with the locate_block function to find one.""";
    
    private static final String INGREDIENT_PARAM_ID = "ingredient_item";
    private static final String INGREDIENT_PARAM_DESC = """
            ingredient_item (string, optional): The item used as recipe input, in format namespace:name 
            (e.g., minecraft:iron_ingot). Leave empty to not filter by input.""";
    
    private static final String PRODUCT_PARAM_ID = "product_item";
    private static final String PRODUCT_PARAM_DESC = """
            product_item (string, optional): The item produced by the recipe, in format namespace:name 
            (e.g., minecraft:iron_block). Leave empty to not filter by output.""";

    private static final String JEI_UNAVAILABLE = "JEI is not available. Cannot search for recipes.";
    private static final String NO_PARAMS = "You must provide at least one of ingredient_item or product_item.";
    private static final String ITEM_NOT_FOUND = "Item '%s' not found in registry.";
    private static final String NO_RESULTS = "No recipes found matching the criteria.";

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
        StringParameter ingredientParam = StringParameter.create();
        ingredientParam.setDescription(INGREDIENT_PARAM_DESC);
        root.addProperties(INGREDIENT_PARAM_ID, ingredientParam, false);

        StringParameter productParam = StringParameter.create();
        productParam.setDescription(PRODUCT_PARAM_DESC);
        root.addProperties(PRODUCT_PARAM_ID, productParam, false);

        return root;
    }

    @Override
    public Codec<Result> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf(INGREDIENT_PARAM_ID, "").forGetter(Result::ingredientItem),
                        Codec.STRING.optionalFieldOf(PRODUCT_PARAM_ID, "").forGetter(Result::productItem)
                ).apply(instance, Result::new));
    }

    @Override
    public ToolResponse onToolCall(Result result, EntityMaid maid) {
        if (!JeiRuntimeHolder.isAvailable()) {
            return new ToolResponse(JEI_UNAVAILABLE);
        }

        IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElse(null);
        if (runtime == null) {
            return new ToolResponse(JEI_UNAVAILABLE);
        }

        boolean hasIngredient = !result.ingredientItem.isEmpty();
        boolean hasProduct = !result.productItem.isEmpty();

        if (!hasIngredient && !hasProduct) {
            return new ToolResponse(NO_PARAMS);
        }

        // Resolve items
        ItemStack ingredientStack = null;
        ItemStack productStack = null;

        if (hasIngredient) {
            ingredientStack = resolveItem(result.ingredientItem);
            if (ingredientStack == null) {
                return new ToolResponse(ITEM_NOT_FOUND.formatted(result.ingredientItem));
            }
        }

        if (hasProduct) {
            productStack = resolveItem(result.productItem);
            if (productStack == null) {
                return new ToolResponse(ITEM_NOT_FOUND.formatted(result.productItem));
            }
        }

        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        int maxResults = JeiConfig.RECIPE_SEARCH_MAX_RESULTS.get();

        List<IRecipeFormatter.RecipeData> recipes;
        
        if (hasIngredient && hasProduct) {
            recipes = findRecipesWithBoth(recipeManager, focusFactory, ingredientStack, productStack, maxResults);
        } else if (hasIngredient) {
            recipes = findRecipesByRole(recipeManager, focusFactory, ingredientStack, RecipeIngredientRole.INPUT, maxResults);
        } else {
            recipes = findRecipesByRole(recipeManager, focusFactory, productStack, RecipeIngredientRole.OUTPUT, maxResults);
        }

        if (recipes.isEmpty()) {
            return new ToolResponse(NO_RESULTS);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d recipe(s):\n\n", recipes.size()));
        for (int i = 0; i < recipes.size(); i++) {
            sb.append(recipes.get(i).format());
            if (i < recipes.size() - 1) sb.append("\n---\n");
        }

        return new ToolResponse(sb.toString().trim());
    }

    private ItemStack resolveItem(String itemId) {
        try {
            String[] split = itemId.split(":");
            if (split.length != 2) return null;
            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(split[0], split[1]);
            if (!BuiltInRegistries.ITEM.containsKey(resourceLocation)) {
                return null;
            }
            Item item = BuiltInRegistries.ITEM.get(resourceLocation);
            return new ItemStack(item);
        } catch (Exception e) {
            return null;
        }
    }

    private List<IRecipeFormatter.RecipeData> findRecipesByRole(IRecipeManager recipeManager, IFocusFactory focusFactory,
                                                                 ItemStack stack, RecipeIngredientRole role, int maxResults) {
        IFocus<ItemStack> focus = focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, stack);
        List<IFocus<?>> focuses = List.of(focus);

        List<IRecipeFormatter.RecipeData> results = new ArrayList<>();
        
        collectRecipes(recipeManager, RecipeTypes.CRAFTING, focuses, results, maxResults);
        if (results.size() >= maxResults) return results.subList(0, maxResults);
        
        collectRecipes(recipeManager, RecipeTypes.SMELTING, focuses, results, maxResults);
        if (results.size() >= maxResults) return results.subList(0, maxResults);

        collectRecipes(recipeManager, RecipeTypes.SMITHING, focuses, results, maxResults);
        if (results.size() >= maxResults) return results.subList(0, maxResults);

        collectRecipes(recipeManager, RecipeTypes.STONECUTTING, focuses, results, maxResults);
        if (results.size() >= maxResults) return results.subList(0, maxResults);
        
        collectRecipes(recipeManager, RecipeTypes.BLASTING, focuses, results, maxResults);
        if (results.size() >= maxResults) return results.subList(0, maxResults);
        
        collectRecipes(recipeManager, RecipeTypes.SMOKING, focuses, results, maxResults);

        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    private <T> void collectRecipes(IRecipeManager recipeManager, RecipeType<T> recipeType,
                                    List<IFocus<?>> focuses, List<IRecipeFormatter.RecipeData> results, int maxResults) {
        if (results.size() >= maxResults) return;
        
        try {
            Stream<T> recipeStream = recipeManager.createRecipeLookup(recipeType)
                    .limitFocus(focuses)
                    .get();

            recipeStream
                    .limit(maxResults - results.size())
                    .forEach(recipe -> {
                        IRecipeFormatter.RecipeData data = RecipeFormatterRegistry.extractRecipeData(recipe);
                        if (data != null) {
                            results.add(data);
                        }
                    });
        } catch (Exception ignored) {
            // Some recipe types may not be available
        }
    }

    private List<IRecipeFormatter.RecipeData> findRecipesWithBoth(IRecipeManager recipeManager, IFocusFactory focusFactory,
                                                                   ItemStack ingredientStack, ItemStack productStack, int maxResults) {
        Map<String, IRecipeFormatter.RecipeData> ingredientRecipes = new HashMap<>();
        List<IRecipeFormatter.RecipeData> ingredientList = findRecipesByRole(recipeManager, focusFactory, 
                ingredientStack, RecipeIngredientRole.INPUT, 200);
        for (IRecipeFormatter.RecipeData recipe : ingredientList) {
            ingredientRecipes.put(recipe.recipeId(), recipe);
        }

        List<IRecipeFormatter.RecipeData> productRecipes = findRecipesByRole(recipeManager, focusFactory,
                productStack, RecipeIngredientRole.OUTPUT, 200);
        
        return productRecipes.stream()
                .filter(recipe -> ingredientRecipes.containsKey(recipe.recipeId()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    public record Result(String ingredientItem, String productItem) {
    }
}

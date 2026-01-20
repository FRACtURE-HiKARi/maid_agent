package com.github.fracture_hikari.maid_agent.util;

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
 * Utility class for recipe lookups.
 * Provides methods to find and analyze recipes.
 */
public final class RecipeLookup {
    
    private RecipeLookup() {} // Prevent instantiation
    
    /**
     * Find all crafting recipes that produce the target item.
     * @param level Server level for recipe manager access
     * @param target The target item to craft
     * @param limit Maximum number of recipes to return
     * @return List of matching recipes
     */
    public static List<CraftingRecipe> findByOutput(ServerLevel level, ItemStack target, int limit) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), target))
                .limit(limit)
                .toList();
    }
    
    /**
     * Find all crafting recipes for target (unlimited).
     */
    public static List<CraftingRecipe> findByOutput(ServerLevel level, ItemStack target) {
        return findByOutput(level, target, Integer.MAX_VALUE);
    }
    
    /**
     * Find crafting recipe by ID.
     * @param level Server level
     * @param recipeId Recipe ID string (e.g., "minecraft:stick")
     * @return Optional containing the recipe if found
     */
    public static Optional<CraftingRecipe> findById(ServerLevel level, String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return Optional.empty();
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }
    
    /**
     * Find crafting recipe by ResourceLocation ID.
     */
    public static Optional<CraftingRecipe> findById(ServerLevel level, ResourceLocation id) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }
    
    /**
     * Find smelting recipe for item.
     * Delegates to MSM's RecipeUtil.
     */
    public static Optional<SmeltingRecipe> findSmeltingByOutput(ServerLevel level, ItemStack target) {
        return RecipeUtil.getSmeltingRecipe(level, target);
    }
    
    /**
     * Analyze recipe ingredients, merging duplicates.
     * @param ingredients List of ingredients from recipe
     * @param multiplier Multiply counts (for crafting multiple)
     * @return List of ingredient needs with merged counts
     */
    public static List<IngredientNeed> analyzeIngredients(List<Ingredient> ingredients, int multiplier) {
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
     * Check if recipe is shapeless.
     */
    public static boolean isShapeless(CraftingRecipe recipe) {
        return recipe.getClass().getSimpleName().contains("Shapeless");
    }
    
    /**
     * Get recipe type string ("shaped" or "shapeless").
     */
    public static String getTypeString(CraftingRecipe recipe) {
        return isShapeless(recipe) ? "shapeless" : "shaped";
    }
    
    /**
     * Represents an ingredient requirement.
     */
    public record IngredientNeed(String itemId, int count) {}
}

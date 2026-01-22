package com.github.fracture_hikari.maid_agent.util;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import studio.fantasyit.maid_storage_manager.util.RecipeUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified recipe lookup utility with JEI-first approach and vanilla fallback.
 * All recipe lookups across the project should go through this class.
 * 
 * JEI provides better modded recipe support and caching.
 */
public final class RecipeLookup {
    
    private RecipeLookup() {} // Prevent instantiation
    
    // ========== CRAFTING RECIPES ==========
    
    /**
     * Find all crafting recipes that produce the target item.
     * Uses JEI when available for better modded support.
     */
    public static List<CraftingRecipe> findByOutput(ServerLevel level, ItemStack target, int limit) {
        // Try JEI first
        if (JeiRuntimeHolder.isAvailable()) {
            try {
                List<CraftingRecipe> result = findByOutputWithJei(target, limit);
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                // Fall through to vanilla
            }
        }
        // Vanilla fallback
        return findByOutputVanilla(level, target, limit);
    }
    
    /**
     * Find all crafting recipes for target (unlimited).
     */
    public static List<CraftingRecipe> findByOutput(ServerLevel level, ItemStack target) {
        return findByOutput(level, target, Integer.MAX_VALUE);
    }
    
    /**
     * Find crafting recipes that use the given item as INPUT.
     * Only available with JEI - returns empty list without JEI.
     */
    public static List<CraftingRecipe> findByInput(ItemStack ingredient, int limit) {
        if (!JeiRuntimeHolder.isAvailable()) {
            return Collections.emptyList();
        }
        try {
            return findByInputWithJei(ingredient, limit);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    
    /**
     * Find crafting recipe by ID.
     */
    public static Optional<CraftingRecipe> findById(ServerLevel level, String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return Optional.empty();
        return findById(level, id);
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
     * Find smelting recipe by ID.
     */
    public static Optional<SmeltingRecipe> findSmeltingById(ServerLevel level, String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return Optional.empty();
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.SMELTING)
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }
    
    // ========== SMELTING RECIPES ==========
    
    /**
     * Find first smelting recipe for item.
     * Uses JEI when available, falls back to MSM's RecipeUtil.
     */
    public static Optional<SmeltingRecipe> findSmeltingByOutput(ServerLevel level, ItemStack target) {
        List<SmeltingRecipe> all = findAllSmeltingByOutput(level, target, 1);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }
    
    /**
     * Find ALL smelting recipes that produce the target item.
     * E.g., iron_ingot can come from iron_ore OR raw_iron.
     * Uses JEI when available for better modded support.
     */
    public static List<SmeltingRecipe> findAllSmeltingByOutput(ServerLevel level, ItemStack target, int limit) {
        // Try JEI first
        if (JeiRuntimeHolder.isAvailable()) {
            try {
                List<SmeltingRecipe> result = findAllSmeltingByOutputWithJei(target, limit);
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                // Fall through to vanilla
            }
        }
        // Vanilla fallback
        return findAllSmeltingByOutputVanilla(level, target, limit);
    }
    
    private static List<SmeltingRecipe> findAllSmeltingByOutputWithJei(ItemStack target, int limit) {
        IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElseThrow();
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        
        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, target);
        
        return recipeManager.createRecipeLookup(RecipeTypes.SMELTING)
                .limitFocus(List.of(focus))
                .get()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    private static List<SmeltingRecipe> findAllSmeltingByOutputVanilla(ServerLevel level, ItemStack target, int limit) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.SMELTING)
                .stream()
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), target))
                .limit(limit)
                .toList();
    }
    
    private static List<CraftingRecipe> findByOutputWithJei(ItemStack target, int limit) {
        IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElseThrow();
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        
        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, target);
        
        return recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
                .limitFocus(List.of(focus))
                .get()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    private static List<CraftingRecipe> findByInputWithJei(ItemStack ingredient, int limit) {
        IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElseThrow();
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        
        IFocus<ItemStack> focus = focusFactory.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, ingredient);
        
        return recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
                .limitFocus(List.of(focus))
                .get()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static List<CraftingRecipe> findByOutputVanilla(ServerLevel level, ItemStack target, int limit) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), target))
                .limit(limit)
                .toList();
    }

    /**
     * Check if recipe is shapeless.
     */
    public static boolean isShapeless(CraftingRecipe recipe) {
        return recipe.getClass().getSimpleName().contains("Shapeless");
    }
}

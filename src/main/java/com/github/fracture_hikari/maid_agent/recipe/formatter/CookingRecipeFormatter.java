package com.github.fracture_hikari.maid_agent.recipe.formatter;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.recipe.IRecipeFormatter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for furnace/cooking recipes (smelting, blasting, smoking, campfire).
 */
public class CookingRecipeFormatter implements IRecipeFormatter {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "cooking");
    
    @Override
    public @NotNull ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public @NotNull ResourceLocation getWorkstationBlock() {
        // Default to furnace, but the actual workstation is determined per-recipe
        return ResourceLocation.fromNamespaceAndPath("minecraft", "furnace");
    }
    
    @Override
    public @NotNull String getWorkstationDescription() {
        return "Smelting with fuel";
    }
    
    @Override
    public boolean canHandle(@NotNull Object recipe) {
        return recipe instanceof AbstractCookingRecipe;
    }
    
    @Override
    public @Nullable RecipeData extract(@NotNull Object recipe) {
        if (!(recipe instanceof AbstractCookingRecipe cookingRecipe)) {
            return null;
        }
        
        String recipeId = getRecipeId(cookingRecipe);
        ResourceLocation workstation = getActualWorkstation(cookingRecipe);
        String description = getActualDescription(cookingRecipe);
        
        List<SlotInfo> ingredients = extractIngredients(cookingRecipe.getIngredients());
        List<SlotInfo> outputs = extractOutputs(cookingRecipe);
        
        int cookTime = cookingRecipe.getCookingTime();
        float experience = cookingRecipe.getExperience();
        String additionalInfo = String.format("Cook time: %d ticks (%.1f seconds), XP: %.1f", 
                cookTime, cookTime / 20.0, experience);
        
        return new RecipeData(
                recipeId,
                workstation,
                description,
                ingredients,
                outputs,
                additionalInfo
        );
    }
    
    private ResourceLocation getActualWorkstation(AbstractCookingRecipe recipe) {
        if (recipe instanceof SmeltingRecipe) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "furnace");
        } else if (recipe instanceof BlastingRecipe) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "blast_furnace");
        } else if (recipe instanceof SmokingRecipe) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "smoker");
        } else if (recipe instanceof CampfireCookingRecipe) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "campfire");
        }
        return ResourceLocation.fromNamespaceAndPath("minecraft", "furnace");
    }
    
    private String getActualDescription(AbstractCookingRecipe recipe) {
        if (recipe instanceof SmeltingRecipe) {
            return "Furnace smelting, requires fuel";
        } else if (recipe instanceof BlastingRecipe) {
            return "Blast furnace, requires fuel, 2x speed for ores";
        } else if (recipe instanceof SmokingRecipe) {
            return "Smoker, requires fuel, for food items";
        } else if (recipe instanceof CampfireCookingRecipe) {
            return "Campfire cooking, no fuel required";
        }
        return "Smelting, requires fuel";
    }
    
    private List<SlotInfo> extractIngredients(NonNullList<Ingredient> ingredients) {
        List<SlotInfo> slots = new ArrayList<>();
        
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                ItemStack[] items = ingredient.getItems();
                if (items.length > 0) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(items[0].getItem());
                    String alternatives = items.length > 1 ? "or " + (items.length - 1) + " alternatives" : null;
                    slots.add(SlotInfo.of(id.toString(), 1, alternatives));
                }
            }
        }
        
        return slots;
    }
    
    private List<SlotInfo> extractOutputs(AbstractCookingRecipe recipe) {
        List<SlotInfo> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(null);
        if (!result.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            outputs.add(SlotInfo.of(id.toString(), result.getCount(), null));
        }
        return outputs;
    }
    
    private String getRecipeId(AbstractCookingRecipe recipe) {
        try {
            var method = recipe.getClass().getMethod("getId");
            Object id = method.invoke(recipe);
            if (id instanceof ResourceLocation rl) {
                return rl.toString();
            }
            return id.toString();
        } catch (Exception e) {
            return recipe.getClass().getSimpleName() + "@" + Integer.toHexString(recipe.hashCode());
        }
    }
}

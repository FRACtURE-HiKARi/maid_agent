package com.github.fracture_hikari.maid_agent.recipe.formatter;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.recipe.IRecipeFormatter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for smithing table recipes.
 */
public class SmithingRecipeFormatter implements IRecipeFormatter {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "smithing");
    public static final ResourceLocation WORKSTATION = ResourceLocation.fromNamespaceAndPath("minecraft", "smithing_table");
    
    @Override
    public @NotNull ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public @NotNull ResourceLocation getWorkstationBlock() {
        return WORKSTATION;
    }
    
    @Override
    public @NotNull String getWorkstationDescription() {
        return "Smithing table for upgrading/transforming items";
    }
    
    @Override
    public boolean canHandle(@NotNull Object recipe) {
        return recipe instanceof SmithingRecipe;
    }
    
    @Override
    public @Nullable RecipeData extract(@NotNull Object recipe) {
        if (!(recipe instanceof SmithingRecipe smithingRecipe)) {
            return null;
        }
        
        String recipeId = getRecipeId(smithingRecipe);
        List<SlotInfo> ingredients = extractIngredients(smithingRecipe.getIngredients());
        List<SlotInfo> outputs = extractOutputs(smithingRecipe);
        
        return new RecipeData(
                recipeId,
                WORKSTATION,
                getWorkstationDescription(),
                ingredients,
                outputs,
                "Slots: [0]=template, [1]=base item, [2]=addition material"
        );
    }
    
    private List<SlotInfo> extractIngredients(NonNullList<Ingredient> ingredients) {
        List<SlotInfo> slots = new ArrayList<>();
        String[] slotNames = {"template", "base", "addition"};
        
        for (int i = 0; i < ingredients.size() && i < 3; i++) {
            Ingredient ingredient = ingredients.get(i);
            if (!ingredient.isEmpty()) {
                ItemStack[] items = ingredient.getItems();
                if (items.length > 0) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(items[0].getItem());
                    String alternatives = items.length > 1 ? "or " + (items.length - 1) + " alternatives" : null;
                    // Use x position to indicate slot index, y=-1 for non-grid
                    slots.add(SlotInfo.of(id.toString(), 1, i, -1, slotNames[i] + (alternatives != null ? ", " + alternatives : "")));
                }
            }
        }
        
        return slots;
    }
    
    private List<SlotInfo> extractOutputs(SmithingRecipe recipe) {
        List<SlotInfo> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(null);
        if (!result.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            outputs.add(SlotInfo.of(id.toString(), result.getCount(), null));
        }
        return outputs;
    }
    
    private String getRecipeId(SmithingRecipe recipe) {
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

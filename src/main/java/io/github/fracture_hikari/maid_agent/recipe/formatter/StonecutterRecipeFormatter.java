package io.github.fracture_hikari.maid_agent.recipe.formatter;

import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.recipe.IRecipeFormatter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for stonecutter recipes.
 */
public class StonecutterRecipeFormatter implements IRecipeFormatter {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "stonecutting");
    public static final ResourceLocation WORKSTATION = ResourceLocation.fromNamespaceAndPath("minecraft", "stonecutter");
    
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
        return "Stonecutter for precise stone cutting";
    }
    
    @Override
    public boolean canHandle(@NotNull Object recipe) {
        return recipe instanceof StonecutterRecipe;
    }
    
    @Override
    public @Nullable RecipeData extract(@NotNull Object recipe) {
        if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) {
            return null;
        }
        
        String recipeId = getRecipeId(stonecutterRecipe);
        List<SlotInfo> ingredients = extractIngredients(stonecutterRecipe.getIngredients());
        List<SlotInfo> outputs = extractOutputs(stonecutterRecipe);
        
        return new RecipeData(
                recipeId,
                WORKSTATION,
                getWorkstationDescription(),
                ingredients,
                outputs,
                "1:1 conversion, no fuel required"
        );
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
    
    private List<SlotInfo> extractOutputs(StonecutterRecipe recipe) {
        List<SlotInfo> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(null);
        if (!result.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            outputs.add(SlotInfo.of(id.toString(), result.getCount(), null));
        }
        return outputs;
    }
    
    private String getRecipeId(StonecutterRecipe recipe) {
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

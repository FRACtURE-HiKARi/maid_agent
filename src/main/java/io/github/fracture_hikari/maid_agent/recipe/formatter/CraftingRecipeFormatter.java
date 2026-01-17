package io.github.fracture_hikari.maid_agent.recipe.formatter;

import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.recipe.IRecipeFormatter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for vanilla crafting recipes (shaped and shapeless).
 */
public class CraftingRecipeFormatter implements IRecipeFormatter {
    
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "crafting");
    public static final ResourceLocation WORKSTATION = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_table");
    
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
        return "3x3 crafting grid";
    }
    
    @Override
    public boolean canHandle(@NotNull Object recipe) {
        return recipe instanceof CraftingRecipe;
    }
    
    @Override
    public @Nullable RecipeData extract(@NotNull Object recipe) {
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return null;
        }
        
        String recipeId = getRecipeId(craftingRecipe);
        List<SlotInfo> ingredients;
        String additionalInfo = null;
        
        if (craftingRecipe instanceof ShapedRecipe shapedRecipe) {
            ingredients = extractShapedIngredients(shapedRecipe);
            additionalInfo = String.format("Shaped recipe: %dx%d grid", shapedRecipe.getWidth(), shapedRecipe.getHeight());
        } else if (craftingRecipe instanceof ShapelessRecipe) {
            ingredients = extractShapelessIngredients(craftingRecipe.getIngredients());
            additionalInfo = "Shapeless recipe: any arrangement";
        } else {
            ingredients = extractShapelessIngredients(craftingRecipe.getIngredients());
        }
        
        List<SlotInfo> outputs = extractOutputs(craftingRecipe);
        
        return new RecipeData(
                recipeId,
                WORKSTATION,
                getWorkstationDescription(),
                ingredients,
                outputs,
                additionalInfo
        );
    }
    
    private List<SlotInfo> extractShapedIngredients(ShapedRecipe recipe) {
        List<SlotInfo> slots = new ArrayList<>();
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (index < ingredients.size()) {
                    Ingredient ingredient = ingredients.get(index);
                    if (!ingredient.isEmpty()) {
                        SlotInfo slot = ingredientToSlotInfo(ingredient, x, y);
                        if (slot != null) {
                            slots.add(slot);
                        }
                    }
                }
            }
        }
        
        return slots;
    }
    
    private List<SlotInfo> extractShapelessIngredients(NonNullList<Ingredient> ingredients) {
        List<SlotInfo> slots = new ArrayList<>();
        
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                SlotInfo slot = ingredientToSlotInfo(ingredient, -1, -1);
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        
        return slots;
    }
    
    private List<SlotInfo> extractOutputs(CraftingRecipe recipe) {
        List<SlotInfo> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(null);
        if (!result.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            outputs.add(SlotInfo.of(id.toString(), result.getCount(), null));
        }
        return outputs;
    }
    
    @Nullable
    private SlotInfo ingredientToSlotInfo(Ingredient ingredient, int x, int y) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return null;
        
        ItemStack first = items[0];
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(first.getItem());
        String alternatives = items.length > 1 ? "or " + (items.length - 1) + " alternatives" : null;
        
        return SlotInfo.of(id.toString(), 1, x, y, alternatives);
    }
    
    private String getRecipeId(CraftingRecipe recipe) {
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

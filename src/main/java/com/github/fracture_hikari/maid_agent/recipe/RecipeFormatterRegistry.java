package com.github.fracture_hikari.maid_agent.recipe;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for recipe formatters.
 * Other mods can register their own formatters to support modded recipe types.
 * 
 * <p>Registration should happen during mod initialization, for example in your mod's constructor
 * or during FMLCommonSetupEvent.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * RecipeFormatterRegistry.register(new MyModRecipeFormatter());
 * }</pre>
 */
public class RecipeFormatterRegistry {
    
    private static final List<IRecipeFormatter> formatters = new ArrayList<>();
    private static final Map<ResourceLocation, IRecipeFormatter> formatterMap = new HashMap<>();
    
    /**
     * Register a recipe formatter.
     * 
     * @param formatter The formatter to register
     */
    public static synchronized void register(@NotNull IRecipeFormatter formatter) {
        ResourceLocation id = formatter.getId();
        if (formatterMap.containsKey(id)) {
            MaidAgent.LOGGER.warn("Recipe formatter already registered with ID: {}", id);
            return;
        }
        
        formatters.add(formatter);
        formatterMap.put(id, formatter);
        MaidAgent.LOGGER.debug("Registered recipe formatter: {}", id);
    }
    
    /**
     * Get a formatter by its ID.
     * 
     * @param id The formatter ID
     * @return The formatter, or null if not found
     */
    @Nullable
    public static IRecipeFormatter getFormatter(@NotNull ResourceLocation id) {
        return formatterMap.get(id);
    }
    
    /**
     * Find a formatter that can handle the given recipe.
     * 
     * @param recipe The recipe object from JEI
     * @return The first formatter that can handle this recipe, or null if none found
     */
    @Nullable
    public static IRecipeFormatter findFormatter(@NotNull Object recipe) {
        for (IRecipeFormatter formatter : formatters) {
            if (formatter.canHandle(recipe)) {
                return formatter;
            }
        }
        return null;
    }
    
    /**
     * Get all registered formatters.
     * 
     * @return List of all formatters
     */
    public static List<IRecipeFormatter> getAllFormatters() {
        return new ArrayList<>(formatters);
    }
    
    /**
     * Extract recipe data using the appropriate formatter.
     * 
     * @param recipe The recipe object from JEI
     * @return The extracted recipe data, or null if no formatter could handle it
     */
    @Nullable
    public static IRecipeFormatter.RecipeData extractRecipeData(@NotNull Object recipe) {
        IRecipeFormatter formatter = findFormatter(recipe);
        if (formatter != null) {
            return formatter.extract(recipe);
        }
        return null;
    }
}

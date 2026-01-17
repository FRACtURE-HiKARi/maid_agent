package io.github.fracture_hikari.maid_agent.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for formatting recipe information for LLM consumption.
 * Implement this interface to add support for modded recipe types.
 * 
 * Register implementations via {@link RecipeFormatterRegistry#register(IRecipeFormatter)}.
 */
public interface IRecipeFormatter {
    
    /**
     * Get the unique identifier for this recipe formatter.
     * Should be in namespace:name format.
     * 
     * @return The formatter ID
     */
    @NotNull ResourceLocation getId();
    
    /**
     * Get the workstation block required for this recipe type.
     * Should be in namespace:name format (e.g., "minecraft:crafting_table").
     * 
     * @return The workstation block ResourceLocation
     */
    @NotNull ResourceLocation getWorkstationBlock();
    
    /**
     * Get a human-readable description of the workstation and process.
     * 
     * @return Description like "Crafting Table (3x3 grid)" or "Furnace (smelting, requires fuel)"
     */
    @NotNull String getWorkstationDescription();
    
    /**
     * Check if this formatter can handle the given recipe object.
     * 
     * @param recipe The recipe object from JEI
     * @return true if this formatter can extract info from this recipe
     */
    boolean canHandle(@NotNull Object recipe);
    
    /**
     * Extract formatted recipe information from the recipe object.
     * 
     * @param recipe The recipe object from JEI
     * @return The extracted recipe data, or null if extraction failed
     */
    @Nullable RecipeData extract(@NotNull Object recipe);
    
    /**
     * Data class holding extracted recipe information.
     */
    record RecipeData(
            @NotNull String recipeId,
            @NotNull ResourceLocation workstation,
            @NotNull String workstationDescription,
            @NotNull List<SlotInfo> ingredients,
            @NotNull List<SlotInfo> outputs,
            @Nullable String additionalInfo
    ) {
        /**
         * Format the recipe data for LLM consumption.
         */
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("Recipe: ").append(recipeId).append("\n");
            sb.append("Workstation: ").append(workstation).append(" (").append(workstationDescription).append(")\n");
            
            if (!ingredients.isEmpty()) {
                sb.append("Ingredients:\n");
                for (SlotInfo ingredient : ingredients) {
                    sb.append("  ").append(ingredient.format()).append("\n");
                }
            }
            
            if (!outputs.isEmpty()) {
                sb.append("Output:\n");
                for (SlotInfo output : outputs) {
                    sb.append("  ").append(output.format()).append("\n");
                }
            }
            
            if (additionalInfo != null && !additionalInfo.isEmpty()) {
                sb.append("Info: ").append(additionalInfo);
            }
            
            return sb.toString().trim();
        }
    }
    
    /**
     * Represents a slot in a recipe with position information.
     * For shaped recipes, x and y indicate grid position.
     * For shapeless recipes, x and y can be -1.
     */
    record SlotInfo(
            @NotNull String itemId,       // namespace:name format
            int count,                    // Stack count
            int x,                        // Grid X position (-1 if shapeless)
            int y,                        // Grid Y position (-1 if shapeless)
            @Nullable String alternatives // Description if there are alternative items (tags)
    ) {
        /**
         * Format for LLM display.
         */
        public String format() {
            StringBuilder sb = new StringBuilder();
            
            // Position info for shaped recipes
            if (x >= 0 && y >= 0) {
                sb.append("[").append(x).append(",").append(y).append("] ");
            }
            
            sb.append(itemId);
            
            if (count > 1) {
                sb.append(" x").append(count);
            }
            
            if (alternatives != null && !alternatives.isEmpty()) {
                sb.append(" (").append(alternatives).append(")");
            }
            
            return sb.toString();
        }
        
        /**
         * Create a SlotInfo without position (for shapeless recipes).
         */
        public static SlotInfo of(@NotNull String itemId, int count, @Nullable String alternatives) {
            return new SlotInfo(itemId, count, -1, -1, alternatives);
        }
        
        /**
         * Create a SlotInfo with position (for shaped recipes).
         */
        public static SlotInfo of(@NotNull String itemId, int count, int x, int y, @Nullable String alternatives) {
            return new SlotInfo(itemId, count, x, y, alternatives);
        }
    }
}

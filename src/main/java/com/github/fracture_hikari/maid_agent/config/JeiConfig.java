package com.github.fracture_hikari.maid_agent.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for Maid Agent's JEI integration features.
 */
public class JeiConfig {
    public static ForgeConfigSpec.IntValue ITEM_SEARCH_MAX_RESULTS;
    public static ForgeConfigSpec.IntValue RECIPE_SEARCH_MAX_RESULTS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("jei");

        builder.comment("Maximum number of items returned by the item search function call");
        builder.comment("Higher values provide more results but consume more LLM tokens");
        ITEM_SEARCH_MAX_RESULTS = builder.defineInRange("ItemSearchMaxResults", 20, 1, 100);

        builder.comment("Maximum number of recipes returned by the recipe search function call");
        builder.comment("Higher values provide more results but consume more LLM tokens");
        RECIPE_SEARCH_MAX_RESULTS = builder.defineInRange("RecipeSearchMaxResults", 10, 1, 50);

        builder.pop();
    }
}

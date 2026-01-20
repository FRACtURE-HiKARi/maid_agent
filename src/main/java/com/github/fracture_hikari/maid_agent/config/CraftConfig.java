package com.github.fracture_hikari.maid_agent.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for Maid Agent's crafting features.
 */
public class CraftConfig {
    public static ForgeConfigSpec.IntValue MAX_CRAFT_OUTPUT;
    public static ForgeConfigSpec.IntValue MAX_CRAFT_TREE_DEPTH;
    public static ForgeConfigSpec.BooleanValue ENABLE_FURNACE_CRAFTING;
    public static ForgeConfigSpec.IntValue MAX_ALTERNATE_TREES;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("crafting");

        builder.comment("Maximum number of items to craft per LLM request");
        MAX_CRAFT_OUTPUT = builder.defineInRange("MaxCraftOutput", 64, 1, 256);

        builder.comment("Maximum depth when searching crafting tree for missing ingredients");
        MAX_CRAFT_TREE_DEPTH = builder.defineInRange("MaxCraftTreeDepth", 3, 1, 10);

        builder.comment("Enable furnace-based crafting (smelting, with fuel detection)");
        ENABLE_FURNACE_CRAFTING = builder.define("EnableFurnaceCrafting", true);
        
        builder.comment("Maximum number of alternate trees to show when no valid tree found");
        MAX_ALTERNATE_TREES = builder.defineInRange("MaxAlternateTrees", 3, 1, 10);

        builder.pop();
    }
}

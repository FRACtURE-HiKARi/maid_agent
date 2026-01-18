package com.github.fracture_hikari.maid_agent.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Main configuration class for Maid Agent.
 * Aggregates all sub-config sections.
 */
public final class MaidAgentConfig {
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        JeiConfig.init(builder);
        SPEC = builder.build();
    }
}

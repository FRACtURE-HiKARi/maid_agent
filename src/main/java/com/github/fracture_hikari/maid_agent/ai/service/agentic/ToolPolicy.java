package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.fml.ModList;

/**
 * Policy interface for controlling tool availability.
 */
@FunctionalInterface
public interface ToolPolicy {
    
    boolean isAllowed(String toolId, EntityMaid maid);

    /**
     * Always allow the tool.
     */
    static ToolPolicy always() {
        return (toolId, maid) -> true;
    }

    /**
     * Require a mod to be loaded.
     */
    static ToolPolicy requireMod(String modId) {
        return (toolId, maid) -> ModList.get().isLoaded(modId);
    }

    /**
     * Check JSON config for tool enablement.
     */
    static ToolPolicy requireConfig() {
        return (toolId, maid) -> ToolConfig.getInstance().isToolEnabled(toolId);
    }

    /**
     * Chain multiple policies (all must pass).
     */
    static ToolPolicy chain(ToolPolicy... policies) {
        return (toolId, maid) -> {
            for (ToolPolicy policy : policies) {
                if (!policy.isAllowed(toolId, maid)) {
                    return false;
                }
            }
            return true;
        };
    }
}

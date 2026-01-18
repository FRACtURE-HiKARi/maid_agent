package io.github.fracture_hikari.maid_agent.ai.service.function;

import io.github.fracture_hikari.maid_agent.MaidAgent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin that captures the runtime instance when JEI initializes.
 * This allows our LLM functions to access JEI's ingredient and recipe APIs.
 */
@JeiPlugin
public class MaidAgentJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        MaidAgent.LOGGER.info("JEI runtime available, capturing for LLM functions");
        JeiRuntimeHolder.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        MaidAgent.LOGGER.info("JEI runtime unavailable");
        JeiRuntimeHolder.setRuntime(null);
    }
}


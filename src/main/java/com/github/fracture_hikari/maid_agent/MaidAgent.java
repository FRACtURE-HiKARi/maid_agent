package com.github.fracture_hikari.maid_agent;

import com.github.fracture_hikari.maid_agent.compat.cloth.ClothConfigExtension;
import com.github.fracture_hikari.maid_agent.config.MaidAgentConfig;
import com.github.fracture_hikari.maid_agent.recipe.RecipeFormatterRegistry;
import com.github.fracture_hikari.maid_agent.recipe.formatter.CookingRecipeFormatter;
import com.github.fracture_hikari.maid_agent.recipe.formatter.CraftingRecipeFormatter;
import com.github.fracture_hikari.maid_agent.recipe.formatter.SmithingRecipeFormatter;
import com.github.fracture_hikari.maid_agent.recipe.formatter.StonecutterRecipeFormatter;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MaidAgent.MODID)
public class MaidAgent {
    public static final String MODID = "maid_agent";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SuppressWarnings("removal")
    public MaidAgent() {
        LOGGER.info("Maid Agent initializing...");

        // Register memory module types
        MemoryModuleRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Register the config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidAgentConfig.SPEC);
        
        // Register vanilla recipe formatters
        registerRecipeFormatters();
        
        // Register client-side cloth config integration
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(ClothConfigExtension.class);
            LOGGER.info("Registered Cloth Config integration");
        });
    }
    
    private void registerRecipeFormatters() {
        LOGGER.info("Registering recipe formatters...");
        RecipeFormatterRegistry.register(new CraftingRecipeFormatter());
        RecipeFormatterRegistry.register(new CookingRecipeFormatter());
        RecipeFormatterRegistry.register(new SmithingRecipeFormatter());
        RecipeFormatterRegistry.register(new StonecutterRecipeFormatter());
    }
}




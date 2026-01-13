package io.github.fracture_hikari.maid_agent;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MaidAgent.MODID)
public class MaidAgent {
    public static final String MODID = "maid_agent";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public MaidAgent() {

        LOGGER.info("Maid Agent initializing...");

        // Register any deferred registries here if needed
        // Example: ItemRegistry.register(modEventBus);
    }

}

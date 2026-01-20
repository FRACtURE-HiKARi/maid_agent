package com.github.fracture_hikari.maid_agent.compat;

import net.minecraftforge.fml.ModList;

/**
 * Mod integration detection utilities.
 * Checks if optional mods are loaded at runtime.
 */
public class Integrations {
    
    /**
     * Check if maid_storage_manager is installed.
     * Required for advanced storage/crafting operations.
     */
    public static boolean maidStorageManager() {
        return ModList.get().isLoaded("maid_storage_manager");
    }
    
    /**
     * Check if JEI is installed.
     */
    public static boolean jei() {
        return ModList.get().isLoaded("jei");
    }
    
    /**
     * Check if Cloth Config is installed.
     */
    public static boolean clothConfig() {
        return ModList.get().isLoaded("cloth_config");
    }
    
    /**
     * Message to show when maid_storage_manager is required but not installed.
     */
    public static String getMsmRequiredMessage() {
        return "This feature requires 'Maid Storage Manager' mod. " +
               "Please install it from CurseForge for advanced storage and crafting support.";
    }
}

package com.github.fracture_hikari.maid_agent.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Utility class for localized text/messages.
 * Use this for both config and function call messages.
 */
public final class LocaleUtils {
    
    private LocaleUtils() {}
    
    /**
     * Create a translatable component with the given key.
     */
    public static MutableComponent translate(String key) {
        return Component.translatable(key);
    }
    
    /**
     * Create a translatable component with the given key and arguments.
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }
    
    /**
     * Create a literal component (non-translatable).
     */
    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }
    
    /**
     * Get the translated string for a key.
     * Falls back to the key if translation is not found.
     */
    public static String get(String key) {
        return translate(key).getString();
    }
    
    /**
     * Get the translated string for a key with arguments.
     */
    public static String get(String key, Object... args) {
        return translate(key, args).getString();
    }
    
    // Common prefixes for easy access
    public static final String CONFIG_PREFIX = "config.maid_agent.";
    public static final String FUNCTION_PREFIX = "function.maid_agent.";
    public static final String MESSAGE_PREFIX = "message.maid_agent.";
}

package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON-based configuration for tool availability.
 * Config file: .minecraft/config/maid_agent/tools.json
 * 
 * On first run, generates a template with all registered tools enabled.
 * Users can set "enabled": false to disable specific tools.
 */
public class ToolConfig {
    private static final String CONFIG_FOLDER = "maid_agent";
    private static final String CONFIG_FILE = "tools.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ToolConfig INSTANCE;
    
    // Map of toolId -> enabled status (true by default)
    private final Map<String, Boolean> toolEnabled = new HashMap<>();
    private boolean requireExplorationFirst = false;
    private boolean loaded = false;
    
    private ToolConfig() {}
    
    public static ToolConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ToolConfig();
        }
        return INSTANCE;
    }
    
    /**
     * Check if a tool is enabled in config.
     * If config hasn't been loaded yet, returns true (all tools enabled by default).
     */
    public boolean isToolEnabled(String toolId) {
        if (!loaded) {
            return true;  // Before config loads, allow everything
        }
        // Default to enabled if not explicitly configured
        return toolEnabled.getOrDefault(toolId, true);
    }
    
    /**
     * Check if exploration is required before running tools.
     */
    public boolean requiresExplorationFirst() {
        return requireExplorationFirst;
    }
    
    /**
     * Get the config file path.
     */
    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
            .resolve(CONFIG_FOLDER)
            .resolve(CONFIG_FILE);
    }
    
    /**
     * Load configuration from JSON file.
     * Should be called AFTER tools are registered in the registry.
     */
    public void load() {
        Path configPath = getConfigPath();
        
        if (!Files.exists(configPath)) {
            // First run - generate template with all registered tools
            generateTemplate();
            loaded = true;
            return;
        }
        
        try {
            String content = Files.readString(configPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            toolEnabled.clear();
            
            // Parse tools array - each entry has id and enabled
            if (json.has("tools") && json.get("tools").isJsonArray()) {
                json.getAsJsonArray("tools").forEach(element -> {
                    if (element.isJsonObject()) {
                        JsonObject toolObj = element.getAsJsonObject();
                        String id = toolObj.has("id") ? toolObj.get("id").getAsString() : "";
                        boolean enabled = !toolObj.has("enabled") || toolObj.get("enabled").getAsBoolean();
                        if (!id.isEmpty()) {
                            toolEnabled.put(id, enabled);
                        }
                    }
                });
            }
            
            if (json.has("require_exploration_first")) {
                requireExplorationFirst = json.get("require_exploration_first").getAsBoolean();
            }
            
            loaded = true;
            MaidAgent.LOGGER.info("Loaded tool config from {} ({} tools configured)", configPath, toolEnabled.size());
        } catch (IOException e) {
            MaidAgent.LOGGER.error("Failed to load tool config: {}", e.getMessage());
            loaded = true;  // Mark as loaded to prevent repeated attempts
        }
    }
    
    /**
     * Generate a template config with all registered tools listed.
     */
    public void generateTemplate() {
        Path configPath = getConfigPath();
        
        try {
            Files.createDirectories(configPath.getParent());
            
            JsonObject json = new JsonObject();
            
            // Add comment explaining the config
            json.addProperty("_comment", "Tool configuration for maid_agent. Set 'enabled': false to disable a tool.");
            
            // Build tools array from registry
            JsonArray toolsArray = new JsonArray();
            AgenticToolRegistry registry = AgenticToolRegistry.getInstance();
            
            for (AgenticTool<?> tool : registry.getAllTools()) {
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("id", tool.getId());
                toolObj.addProperty("description", tool.getShortDescription());
                toolObj.addProperty("category", tool.getCategory().getId());
                toolObj.addProperty("enabled", true);  // All enabled by default
                toolsArray.add(toolObj);
            }
            
            json.add("tools", toolsArray);
            json.addProperty("require_exploration_first", false);
            
            Files.writeString(configPath, GSON.toJson(json));
            MaidAgent.LOGGER.info("Generated tool config template at {} ({} tools)", configPath, toolsArray.size());
            
            // Mark all tools as enabled by default
            for (AgenticTool<?> tool : registry.getAllTools()) {
                toolEnabled.put(tool.getId(), true);
            }
        } catch (IOException e) {
            MaidAgent.LOGGER.error("Failed to generate tool config: {}", e.getMessage());
        }
    }
    
    /**
     * Reload configuration from disk.
     */
    public void reload() {
        loaded = false;
        toolEnabled.clear();
        load();
    }
}

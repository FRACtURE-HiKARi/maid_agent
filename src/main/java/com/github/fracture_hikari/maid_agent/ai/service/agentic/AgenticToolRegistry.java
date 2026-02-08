package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Singleton registry for all agentic tools.
 * Used by ExploreToolsFunction and RunToolFunction.
 */
public class AgenticToolRegistry {
    private static final AgenticToolRegistry INSTANCE = new AgenticToolRegistry();
    
    private final Map<String, AgenticTool<?>> tools = new LinkedHashMap<>();
    private final Map<ToolCategory, List<AgenticTool<?>>> byCategory = new EnumMap<>(ToolCategory.class);

    private AgenticToolRegistry() {
        // Initialize category lists
        for (ToolCategory cat : ToolCategory.values()) {
            byCategory.put(cat, new ArrayList<>());
        }
    }

    public static AgenticToolRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register an agentic tool.
     */
    public void register(AgenticTool<?> tool) {
        tools.put(tool.getId(), tool);
        byCategory.get(tool.getCategory()).add(tool);
    }

    /**
     * Get a tool by ID.
     */
    @Nullable
    public AgenticTool<?> get(String id) {
        return tools.get(id);
    }

    /**
     * Get all registered tools.
     */
    public Collection<AgenticTool<?>> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Get all categories that have at least one tool.
     */
    public List<ToolCategory> getCategories() {
        List<ToolCategory> result = new ArrayList<>();
        for (ToolCategory cat : ToolCategory.values()) {
            if (!byCategory.get(cat).isEmpty()) {
                result.add(cat);
            }
        }
        return result;
    }

    /**
     * Get tools in a specific category.
     */
    public List<AgenticTool<?>> getToolsInCategory(ToolCategory category) {
        return Collections.unmodifiableList(byCategory.getOrDefault(category, List.of()));
    }

    /**
     * Get count of tools in a category.
     */
    public int getToolCount(ToolCategory category) {
        return byCategory.getOrDefault(category, List.of()).size();
    }

    /**
     * Clear all registered tools (for testing).
     */
    public void clear() {
        tools.clear();
        for (ToolCategory cat : ToolCategory.values()) {
            byCategory.get(cat).clear();
        }
    }
}

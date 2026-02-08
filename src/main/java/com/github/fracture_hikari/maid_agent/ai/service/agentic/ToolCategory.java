package com.github.fracture_hikari.maid_agent.ai.service.agentic;

/**
 * Categories for grouping agentic tools.
 * Used by ExploreToolsFunction for hierarchical discovery.
 */
public enum ToolCategory {
    STORAGE("storage", "Manage containers and items"),
    CRAFTING("crafting", "Create items from recipes"),
    STATUS("status", "Query maid state and progress"),
    CONTROL("control", "Manage task execution");

    private final String id;
    private final String description;

    ToolCategory(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static ToolCategory fromId(String id) {
        for (ToolCategory cat : values()) {
            if (cat.id.equals(id)) {
                return cat;
            }
        }
        return null;
    }
}

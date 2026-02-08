package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.util.List;

/**
 * Core interface for agentic tools.
 * Wraps existing IFunctionCall implementations with additional metadata
 * for hierarchical exploration and policy filtering.
 *
 * @param <T> The parameter type for this tool
 */
public interface AgenticTool<T> {
    
    /**
     * Unique identifier for this tool.
     */
    String getId();

    /**
     * Category for hierarchical grouping.
     */
    ToolCategory getCategory();

    /**
     * Short description (~10 tokens) for category listing.
     */
    String getShortDescription();

    /**
     * Full description with parameter details for tool exploration.
     */
    String getFullDescription(EntityMaid maid);

    /**
     * List of tool IDs that should be called before this one.
     */
    default List<String> getPrerequisites() {
        return List.of();
    }

    /**
     * Policy controlling when this tool is available.
     */
    default ToolPolicy getPolicy() {
        return ToolPolicy.always();
    }

    /**
     * Get parameter schema description for LLM.
     * Should list all parameters with types and descriptions.
     */
    default String getParameterSchema(EntityMaid maid) {
        return "";  // Override in implementations
    }

    /**
     * Codec for parsing JSON parameters.
     */
    Codec<T> codec();

    /**
     * Execute the tool with typed parameters.
     */
    AgenticResponse execute(T params, EntityMaid maid, ToolContext ctx);

    /**
     * Execute from raw JSON - parses params and delegates to execute().
     * This is called on main thread, parsing overhead is acceptable.
     */
    default ToolResponse executeFromJson(JsonObject json, EntityMaid maid, String toolCallId) {
        var result = codec().parse(JsonOps.INSTANCE, json).resultOrPartial(err -> {});
        if (result.isEmpty()) {
            return new ToolResponse("Invalid parameters for " + getId() + ". you should probably try to explore the useage again.");
        }
        
        AgenticResponse response = execute(result.get(), maid, new ToolContext(toolCallId));
        if (response.isPending()) {
            return ToolResponse.PENDING;
        }
        return response.toToolResponse();
    }
}

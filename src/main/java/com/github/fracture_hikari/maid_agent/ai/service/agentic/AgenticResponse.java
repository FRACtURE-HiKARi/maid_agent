package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Structured response from agentic tool execution.
 * Provides both machine-readable data and human-readable message.
 */
public class AgenticResponse {
    private final boolean success;
    private final JsonObject data;
    private final String message;
    private final List<String> suggestedNextTools;

    private AgenticResponse(boolean success, JsonObject data, String message, List<String> suggestedNextTools) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.suggestedNextTools = suggestedNextTools;
    }

    public boolean isSuccess() {
        return success;
    }

    public JsonObject getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getSuggestedNextTools() {
        return suggestedNextTools;
    }

    /**
     * Convert to ToolResponse for IFunctionCall compatibility.
     */
    public ToolResponse toToolResponse() {
        // Include structured data in message if present
        if (data != null && data.size() > 0) {
            return new ToolResponse(message + "\n[DATA]" + data.toString());
        }
        return new ToolResponse(message);
    }

    /**
     * Create from existing ToolResponse.
     */
    public static AgenticResponse fromToolResponse(ToolResponse tr) {
        if (tr.isPending()) {
            return pending();
        }
        return success(tr.message());
    }

    // Builder methods
    
    public static AgenticResponse success(String message) {
        return new AgenticResponse(true, null, message, List.of());
    }

    public static AgenticResponse success(String message, JsonObject data) {
        return new AgenticResponse(true, data, message, List.of());
    }

    public static AgenticResponse success(String message, JsonObject data, List<String> nextTools) {
        return new AgenticResponse(true, data, message, nextTools);
    }

    public static AgenticResponse failure(String message) {
        return new AgenticResponse(false, null, message, List.of());
    }

    public static AgenticResponse pending() {
        return new AgenticResponse(true, null, null, List.of());
    }
    
    public boolean isPending() {
        return success && message == null;
    }
}

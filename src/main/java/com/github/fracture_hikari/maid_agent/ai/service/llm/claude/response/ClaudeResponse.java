package com.github.fracture_hikari.maid_agent.ai.service.llm.claude.response;

import com.google.gson.annotations.SerializedName;
import com.github.fracture_hikari.maid_agent.ai.service.llm.claude.request.ClaudeContent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Claude API response from /v1/messages endpoint.
 */
public class ClaudeResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("type")
    private String type;

    @SerializedName("role")
    private String role;

    @SerializedName("content")
    @Nullable
    private List<ClaudeContent> content;

    @SerializedName("model")
    private String model;

    @SerializedName("stop_reason")
    @Nullable
    private String stopReason;

    @SerializedName("usage")
    @Nullable
    private ClaudeUsage usage;

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getRole() {
        return role;
    }

    @Nullable
    public List<ClaudeContent> getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    @Nullable
    public String getStopReason() {
        return stopReason;
    }

    @Nullable
    public ClaudeUsage getUsage() {
        return usage;
    }

    /**
     * Check if the response indicates a tool use request.
     */
    public boolean hasToolUse() {
        return "tool_use".equals(stopReason) || containsToolUseContent();
    }

    private boolean containsToolUseContent() {
        if (content == null) {
            return false;
        }
        for (ClaudeContent c : content) {
            if (c.isToolUse()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the first tool use content block from the response.
     */
    @Nullable
    public ClaudeContent getFirstToolUse() {
        if (content == null) {
            return null;
        }
        for (ClaudeContent c : content) {
            if (c.isToolUse()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Get text from all text content blocks.
     */
    @Nullable
    public String getText() {
        if (content == null || content.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ClaudeContent c : content) {
            if (c.isText() && c.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(c.getText());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}

package com.github.fracture_hikari.maid_agent.ai.service.llm.claude.request;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Claude API content block - can be text, tool_use, or tool_result.
 */
public class ClaudeContent {
    @SerializedName("type")
    private String type;

    @SerializedName("text")
    @Nullable
    private String text;

    // Tool use fields (for assistant response / request history)
    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("name")
    @Nullable
    private String name;

    @SerializedName("input")
    @Nullable
    private Object input;

    // Tool result fields (for sending tool results back)
    @SerializedName("tool_use_id")
    @Nullable
    private String toolUseId;

    @SerializedName("content")
    @Nullable
    private String toolResultContent;

    public static ClaudeContent text(String text) {
        ClaudeContent content = new ClaudeContent();
        content.type = "text";
        content.text = text;
        return content;
    }

    public static ClaudeContent toolUse(String id, String name, Object input) {
        ClaudeContent content = new ClaudeContent();
        content.type = "tool_use";
        content.id = id;
        content.name = name;
        content.input = input;
        return content;
    }

    public static ClaudeContent toolResult(String toolUseId, String result) {
        ClaudeContent content = new ClaudeContent();
        content.type = "tool_result";
        content.toolUseId = toolUseId;
        content.toolResultContent = result;
        return content;
    }

    public String getType() {
        return type;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public Object getInput() {
        return input;
    }

    public String getInputAsJson() {
        if (input == null) {
            return "{}";
        }
        return com.github.tartaricacid.touhoulittlemaid.ai.service.Client.GSON.toJson(input);
    }

    @Nullable
    public String getToolUseId() {
        return toolUseId;
    }

    public boolean isToolUse() {
        return "tool_use".equals(type);
    }

    public boolean isText() {
        return "text".equals(type);
    }
}

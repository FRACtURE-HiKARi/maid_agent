package io.github.fracture_hikari.maid_agent.ai.service.llm.claude.request;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude API message object with role and content.
 */
public class ClaudeMessage {
    @SerializedName("role")
    private String role;

    @SerializedName("content")
    private Object content;

    public static ClaudeMessage user(String text) {
        ClaudeMessage message = new ClaudeMessage();
        message.role = "user";
        message.content = text;
        return message;
    }

    public static ClaudeMessage userWithContent(List<ClaudeContent> contents) {
        ClaudeMessage message = new ClaudeMessage();
        message.role = "user";
        message.content = contents;
        return message;
    }

    public static ClaudeMessage assistant(String text) {
        ClaudeMessage message = new ClaudeMessage();
        message.role = "assistant";
        message.content = text;
        return message;
    }

    public static ClaudeMessage assistantWithContent(List<ClaudeContent> contents) {
        ClaudeMessage message = new ClaudeMessage();
        message.role = "assistant";
        message.content = contents;
        return message;
    }

    public static ClaudeMessage toolResult(String toolUseId, String result) {
        ClaudeMessage message = new ClaudeMessage();
        message.role = "user";
        List<ClaudeContent> contents = new ArrayList<>();
        contents.add(ClaudeContent.toolResult(toolUseId, result));
        message.content = contents;
        return message;
    }

    public String getRole() {
        return role;
    }

    public Object getContent() {
        return content;
    }
}

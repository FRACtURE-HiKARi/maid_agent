package com.github.fracture_hikari.maid_agent.ai.service.llm.claude.request;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Claude API request body for /v1/messages endpoint.
 */
public class ClaudeRequest {
    @SerializedName("model")
    private String model;

    @SerializedName("max_tokens")
    private int maxTokens = 4096;

    @SerializedName("messages")
    private List<ClaudeMessage> messages = Lists.newArrayList();

    @SerializedName("system")
    @Nullable
    private String system;

    @SerializedName("temperature")
    @Nullable
    private Double temperature;

    @SerializedName("tools")
    @Nullable
    private List<ClaudeTool> tools;

    public static ClaudeRequest create() {
        return new ClaudeRequest();
    }

    public ClaudeRequest model(String model) {
        this.model = model;
        return this;
    }

    public ClaudeRequest maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public ClaudeRequest temperature(double temperature) {
        this.temperature = Math.min(temperature, 1.0);
        return this;
    }

    public ClaudeRequest system(String system) {
        this.system = system;
        return this;
    }

    public ClaudeRequest addMessage(ClaudeMessage message) {
        this.messages.add(message);
        return this;
    }

    public ClaudeRequest addUserMessage(String text) {
        this.messages.add(ClaudeMessage.user(text));
        return this;
    }

    public ClaudeRequest addAssistantMessage(String text) {
        this.messages.add(ClaudeMessage.assistant(text));
        return this;
    }

    public ClaudeRequest addToolResult(String toolUseId, String result) {
        this.messages.add(ClaudeMessage.toolResult(toolUseId, result));
        return this;
    }

    public ClaudeRequest addTool(ClaudeTool tool) {
        if (this.tools == null) {
            this.tools = Lists.newArrayList();
        }
        this.tools.add(tool);
        return this;
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public List<ClaudeMessage> getMessages() {
        return messages;
    }

    @Nullable
    public String getSystem() {
        return system;
    }

    @Nullable
    public Double getTemperature() {
        return temperature;
    }

    @Nullable
    public List<ClaudeTool> getTools() {
        return tools;
    }
}

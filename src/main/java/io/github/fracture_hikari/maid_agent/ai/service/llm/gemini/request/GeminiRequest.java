package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gemini API request body for generateContent endpoint.
 */
public class GeminiRequest {
    @SerializedName("contents")
    private List<GeminiContent> contents = Lists.newArrayList();

    @SerializedName("generationConfig")
    @Nullable
    private GeminiGenerationConfig generationConfig;

    @SerializedName("systemInstruction")
    @Nullable
    private GeminiContent systemInstruction;

    @SerializedName("tools")
    @Nullable
    private List<GeminiTool> tools;

    public static GeminiRequest create() {
        return new GeminiRequest();
    }

    public GeminiRequest addContent(GeminiContent content) {
        this.contents.add(content);
        return this;
    }

    public GeminiRequest addUserMessage(String message) {
        this.contents.add(GeminiContent.userContent(message));
        return this;
    }

    public GeminiRequest addModelMessage(String message) {
        this.contents.add(GeminiContent.modelContent(message));
        return this;
    }

    public GeminiRequest addFunctionCall(String name, String args) {
        this.contents.add(GeminiContent.functionCallContent(name, args));
        return this;
    }

    public GeminiRequest addFunctionResponse(String name, String response) {
        this.contents.add(GeminiContent.functionResponseContent(name, response));
        return this;
    }

    public GeminiRequest generationConfig(GeminiGenerationConfig config) {
        this.generationConfig = config;
        return this;
    }

    public GeminiRequest systemInstruction(String instruction) {
        this.systemInstruction = GeminiContent.create(null, List.of(GeminiPart.fromText(instruction)));
        return this;
    }

    public GeminiRequest tools(List<GeminiTool> tools) {
        this.tools = tools;
        return this;
    }

    public GeminiRequest addTool(GeminiTool tool) {
        if (this.tools == null) {
            this.tools = Lists.newArrayList();
        }
        this.tools.add(tool);
        return this;
    }

    public List<GeminiContent> getContents() {
        return contents;
    }

    @Nullable
    public GeminiGenerationConfig getGenerationConfig() {
        return generationConfig;
    }

    @Nullable
    public GeminiContent getSystemInstruction() {
        return systemInstruction;
    }

    @Nullable
    public List<GeminiTool> getTools() {
        return tools;
    }
}

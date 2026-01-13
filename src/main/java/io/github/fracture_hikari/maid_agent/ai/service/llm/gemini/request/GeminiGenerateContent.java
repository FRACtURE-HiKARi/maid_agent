package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Main request body for Gemini generateContent API.
 * Contains the conversation contents, generation config, and tools.
 */
public class GeminiGenerateContent {
    @SerializedName("contents")
    private List<GeminiContent> contents = Lists.newArrayList();

    @SerializedName("systemInstruction")
    @Nullable
    private SystemInstructionContent systemInstruction;

    @SerializedName("generationConfig")
    private GeminiGenerationConfig generationConfig;

    @SerializedName("tools")
    @Nullable
    private List<GeminiTool> tools;

    public static GeminiGenerateContent create() {
        return new GeminiGenerateContent();
    }

    public GeminiGenerateContent setGenerationConfig(GeminiGenerationConfig config) {
        this.generationConfig = config;
        return this;
    }

    public GeminiGenerateContent setSystemInstruction(String instruction) {
        SystemInstructionContent content = new SystemInstructionContent();
        content.addPart(GeminiPart.text(instruction));
        this.systemInstruction = content;
        return this;
    }

    public GeminiGenerateContent addUserContent(String text) {
        this.contents.add(GeminiContent.user(text));
        return this;
    }

    public GeminiGenerateContent addModelContent(String text) {
        this.contents.add(GeminiContent.model(text));
        return this;
    }

    public GeminiGenerateContent addModelFunctionCall(String name, String args) {
        this.contents.add(GeminiContent.modelWithFunctionCall(name, args));
        return this;
    }

    public GeminiGenerateContent addFunctionResponse(String name, String response) {
        this.contents.add(GeminiContent.functionResponse(name, response));
        return this;
    }

    public GeminiGenerateContent addContent(GeminiContent content) {
        this.contents.add(content);
        return this;
    }

    public GeminiGenerateContent addTool(GeminiTool tool) {
        if (this.tools == null) {
            this.tools = Lists.newArrayList();
        }
        if (!tool.isEmpty()) {
            this.tools.add(tool);
        }
        return this;
    }

    public List<GeminiContent> getContents() {
        return contents;
    }

    @Nullable
    public SystemInstructionContent getSystemInstruction() {
        return systemInstruction;
    }

    public GeminiGenerationConfig getGenerationConfig() {
        return generationConfig;
    }

    @Nullable
    public List<GeminiTool> getTools() {
        return tools;
    }

    /**
     * Inner class for system instruction content without role.
     * System instruction in Gemini API doesn't have a role field.
     */
    public static class SystemInstructionContent {
        @SerializedName("parts")
        private List<GeminiPart> parts = Lists.newArrayList();

        public SystemInstructionContent addPart(GeminiPart part) {
            this.parts.add(part);
            return this;
        }
    }
}

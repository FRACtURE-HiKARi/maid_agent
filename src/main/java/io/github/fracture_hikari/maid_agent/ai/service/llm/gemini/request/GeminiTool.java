package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Tool definition for Gemini API.
 * Contains function declarations for function calling capability.
 */
public class GeminiTool {
    @SerializedName("functionDeclarations")
    private List<GeminiFunctionDeclaration> functionDeclarations = Lists.newArrayList();

    public static GeminiTool create() {
        return new GeminiTool();
    }

    public GeminiTool addFunctionDeclaration(GeminiFunctionDeclaration declaration) {
        this.functionDeclarations.add(declaration);
        return this;
    }

    public List<GeminiFunctionDeclaration> getFunctionDeclarations() {
        return functionDeclarations;
    }

    public boolean isEmpty() {
        return functionDeclarations.isEmpty();
    }
}

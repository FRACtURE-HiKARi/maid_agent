package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gemini API Tool object containing function declarations.
 */
public class GeminiTool {
    @SerializedName("functionDeclarations")
    private List<GeminiFunctionDeclaration> functionDeclarations;

    public GeminiTool(List<GeminiFunctionDeclaration> functionDeclarations) {
        this.functionDeclarations = functionDeclarations;
    }

    public static GeminiTool create(List<GeminiFunctionDeclaration> declarations) {
        return new GeminiTool(declarations);
    }

    public List<GeminiFunctionDeclaration> getFunctionDeclarations() {
        return functionDeclarations;
    }
}

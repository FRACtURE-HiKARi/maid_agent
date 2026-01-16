package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gemini API Content object representing a message turn.
 */
public class GeminiContent {
    @SerializedName("role")
    private String role;

    @SerializedName("parts")
    private List<GeminiPart> parts;

    public static GeminiContent create(String role, List<GeminiPart> parts) {
        GeminiContent content = new GeminiContent();
        content.role = role;
        content.parts = parts;
        return content;
    }

    public static GeminiContent userContent(String text) {
        return create("user", List.of(GeminiPart.fromText(text)));
    }

    public static GeminiContent modelContent(String text) {
        return create("model", List.of(GeminiPart.fromText(text)));
    }

    public static GeminiContent functionCallContent(String name, String args) {
        return create("model", List.of(GeminiPart.fromFunctionCall(name, args)));
    }

    public static GeminiContent functionResponseContent(String name, String response) {
        return create("user", List.of(GeminiPart.fromFunctionResponse(name, response)));
    }

    public String getRole() {
        return role;
    }

    public List<GeminiPart> getParts() {
        return parts;
    }

    @Nullable
    public String getText() {
        if (parts != null && !parts.isEmpty()) {
            return parts.get(0).getText();
        }
        return null;
    }
}

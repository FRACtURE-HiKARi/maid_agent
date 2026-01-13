package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Content object for Gemini API request.
 * Represents a message in the conversation with a role and parts.
 */
public class GeminiContent {
    @SerializedName("role")
    private String role;

    @SerializedName("parts")
    private List<GeminiPart> parts = Lists.newArrayList();

    private GeminiContent(String role) {
        this.role = role;
    }

    public static GeminiContent user(String text) {
        GeminiContent content = new GeminiContent("user");
        content.parts.add(GeminiPart.text(text));
        return content;
    }

    public static GeminiContent model(String text) {
        GeminiContent content = new GeminiContent("model");
        content.parts.add(GeminiPart.text(text));
        return content;
    }

    public static GeminiContent modelWithFunctionCall(String name, String args) {
        GeminiContent content = new GeminiContent("model");
        content.parts.add(GeminiPart.functionCall(name, args));
        return content;
    }

    public static GeminiContent functionResponse(String name, String response) {
        GeminiContent content = new GeminiContent("user");
        content.parts.add(GeminiPart.functionResponse(name, response));
        return content;
    }

    public String getRole() {
        return role;
    }

    public List<GeminiPart> getParts() {
        return parts;
    }

    public GeminiContent addPart(GeminiPart part) {
        this.parts.add(part);
        return this;
    }
}

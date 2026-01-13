package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Content from Gemini API response.
 * Contains the role and parts of the response.
 */
public class GeminiContentResponse {
    @SerializedName("parts")
    private List<GeminiPartResponse> parts;

    @SerializedName("role")
    private String role;

    public List<GeminiPartResponse> getParts() {
        return parts;
    }

    public String getRole() {
        return role;
    }

    /**
     * Get the first text content from parts.
     */
    @Nullable
    public String getFirstText() {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        for (GeminiPartResponse part : parts) {
            if (part.hasText()) {
                return part.getText();
            }
        }
        return null;
    }

    /**
     * Get the first function call from parts.
     */
    @Nullable
    public GeminiFunctionCall getFirstFunctionCall() {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        for (GeminiPartResponse part : parts) {
            if (part.hasFunctionCall()) {
                return part.getFunctionCall();
            }
        }
        return null;
    }

    /**
     * Check if any part contains a function call.
     */
    public boolean hasFunctionCall() {
        if (parts == null || parts.isEmpty()) {
            return false;
        }
        for (GeminiPartResponse part : parts) {
            if (part.hasFunctionCall()) {
                return true;
            }
        }
        return false;
    }
}

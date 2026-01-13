package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Part in Gemini API response.
 * Contains either text content or a function call.
 */
public class GeminiPartResponse {
    @SerializedName("text")
    @Nullable
    private String text;

    @SerializedName("functionCall")
    @Nullable
    private GeminiFunctionCall functionCall;

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public GeminiFunctionCall getFunctionCall() {
        return functionCall;
    }

    public boolean hasFunctionCall() {
        return functionCall != null;
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }
}

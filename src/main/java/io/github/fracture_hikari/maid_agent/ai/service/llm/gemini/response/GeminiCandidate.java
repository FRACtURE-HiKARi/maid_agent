package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Candidate response from Gemini API.
 * Contains the generated content and finish reason.
 */
public class GeminiCandidate {
    @SerializedName("content")
    private GeminiContentResponse content;

    @SerializedName("finishReason")
    private String finishReason;

    @SerializedName("index")
    private int index;

    public GeminiContentResponse getContent() {
        return content;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public int getIndex() {
        return index;
    }

    /**
     * Get the text content from this candidate.
     */
    @Nullable
    public String getText() {
        if (content == null) {
            return null;
        }
        return content.getFirstText();
    }

    /**
     * Check if this candidate has a function call.
     */
    public boolean hasFunctionCall() {
        return content != null && content.hasFunctionCall();
    }

    /**
     * Get the first function call from this candidate.
     */
    @Nullable
    public GeminiFunctionCall getFunctionCall() {
        if (content == null) {
            return null;
        }
        return content.getFirstFunctionCall();
    }
}

package com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request.GeminiContent;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request.GeminiPart;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gemini API response from generateContent endpoint.
 */
public class GeminiResponse {
    @SerializedName("candidates")
    @Nullable
    private List<GeminiCandidate> candidates;

    @SerializedName("usageMetadata")
    @Nullable
    private GeminiUsageMetadata usageMetadata;

    @SerializedName("modelVersion")
    @Nullable
    private String modelVersion;

    @Nullable
    public List<GeminiCandidate> getCandidates() {
        return candidates;
    }

    @Nullable
    public GeminiUsageMetadata getUsageMetadata() {
        return usageMetadata;
    }

    @Nullable
    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * Get the first candidate's content, or null if none.
     */
    @Nullable
    public GeminiContent getFirstContent() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0).getContent();
        }
        return null;
    }

    /**
     * Extract text from the first candidate's first text part.
     */
    @Nullable
    public String getText() {
        GeminiContent content = getFirstContent();
        if (content != null && content.getParts() != null) {
            for (GeminiPart part : content.getParts()) {
                if (part.getText() != null) {
                    return part.getText();
                }
            }
        }
        return null;
    }

    /**
     * Check if the first candidate has a function call.
     */
    public boolean hasFunctionCall() {
        GeminiContent content = getFirstContent();
        if (content != null && content.getParts() != null) {
            for (GeminiPart part : content.getParts()) {
                if (part.hasFunctionCall()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the first function call from the response.
     */
    @Nullable
    public GeminiPart.GeminiFunctionCall getFirstFunctionCall() {
        GeminiContent content = getFirstContent();
        if (content != null && content.getParts() != null) {
            for (GeminiPart part : content.getParts()) {
                if (part.hasFunctionCall()) {
                    return part.getFunctionCall();
                }
            }
        }
        return null;
    }
}

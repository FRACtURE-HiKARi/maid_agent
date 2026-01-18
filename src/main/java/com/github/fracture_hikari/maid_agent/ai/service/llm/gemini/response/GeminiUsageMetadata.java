package com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;

/**
 * Gemini API UsageMetadata for token counting.
 */
public class GeminiUsageMetadata {
    @SerializedName("promptTokenCount")
    private int promptTokenCount;

    @SerializedName("candidatesTokenCount")
    private int candidatesTokenCount;

    @SerializedName("totalTokenCount")
    private int totalTokenCount;

    public int getPromptTokenCount() {
        return promptTokenCount;
    }

    public int getCandidatesTokenCount() {
        return candidatesTokenCount;
    }

    public int getTotalTokenCount() {
        return totalTokenCount;
    }
}

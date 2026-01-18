package com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.annotations.SerializedName;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request.GeminiContent;

import javax.annotation.Nullable;

/**
 * Gemini API Candidate - one of the response choices.
 */
public class GeminiCandidate {
    @SerializedName("content")
    @Nullable
    private GeminiContent content;

    @SerializedName("finishReason")
    @Nullable
    private String finishReason;

    @SerializedName("index")
    private int index;

    @Nullable
    public GeminiContent getContent() {
        return content;
    }

    @Nullable
    public String getFinishReason() {
        return finishReason;
    }

    public int getIndex() {
        return index;
    }
}

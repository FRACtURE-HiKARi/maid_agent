package io.github.fracture_hikari.maid_agent.ai.service.llm.claude.response;

import com.google.gson.annotations.SerializedName;

/**
 * Claude API usage metadata for token counting.
 */
public class ClaudeUsage {
    @SerializedName("input_tokens")
    private int inputTokens;

    @SerializedName("output_tokens")
    private int outputTokens;

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }
}

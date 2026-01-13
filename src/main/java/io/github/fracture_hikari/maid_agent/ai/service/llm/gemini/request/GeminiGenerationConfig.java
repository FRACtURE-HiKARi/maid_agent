package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

/**
 * Generation configuration for Gemini API.
 * Controls model output parameters like temperature and token limits.
 */
public class GeminiGenerationConfig {
    @SerializedName("temperature")
    private double temperature = 0.5;

    @SerializedName("maxOutputTokens")
    private int maxOutputTokens = 4096;

    @SerializedName("topP")
    private Double topP;

    @SerializedName("topK")
    private Integer topK;

    public static GeminiGenerationConfig create() {
        return new GeminiGenerationConfig();
    }

    public GeminiGenerationConfig temperature(double temperature) {
        // Temperature range is [0, 2.0)
        this.temperature = Math.min(temperature, 1.99);
        return this;
    }

    public GeminiGenerationConfig maxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public GeminiGenerationConfig topP(double topP) {
        this.topP = topP;
        return this;
    }

    public GeminiGenerationConfig topK(int topK) {
        this.topK = topK;
        return this;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getTopK() {
        return topK;
    }
}

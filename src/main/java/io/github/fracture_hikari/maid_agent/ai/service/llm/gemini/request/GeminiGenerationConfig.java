package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

/**
 * Gemini API GenerationConfig for controlling output generation.
 */
public class GeminiGenerationConfig {
    @SerializedName("temperature")
    private double temperature = 0.5;

    @SerializedName("maxOutputTokens")
    private int maxOutputTokens = 4096;

    public static GeminiGenerationConfig create() {
        return new GeminiGenerationConfig();
    }

    public GeminiGenerationConfig temperature(double temperature) {
        // Gemini temperature range is [0, 2]
        this.temperature = Math.min(temperature, 2.0);
        return this;
    }

    public GeminiGenerationConfig maxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }
}

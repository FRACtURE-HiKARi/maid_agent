package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Function call from Gemini API response.
 * Contains the function name and arguments.
 */
public class GeminiFunctionCall {
    @SerializedName("name")
    private String name;

    @SerializedName("args")
    @Nullable
    private JsonObject args;

    public String getName() {
        return name;
    }

    @Nullable
    public JsonObject getArgs() {
        return args;
    }

    /**
     * Get arguments as a JSON string.
     */
    public String getArgsAsString() {
        return args != null ? args.toString() : "{}";
    }
}

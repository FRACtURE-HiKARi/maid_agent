package com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Gemini API Part object - can contain text, functionCall, or functionResponse.
 */
public class GeminiPart {
    @SerializedName("text")
    @Nullable
    private String text;

    @SerializedName("functionCall")
    @Nullable
    private GeminiFunctionCall functionCall;

    @SerializedName("functionResponse")
    @Nullable
    private GeminiFunctionResponse functionResponse;

    public static GeminiPart fromText(String text) {
        GeminiPart part = new GeminiPart();
        part.text = text;
        return part;
    }

    public static GeminiPart fromFunctionCall(String name, String args) {
        GeminiPart part = new GeminiPart();
        part.functionCall = new GeminiFunctionCall(name, args);
        return part;
    }

    public static GeminiPart fromFunctionResponse(String name, String response) {
        GeminiPart part = new GeminiPart();
        part.functionResponse = new GeminiFunctionResponse(name, response);
        return part;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public GeminiFunctionCall getFunctionCall() {
        return functionCall;
    }

    @Nullable
    public GeminiFunctionResponse getFunctionResponse() {
        return functionResponse;
    }

    public boolean hasFunctionCall() {
        return functionCall != null;
    }

    /**
     * FunctionCall nested object for model-initiated function calls.
     */
    public static class GeminiFunctionCall {
        @SerializedName("name")
        private String name;

        @SerializedName("args")
        private Map<String, Object> args;

        // For deserialization
        public GeminiFunctionCall() {}

        public GeminiFunctionCall(String name, String argsJson) {
            this.name = name;
            // Parse args from JSON string to Map
            try {
                this.args = com.github.tartaricacid.touhoulittlemaid.ai.service.Client.GSON
                        .fromJson(argsJson, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
            } catch (Exception e) {
                this.args = Map.of("raw", argsJson);
            }
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArgs() {
            return args;
        }

        public String getArgsAsJson() {
            if (args == null) {
                return "{}";
            }
            return com.github.tartaricacid.touhoulittlemaid.ai.service.Client.GSON.toJson(args);
        }
    }

    /**
     * FunctionResponse nested object for tool results.
     */
    public static class GeminiFunctionResponse {
        @SerializedName("name")
        private String name;

        @SerializedName("response")
        private Map<String, Object> response;

        public GeminiFunctionResponse() {}

        public GeminiFunctionResponse(String name, String result) {
            this.name = name;
            this.response = Map.of("result", result);
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getResponse() {
            return response;
        }
    }
}

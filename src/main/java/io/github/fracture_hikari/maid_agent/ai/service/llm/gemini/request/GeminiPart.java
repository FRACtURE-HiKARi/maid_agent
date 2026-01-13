package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Part object for Gemini API request.
 * Contains text content or function call/response data.
 */
public class GeminiPart {
    @SerializedName("text")
    @Nullable
    private String text;

    @SerializedName("functionCall")
    @Nullable
    private GeminiFunctionCallRequest functionCall;

    @SerializedName("functionResponse")
    @Nullable
    private GeminiFunctionResponse functionResponse;

    public static GeminiPart text(String text) {
        GeminiPart part = new GeminiPart();
        part.text = text;
        return part;
    }

    public static GeminiPart functionCall(String name, String args) {
        GeminiPart part = new GeminiPart();
        part.functionCall = new GeminiFunctionCallRequest(name, args);
        return part;
    }

    public static GeminiPart functionResponse(String name, String response) {
        GeminiPart part = new GeminiPart();
        part.functionResponse = new GeminiFunctionResponse(name, response);
        return part;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public GeminiFunctionCallRequest getFunctionCall() {
        return functionCall;
    }

    @Nullable
    public GeminiFunctionResponse getFunctionResponse() {
        return functionResponse;
    }

    /**
     * Function call request structure for Gemini API.
     */
    public static class GeminiFunctionCallRequest {
        @SerializedName("name")
        private String name;

        @SerializedName("args")
        private String args;

        public GeminiFunctionCallRequest(String name, String args) {
            this.name = name;
            this.args = args;
        }

        public String getName() {
            return name;
        }

        public String getArgs() {
            return args;
        }
    }

    /**
     * Function response structure for Gemini API.
     */
    public static class GeminiFunctionResponse {
        @SerializedName("name")
        private String name;

        @SerializedName("response")
        private String response;

        public GeminiFunctionResponse(String name, String response) {
            this.name = name;
            this.response = response;
        }

        public String getName() {
            return name;
        }

        public String getResponse() {
            return response;
        }
    }
}

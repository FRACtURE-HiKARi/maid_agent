package com.github.fracture_hikari.maid_agent.ai.service.llm.gemini;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ErrorCode;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ResponseCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.FunctionCallRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.*;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.FunctionToolCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.capability.ChatTokensCapabilityProvider;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request.*;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response.GeminiResponse;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini API client using HTTP requests.
 * Implements the LLMClient interface following the OpenAI client pattern.
 */
public final class LLMGeminiClient implements LLMClient {
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final LLMGeminiSite site;

    public LLMGeminiClient(HttpClient httpClient, LLMGeminiSite site) {
        this.httpClient = httpClient;
        this.site = site;
    }

    @Override
    public void chat(List<LLMMessage> messages, LLMConfig config, ResponseCallback<ResponseChat> callback) {
        String model = config.model();
        double temperature = config.temperature();
        int maxTokens = config.maxTokens();
        EntityMaid maid = config.maid();
        ChatType chatType = config.chatType();

        // Build the API URL: {baseUrl}/{model}:generateContent?key={apiKey}
        String baseUrl = this.site.url();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        URI url = URI.create(baseUrl + model + ":generateContent?key=" + this.site.secretKey());

        // Build the request body
        GeminiRequest geminiRequest = buildRequest(messages, temperature, maxTokens, maid, chatType);

        String requestJson = GSON.toJson(geminiRequest);

        if (TouhouLittleMaid.DEBUG) {
            MaidAgent.LOGGER.debug("Chat messages: {}", messages);
            MaidAgent.LOGGER.debug("Gemini Request: {}", requestJson);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(MAX_TIMEOUT)
                .uri(url);

        this.site.headers().forEach(builder::header);
        HttpRequest httpRequest = builder.build();

        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) ->
                        handle(messages, config, callback, response, throwable, httpRequest));
    }

    private GeminiRequest buildRequest(List<LLMMessage> messages, double temperature, int maxTokens,
                                        EntityMaid maid, ChatType chatType) {
        GeminiRequest request = GeminiRequest.create();

        // Set generation config
        request.generationConfig(GeminiGenerationConfig.create()
                .temperature(temperature)
                .maxOutputTokens(maxTokens));

        // Build a map of toolCallId -> functionName from assistant messages
        // This is needed because Gemini expects function name in functionResponse,
        // but TLM stores only the toolCallId for TOOL role messages
        Map<String, String> toolCallIdToFunctionName = getIdToFunctionName(messages);

        // Extract system instruction and add other messages
        for (LLMMessage message : messages) {
            if (message.role() == Role.SYSTEM) {
                request.systemInstruction(message.message());
            } else if (message.role() == Role.USER) {
                request.addUserMessage(message.message());
            } else if (message.role() == Role.ASSISTANT) {
                if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                    request.addModelMessage(message.message());
                } else {
                    // Handle assistant message with tool calls
                    for (ToolCall toolCall : message.toolCalls()) {
                        if (toolCall.getFunction() != null) {
                            request.addFunctionCall(
                                    toolCall.getFunction().getName(),
                                    toolCall.getFunction().getArguments());
                        }
                    }
                }
            } else if (message.role() == Role.TOOL) {
                // Gemini expects the function NAME, not the call ID
                String functionName = toolCallIdToFunctionName.getOrDefault(
                        message.toolCallId(), 
                        message.toolCallId()); // Fallback to ID if name not found
                request.addFunctionResponse(functionName, message.message());
            }
        }

        // Add function call tools if enabled
        if (AIConfig.FUNCTION_CALL_ENABLED.get() && chatType != ChatType.AUTO_GEN_SETTING) {
            List<GeminiFunctionDeclaration> declarations = buildFunctionDeclarations(maid);
            if (!declarations.isEmpty()) {
                request.addTool(GeminiTool.create(declarations));
            }
        }

        return request;
    }

    private static @NotNull Map<String, String> getIdToFunctionName(List<LLMMessage> messages) {
        Map<String, String> toolCallIdToFunctionName = new java.util.HashMap<>();
        for (LLMMessage message : messages) {
            if (message.role() == Role.ASSISTANT && message.toolCalls() != null) {
                for (ToolCall toolCall : message.toolCalls()) {
                    if (toolCall.getId() != null && toolCall.getFunction() != null) {
                        toolCallIdToFunctionName.put(toolCall.getId(), toolCall.getFunction().getName());
                    }
                }
            }
        }
        return toolCallIdToFunctionName;
    }


    private List<GeminiFunctionDeclaration> buildFunctionDeclarations(EntityMaid maid) {
        List<GeminiFunctionDeclaration> declarations = new ArrayList<>();

        FunctionCallRegister.getFunctionCalls().forEach((key, value) -> {
            if (!value.addToChatCompletion(maid, null)) {
                return;
            }
            String id = value.getId();
            String description = value.getDescription(maid);
            ObjectParameter root = ObjectParameter.create();
            Parameter parameter = value.addParameters(root, maid);

            // Use TLM's Parameter directly - it serializes to JSON Schema format via GSON
            declarations.add(GeminiFunctionDeclaration.create(id, description, parameter));
        });

        return declarations;
    }

    private void handle(List<LLMMessage> messages, LLMConfig config, ResponseCallback<ResponseChat> callback,
                        HttpResponse<String> response, Throwable throwable, HttpRequest request) {
        this.<GeminiResponse>handleResponse(callback, response, throwable, request, geminiResponse -> {
            if (TouhouLittleMaid.DEBUG) {
                MaidAgent.LOGGER.debug("Gemini Response: {}", GSON.toJson(geminiResponse));
            }

            // token counting
            if (geminiResponse.getUsageMetadata() != null) {
                int totalTokens = geminiResponse.getUsageMetadata().getTotalTokenCount();
                if (totalTokens > 0 && config.maid().getOwner() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getCapability(ChatTokensCapabilityProvider.CHAT_TOKENS_CAP)
                            .ifPresent(tokens -> tokens.addCount(totalTokens));
                }
            }

            // Check if response has candidates
            if (geminiResponse.getCandidates() == null || geminiResponse.getCandidates().isEmpty()) {
                // Empty candidates can happen after function call results
                // Check if this is a follow-up to a function call by looking at message history
                boolean hadFunctionCall = messages.stream().anyMatch(m -> m.role() == Role.TOOL);
                if (hadFunctionCall) {
                    // Provide a fallback response using the last tool message
                    String lastToolMessage = messages.stream()
                            .filter(m -> m.role() == Role.TOOL)
                            .reduce((first, second) -> second)
                            .map(LLMMessage::message)
                            .orElse("Task completed.");
                    callback.onSuccess(new ResponseChat(lastToolMessage));
                    return;
                }
                callback.onFailure(request, new Throwable("No candidates in response"),
                        ErrorCode.CHAT_CHOICE_IS_EMPTY);
                return;
            }

            // Check for function calls
            if (geminiResponse.hasFunctionCall()) {
                GeminiPart.GeminiFunctionCall fc = geminiResponse.getFirstFunctionCall();
                if (fc != null) {
                    Message compatMessage = createCompatibleMessage(fc);
                    ((LLMCallback) callback).onFunctionCall(compatMessage, messages, config, this);
                }
            } else {
                String text = geminiResponse.getText();
                if (StringUtils.isBlank(text)) {
                    // Empty text can happen after function call results
                    // Check if this is a follow-up to a function call
                    boolean hadFunctionCall = messages.stream().anyMatch(m -> m.role() == Role.TOOL);
                    if (hadFunctionCall) {
                        // Provide a fallback response using the last tool message
                        String lastToolMessage = messages.stream()
                                .filter(m -> m.role() == Role.TOOL)
                                .reduce((first, second) -> second)
                                .map(LLMMessage::message)
                                .orElse("Task completed.");
                        callback.onSuccess(new ResponseChat(lastToolMessage));
                        return;
                    }
                    callback.onSuccess(new ResponseChat(StringUtils.EMPTY, StringUtils.EMPTY));
                    return;
                }
                callback.onSuccess(new ResponseChat(text));
            }
        }, GeminiResponse.class);
    }


    /**
     * Create a Message compatible with the OpenAI format for function call handling.
     */
    private Message createCompatibleMessage(GeminiPart.GeminiFunctionCall functionCall) {
        return new GeminiCompatibleMessage(functionCall);
    }

    /**
     * Adapter class to make Gemini function calls compatible with OpenAI Message format.
     */
    public static class GeminiCompatibleMessage extends Message {
        private final List<ToolCall> toolCalls;

        public GeminiCompatibleMessage(GeminiPart.GeminiFunctionCall functionCall) {
            this.toolCalls = Lists.newArrayList();
            String id = "call_gemini_" + System.currentTimeMillis();
            String args = functionCall.getArgsAsJson();
            FunctionToolCall ftc = new FunctionToolCall(functionCall.getName(), args);
            this.toolCalls.add(new ToolCall(id, ftc));
        }

        @Override
        public boolean hasToolCall() {
            return true;
        }

        @Override
        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        @Override
        public String getContent() {
            return null;
        }
    }
}

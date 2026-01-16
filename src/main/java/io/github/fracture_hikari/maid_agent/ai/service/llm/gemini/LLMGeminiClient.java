package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini;

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
import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request.*;
import io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.response.GeminiResponse;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
            MaidAgent.LOGGER.info("Chat messages: {}", messages);
            MaidAgent.LOGGER.info("Gemini Request: {}", requestJson);
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
                request.addFunctionResponse(message.toolCallId(), message.message());
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
                MaidAgent.LOGGER.info("Gemini Response: {}", GSON.toJson(geminiResponse));
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

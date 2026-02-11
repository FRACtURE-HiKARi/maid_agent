package com.github.fracture_hikari.maid_agent.ai.service.llm.claude;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
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
import com.github.fracture_hikari.maid_agent.ai.service.llm.claude.request.*;
import com.github.fracture_hikari.maid_agent.ai.service.llm.claude.response.ClaudeResponse;
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
 * Claude API client using HTTP requests.
 * Implements the LLMClient interface following the same pattern as Gemini client.
 */
public final class LLMClaudeClient implements LLMClient {
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final LLMClaudeSite site;

    public LLMClaudeClient(HttpClient httpClient, LLMClaudeSite site) {
        this.httpClient = httpClient;
        this.site = site;
    }

    @Override
    public void chat(List<LLMMessage> messages, LLMConfig config, ResponseCallback<ResponseChat> callback) {
        MaidAgent.LOGGER.debug("Claude client chat() called.");

        String model = config.model();
        double temperature = config.temperature();
        int maxTokens = config.maxTokens();
        EntityMaid maid = config.maid();
        ChatType chatType = config.chatType();

        // Build URL - Claude uses POST to /v1/messages
        URI url = URI.create(this.site.url());

        // Build the request body
        ClaudeRequest claudeRequest = buildRequest(messages, model, temperature, maxTokens, maid, chatType);

        String requestJson = GSON.toJson(claudeRequest);

        if (TouhouLittleMaid.DEBUG) {
            MaidAgent.LOGGER.debug("Claude Request URL: {}", url);
            MaidAgent.LOGGER.debug("Claude Request: {}", requestJson);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .header("x-api-key", this.site.secretKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(MAX_TIMEOUT)
                .uri(url);

        this.site.headers().forEach(builder::header);
        HttpRequest httpRequest = builder.build();

        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) ->
                        handle(messages, config, callback, response, throwable, httpRequest));
    }

    private ClaudeRequest buildRequest(List<LLMMessage> messages, String model, double temperature,
                                        int maxTokens, EntityMaid maid, ChatType chatType) {
        ClaudeRequest request = ClaudeRequest.create()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature);

        // Process messages
        for (LLMMessage message : messages) {
            if (message.role() == Role.SYSTEM) {
                // Claude uses a separate system field
                request.system(message.message());
            } else if (message.role() == Role.USER) {
                request.addUserMessage(message.message());
            } else if (message.role() == Role.ASSISTANT) {
                if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                    request.addAssistantMessage(message.message());
                } else {
                    // Handle assistant message with tool calls
                    List<ClaudeContent> contents = new ArrayList<>();
                    if (StringUtils.isNotBlank(message.message())) {
                        contents.add(ClaudeContent.text(message.message()));
                    }
                    for (ToolCall toolCall : message.toolCalls()) {
                        if (toolCall.getFunction() != null) {
                            // Parse arguments JSON to Object
                            Object input = GSON.fromJson(toolCall.getFunction().getArguments(), Object.class);
                            contents.add(ClaudeContent.toolUse(
                                    toolCall.getId(),
                                    toolCall.getFunction().getName(),
                                    input));
                        }
                    }
                    request.addMessage(ClaudeMessage.assistantWithContent(contents));
                }
            } else if (message.role() == Role.TOOL) {
                request.addToolResult(message.toolCallId(), message.message());
            }
        }

        // Add tools if enabled
        if (AIConfig.FUNCTION_CALL_ENABLED.get() && chatType != ChatType.AUTO_GEN_SETTING) {
            List<ClaudeTool> tools = buildTools(maid);
            for (ClaudeTool tool : tools) {
                request.addTool(tool);
            }
        }

        return request;
    }

    private List<ClaudeTool> buildTools(EntityMaid maid) {
        List<ClaudeTool> tools = new ArrayList<>();

        FunctionCallRegister.getFunctionCalls().forEach((key, value) -> {
            if (!value.addToChatCompletion(maid, null)) {
                return;
            }
            String id = value.getId();
            String description = value.getDescription(maid);
            ObjectParameter root = ObjectParameter.create();
            Parameter parameter = value.addParameters(root, maid);

            tools.add(ClaudeTool.create(id, description, parameter));
        });

        return tools;
    }

    private void handle(List<LLMMessage> messages, LLMConfig config, ResponseCallback<ResponseChat> callback,
                        HttpResponse<String> response, Throwable throwable, HttpRequest request) {
        this.<ClaudeResponse>handleResponse(callback, response, throwable, request, claudeResponse -> {
            if (TouhouLittleMaid.DEBUG) {
                MaidAgent.LOGGER.debug("Claude Response: {}", GSON.toJson(claudeResponse));
            }

            // Handle token counting
            if (claudeResponse.getUsage() != null) {
                int totalTokens = claudeResponse.getUsage().getTotalTokens();
                if (totalTokens > 0 && config.maid().getOwner() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getCapability(ChatTokensCapabilityProvider.CHAT_TOKENS_CAP)
                            .ifPresent(tokens -> tokens.addCount(totalTokens));
                }
            }

            // Check for tool use
            if (claudeResponse.hasToolUse()) {
                ClaudeContent toolUse = claudeResponse.getFirstToolUse();
                if (toolUse != null) {
                    Message compatMessage = createCompatibleMessage(toolUse);
                    ((LLMCallback) callback).onFunctionCall(compatMessage, messages, config, this);
                    return;
                }
            }

            // Handle text response
            String text = claudeResponse.getText();
            if (StringUtils.isBlank(text)) {
                callback.onSuccess(new ResponseChat(StringUtils.EMPTY, StringUtils.EMPTY));
            } else {
                callback.onSuccess(new ResponseChat(text));
            }
        }, ClaudeResponse.class);
    }

    /**
     * Create a Message compatible with the OpenAI format for function call handling.
     */
    private Message createCompatibleMessage(ClaudeContent toolUse) {
        return new ClaudeCompatibleMessage(toolUse);
    }

    /**
     * Adapter class to make Claude tool_use compatible with OpenAI Message format.
     */
    public static class ClaudeCompatibleMessage extends Message {
        private final List<ToolCall> toolCalls;

        public ClaudeCompatibleMessage(ClaudeContent toolUse) {
            this.toolCalls = Lists.newArrayList();
            String id = toolUse.getId() != null ? toolUse.getId() : "call_claude_" + System.currentTimeMillis();
            String name = toolUse.getName() != null ? toolUse.getName() : "unknown";
            String args = toolUse.getInputAsJson();
            FunctionToolCall ftc = new FunctionToolCall(name, args);
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

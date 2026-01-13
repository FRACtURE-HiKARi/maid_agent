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
import com.google.genai.Client;
import com.google.genai.types.*;
import io.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Gemini API client using Google's official GenAI SDK.
 * Implements the LLMClient interface following the OpenAI client pattern.
 */
public final class LLMGeminiClient implements LLMClient {

    private final LLMGeminiSite site;
    private final Client client;

    public LLMGeminiClient(LLMGeminiSite site) {
        this.site = site;
        // Initialize the Google GenAI client with API key
        this.client = Client.builder()
                .apiKey(site.secretKey())
                .build();
    }

    @Override
    public void chat(List<LLMMessage> messages, LLMConfig config, ResponseCallback<ResponseChat> callback) {
        String model = config.model();
        double temperature = config.temperature();
        int maxTokens = config.maxTokens();
        EntityMaid maid = config.maid();
        ChatType chatType = config.chatType();

        // Run the API call asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Build contents from messages
                List<Content> contents = buildContents(messages);

                // Build generation config
                GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                        .temperature((float) temperature)
                        .maxOutputTokens(maxTokens);

                // Add system instruction if present
                String systemInstruction = extractSystemInstruction(messages);
                if (systemInstruction != null) {
                    configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemInstruction)));
                }

                // Add function call tools if enabled
                if (AIConfig.FUNCTION_CALL_ENABLED.get() && chatType != ChatType.AUTO_GEN_SETTING) {
                    List<Tool> tools = buildFunctionTools(maid);
                    if (!tools.isEmpty()) {
                        configBuilder.tools(tools);
                    }
                }

                GenerateContentConfig genConfig = configBuilder.build();

                if (TouhouLittleMaid.DEBUG) {
                    MaidAgent.LOGGER.info("Gemini Request - Model: {}, Contents: {}", model, contents.size());
                }

                // Make the API call using the SDK
                GenerateContentResponse response = client.models.generateContent(model, contents, genConfig);

                if (TouhouLittleMaid.DEBUG) {
                    MaidAgent.LOGGER.info("Gemini Response: {}", response.text());
                }

                // Handle token counting
                if (response.usageMetadata().isPresent()) {
                    int totalTokens = response.usageMetadata().get().totalTokenCount().orElse(0);
                    if (totalTokens > 0 && config.maid().getOwner() instanceof ServerPlayer serverPlayer) {
                        serverPlayer.getCapability(ChatTokensCapabilityProvider.CHAT_TOKENS_CAP)
                                .ifPresent(tokens -> tokens.addCount(totalTokens));
                    }
                }

                // Process the response
                if (response.candidates().isEmpty() || response.candidates().isEmpty()) {
                    callback.onFailure(null, new Throwable("No candidates in response"),
                            ErrorCode.CHAT_CHOICE_IS_EMPTY);
                    return;
                }

                List<Candidate> candidateList = response.candidates().get();
                Content content = candidateList.get(0).content().orElse(null);

                if (content == null || content.parts().isEmpty() || content.parts().isEmpty()) {
                    callback.onSuccess(new ResponseChat(StringUtils.EMPTY, StringUtils.EMPTY));
                    return;
                }

                // Check for function calls
                for (Part part : content.parts().get()) {
                    if (part.functionCall().isPresent()) {
                        FunctionCall fc = part.functionCall().get();
                        Message compatMessage = createCompatibleMessage(fc);
                        ((LLMCallback) callback).onFunctionCall(compatMessage, messages, config, this);
                        return;
                    }
                }

                // Handle text response
                String text = response.text();
                if (StringUtils.isBlank(text)) {
                    callback.onSuccess(new ResponseChat(StringUtils.EMPTY, StringUtils.EMPTY));
                } else {
                    callback.onSuccess(new ResponseChat(text));
                }

            } catch (Exception e) {
                MaidAgent.LOGGER.error("Gemini API error", e);
                callback.onFailure(null, e, ErrorCode.REQUEST_SENDING_ERROR);
            }
        });
    }

    /**
     * Extract system instruction from messages.
     */
    private String extractSystemInstruction(List<LLMMessage> messages) {
        for (LLMMessage message : messages) {
            if (message.role() == Role.SYSTEM) {
                return message.message();
            }
        }
        return null;
    }

    /**
     * Build Content list from LLMMessage list.
     */
    private List<Content> buildContents(List<LLMMessage> messages) {
        List<Content> contents = new ArrayList<>();

        for (LLMMessage message : messages) {
            // Skip system messages (handled separately as systemInstruction)
            if (message.role() == Role.SYSTEM) {
                continue;
            }

            String role = mapRole(message.role());

            if (message.role() == Role.USER) {
                contents.add(Content.builder()
                        .role(role)
                        .parts(List.of(Part.fromText(message.message())))
                        .build());
            } else if (message.role() == Role.ASSISTANT) {
                if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                    contents.add(Content.builder()
                            .role(role)
                            .parts(List.of(Part.fromText(message.message())))
                            .build());
                } else {
                    // Handle assistant message with tool calls
                    List<Part> parts = new ArrayList<>();
                    for (ToolCall toolCall : message.toolCalls()) {
                        if (toolCall.getFunction() != null) {
                            parts.add(Part.builder()
                                    .functionCall(FunctionCall.builder()
                                            .name(toolCall.getFunction().getName())
                                            .args(Map.of("args", toolCall.getFunction().getArguments()))
                                            .build())
                                    .build());
                        }
                    }
                    contents.add(Content.builder().role(role).parts(parts).build());
                }
            } else if (message.role() == Role.TOOL) {
                // Tool response
                contents.add(Content.builder()
                        .role("user")
                        .parts(List.of(Part.builder()
                                .functionResponse(FunctionResponse.builder()
                                        .name(message.toolCallId())
                                        .response(Map.of("result", message.message()))
                                        .build())
                                .build()))
                        .build());
            }
        }

        return contents;
    }

    /**
     * Map LLMMessage role to Gemini role.
     */
    private String mapRole(Role role) {
        return switch (role) {
            case USER, TOOL -> "user";
            case ASSISTANT -> "model";
            case SYSTEM -> "user"; // System handled separately
        };
    }

    /**
     * Build function tools from registered function calls.
     */
    private List<Tool> buildFunctionTools(EntityMaid maid) {
        List<FunctionDeclaration> declarations = new ArrayList<>();

        FunctionCallRegister.getFunctionCalls().forEach((key, value) -> {
            if (!value.addToChatCompletion(maid, null)) {
                return;
            }
            String id = value.getId();
            String description = value.getDescription(maid);
            ObjectParameter root = ObjectParameter.create();
            Parameter parameter = value.addParameters(root, maid);

            // Convert parameter to Schema
            Schema schema = convertParameterToSchema(parameter);

            FunctionDeclaration declaration = FunctionDeclaration.builder()
                    .name(id)
                    .description(description)
                    .parameters(schema)
                    .build();

            declarations.add(declaration);
        });

        if (declarations.isEmpty()) {
            return List.of();
        }

        return List.of(Tool.builder().functionDeclarations(declarations).build());
    }

    /**
     * Convert TLM Parameter to Gemini Schema.
     */
    private Schema convertParameterToSchema(Parameter parameter) {
        // Basic conversion - may need enhancement based on parameter types
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .build();
    }

    /**
     * Create a Message compatible with the OpenAI format for function call
     * handling.
     */
    private Message createCompatibleMessage(FunctionCall functionCall) {
        return new GeminiCompatibleMessage(functionCall);
    }

    /**
     * Adapter class to make Gemini function calls compatible with OpenAI Message
     * format.
     */
    public static class GeminiCompatibleMessage extends Message {
        private final List<ToolCall> toolCalls;

        public GeminiCompatibleMessage(FunctionCall functionCall) {
            this.toolCalls = Lists.newArrayList();
            String id = "call_gemini_" + System.currentTimeMillis();
            String args = functionCall.args().map(Object::toString).orElse("{}");
            FunctionToolCall ftc = new FunctionToolCall(
                    functionCall.name().orElse("unknown"),
                    args);
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

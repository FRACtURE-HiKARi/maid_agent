package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Entrance function for LLM to execute any internal tool.
 * Registered with TLM - routes to internal AgenticTool handlers.
 */
public class RunToolFunction implements IFunctionCall<RunToolFunction.Params> {
    public static final String ID = "run_tool";
    
    private final AgenticToolRegistry registry;

    public RunToolFunction(AgenticToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription(EntityMaid maid) {
        return """
        Execute a agentic tool by name. Use explore_tools first to discover available tools and their parameters.
        All agentic tools must be called by this entry point, and other tools that is not listed in "explore tools"
        are not designed for agentic use.
        """;
    }

    @Override
    public Parameter addParameters(ObjectParameter root, EntityMaid maid) {
        StringParameter toolParam = StringParameter.create();
        toolParam.setDescription("Name of the tool to execute");
        root.addProperties("tool", toolParam);
        
        // params is a nested object - we'll use a raw JsonObject
        ObjectParameter paramsParam = ObjectParameter.create();
        paramsParam.setDescription("Parameters for the tool (depends on which tool)");
        root.addProperties("params", paramsParam);
        
        return root;
    }

    @Override
    public Codec<Params> codec() {
        // Use PASSTHROUGH for params to accept any JSON structure
        return RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("tool").forGetter(Params::tool),
            Codec.PASSTHROUGH.optionalFieldOf("params", new com.mojang.serialization.Dynamic<>(
                    com.mojang.serialization.JsonOps.INSTANCE, new JsonObject()))
                .xmap(
                    dynamic -> {
                        // Convert Dynamic to JsonObject
                        JsonElement elem = dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue();
                        if (elem.isJsonObject()) {
                            return elem.getAsJsonObject();
                        }
                        return new JsonObject();
                    },
                    jsonObj -> new com.mojang.serialization.Dynamic<>(
                        com.mojang.serialization.JsonOps.INSTANCE, jsonObj)
                )
                .forGetter(Params::params)
        ).apply(instance, Params::new));
    }

    @Override
    public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) {
        return true;  // Always available
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid, String toolCallId) {
        // Look up the tool
        AgenticTool<?> tool = registry.get(params.tool());
        if (tool == null) {
            return new ToolResponse("Unknown tool: " + params.tool() + 
                ". Use explore_tools(action='list_categories') to discover available tools.");
        }

        // Policy check
        if (!tool.getPolicy().isAllowed(tool.getId(), maid)) {
            return new ToolResponse("Tool not available: " + tool.getId() + 
                ". Required conditions not met (e.g., missing mod dependency).");
        }

        // Execute tool - JSON parsing happens on main thread (acceptable overhead)
        return tool.executeFromJson(params.params(), maid, toolCallId);
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        return onToolCall(params, maid, "");
    }

    public record Params(String tool, JsonObject params) {}
}

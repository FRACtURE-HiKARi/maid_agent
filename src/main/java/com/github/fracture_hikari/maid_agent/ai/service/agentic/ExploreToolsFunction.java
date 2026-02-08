package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Entrance function for LLM to discover available tools.
 * Registered with TLM - provides hierarchical tool exploration.
 */
public class ExploreToolsFunction implements IFunctionCall<ExploreToolsFunction.Params> {
    public static final String ID = "explore_tools";
    
    private final AgenticToolRegistry registry;

    public ExploreToolsFunction(AgenticToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription(EntityMaid maid) {
        // Build category list dynamically
        StringBuilder categories = new StringBuilder();
        for (ToolCategory cat : ToolCategory.values()) {
            if (categories.length() > 0) categories.append(", ");
            categories.append(cat.getId()).append(" (").append(cat.getDescription()).append(")");
        }
        
        return "Explore available agentic tools. Categories: " + categories + ". " +
               "Use action='describe_category' with category name to see tools, " +
               "or action='describe_tool' with tool name to get full schema.";
    }

    @Override
    public Parameter addParameters(ObjectParameter root, EntityMaid maid) {
        StringParameter actionParam = StringParameter.create();
        actionParam.setDescription("Action to perform: list_categories, describe_category, or describe_tool");
        actionParam.addEnumValues("list_categories", "describe_category", "describe_tool");
        root.addProperties("action", actionParam);
        
        StringParameter categoryParam = StringParameter.create();
        categoryParam.setDescription("Category name (required for describe_category)");
        root.addProperties("category", categoryParam);
        
        StringParameter toolParam = StringParameter.create();
        toolParam.setDescription("Tool name (required for describe_tool)");
        root.addProperties("tool", toolParam);
        
        return root;
    }

    @Override
    public Codec<Params> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("action").forGetter(Params::action),
            Codec.STRING.optionalFieldOf("category", "").forGetter(Params::category),
            Codec.STRING.optionalFieldOf("tool", "").forGetter(Params::tool)
        ).apply(instance, Params::new));
    }

    @Override
    public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) {
        return true;  // Always available
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        return switch (params.action()) {
            case "list_categories" -> listCategories();
            case "describe_category" -> describeCategory(params.category(), maid);
            case "describe_tool" -> describeTool(params.tool(), maid);
            default -> new ToolResponse("Unknown action: " + params.action() + 
                ". Use: list_categories, describe_category, or describe_tool");
        };
    }

    private ToolResponse listCategories() {
        List<ToolCategory> categories = registry.getCategories();
        if (categories.isEmpty()) {
            return new ToolResponse("No tool categories available.");
        }
        
        StringBuilder sb = new StringBuilder("Available categories:\n");
        for (ToolCategory cat : categories) {
            int count = registry.getToolCount(cat);
            sb.append("- ").append(cat.getId())
              .append(": ").append(cat.getDescription())
              .append(" (").append(count).append(" tools)\n");
        }
        return new ToolResponse(sb.toString().trim());
    }

    private ToolResponse describeCategory(String categoryId, EntityMaid maid) {
        if (categoryId == null || categoryId.isEmpty()) {
            return new ToolResponse("Error: category parameter required for describe_category action.");
        }
        
        ToolCategory category = ToolCategory.fromId(categoryId);
        if (category == null) {
            return new ToolResponse("Unknown category: " + categoryId + 
                ". Use list_categories to see available categories.");
        }
        
        List<AgenticTool<?>> tools = registry.getToolsInCategory(category);
        if (tools.isEmpty()) {
            return new ToolResponse("No tools in category: " + categoryId);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Tools in '").append(categoryId).append("':\n");
        for (AgenticTool<?> tool : tools) {
            sb.append("- ").append(tool.getId()).append(": ").append(tool.getShortDescription());
            List<String> prereqs = tool.getPrerequisites();
            if (!prereqs.isEmpty()) {
                sb.append(" (requires: ").append(String.join(", ", prereqs)).append(")");
            }
            sb.append("\n");
        }
        return new ToolResponse(sb.toString().trim());
    }

    private ToolResponse describeTool(String toolId, EntityMaid maid) {
        if (toolId == null || toolId.isEmpty()) {
            return new ToolResponse("Error: tool parameter required for describe_tool action.");
        }
        
        AgenticTool<?> tool = registry.get(toolId);
        if (tool == null) {
            return new ToolResponse("Unknown tool: " + toolId + 
                ". Use describe_category to see available tools.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(tool.getId()).append("\n");
        sb.append("Description: ").append(tool.getFullDescription(maid)).append("\n");
        
        // Include parameter schema
        String schema = tool.getParameterSchema(maid);
        if (!schema.isEmpty()) {
            sb.append(schema);
        }
        
        List<String> prereqs = tool.getPrerequisites();
        if (!prereqs.isEmpty()) {
            sb.append("Prerequisites: ").append(String.join(", ", prereqs)).append("\n");
        }
        
        sb.append("\nCall: run_tool(tool=\"").append(tool.getId())
          .append("\", params={...})");
        
        return new ToolResponse(sb.toString().trim());
    }

    public record Params(String action, String category, String tool) {}
}

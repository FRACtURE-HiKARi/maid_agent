package com.github.fracture_hikari.maid_agent.ai.service.agentic;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

import java.util.List;

/**
 * Adapter that wraps an existing IFunctionCall as an AgenticTool.
 * Uses builder pattern for fluent configuration.
 *
 * @param <T> The parameter type
 */
public class FunctionCallAdapter<T> implements AgenticTool<T> {
    private final IFunctionCall<T> delegate;
    private final String id;
    private final ToolCategory category;
    private final String shortDescription;
    private final List<String> prerequisites;
    private final ToolPolicy policy;

    private FunctionCallAdapter(Builder<T> builder) {
        this.delegate = builder.delegate;
        this.id = builder.delegate.getId();
        this.category = builder.category;
        this.shortDescription = builder.shortDescription;
        this.prerequisites = builder.prerequisites;
        this.policy = builder.policy;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ToolCategory getCategory() {
        return category;
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }

    @Override
    public String getFullDescription(EntityMaid maid) {
        return delegate.getDescription(maid);
    }

    @Override
    public List<String> getPrerequisites() {
        return prerequisites;
    }

    @Override
    public ToolPolicy getPolicy() {
        return policy;
    }

    @Override
    public String getParameterSchema(EntityMaid maid) {
        // Build schema from delegate's addParameters
        var root = ObjectParameter.create();
        delegate.addParameters(root, maid);
        return formatParameterSchema(root);
    }

    private String formatParameterSchema(ObjectParameter root) {
        // Serialize to JSON and parse it to extract properties
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject json = gson.toJsonTree(root).getAsJsonObject();
        
        StringBuilder sb = new StringBuilder();
        sb.append("Parameters:\n");
        
        com.google.gson.JsonObject properties = json.getAsJsonObject("properties");
        com.google.gson.JsonArray required = json.getAsJsonArray("required");
        
        if (properties == null || properties.size() == 0) {
            sb.append("  (none)\n");
            return sb.toString();
        }
        
        java.util.Set<String> requiredSet = new java.util.HashSet<>();
        if (required != null) {
            for (var elem : required) {
                requiredSet.add(elem.getAsString());
            }
        }
        
        for (var entry : properties.entrySet()) {
            String name = entry.getKey();
            com.google.gson.JsonObject param = entry.getValue().getAsJsonObject();
            boolean isRequired = requiredSet.contains(name);
            
            String type = param.has("type") ? param.get("type").getAsString() : "object";
            String desc = param.has("description") ? param.get("description").getAsString() : null;
            
            sb.append("  - ").append(name);
            sb.append(" (").append(type);
            if (isRequired) {
                sb.append(", required");
            }
            sb.append(")");
            
            if (desc != null && !desc.isEmpty()) {
                // Truncate long descriptions
                if (desc.length() > 80) {
                    desc = desc.substring(0, 77) + "...";
                }
                sb.append(": ").append(desc);
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    @Override
    public Codec<T> codec() {
        return delegate.codec();
    }

    @Override
    public AgenticResponse execute(T params, EntityMaid maid, ToolContext ctx) {
        var response = delegate.onToolCall(params, maid, ctx.toolCallId());
        return AgenticResponse.fromToolResponse(response);
    }

    /**
     * Create a builder to wrap an IFunctionCall.
     */
    public static <T> Builder<T> wrap(IFunctionCall<T> fn) {
        return new Builder<>(fn);
    }

    /**
     * Builder for FunctionCallAdapter.
     */
    public static class Builder<T> {
        private final IFunctionCall<T> delegate;
        private ToolCategory category = ToolCategory.STATUS;
        private String shortDescription = "";
        private List<String> prerequisites = List.of();
        private ToolPolicy policy = ToolPolicy.always();

        private Builder(IFunctionCall<T> delegate) {
            this.delegate = delegate;
        }

        public Builder<T> category(ToolCategory category) {
            this.category = category;
            return this;
        }

        public Builder<T> shortDescription(String shortDescription) {
            this.shortDescription = shortDescription;
            return this;
        }

        public Builder<T> prerequisites(String... prerequisites) {
            this.prerequisites = List.of(prerequisites);
            return this;
        }

        public Builder<T> policy(ToolPolicy policy) {
            this.policy = policy;
            return this;
        }

        public FunctionCallAdapter<T> build() {
            return new FunctionCallAdapter<>(this);
        }
    }
}

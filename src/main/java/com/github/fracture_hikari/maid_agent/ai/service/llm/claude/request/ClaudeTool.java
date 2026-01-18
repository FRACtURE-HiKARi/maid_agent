package com.github.fracture_hikari.maid_agent.ai.service.llm.claude.request;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Claude API Tool definition for function calling.
 */
public class ClaudeTool {
    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("input_schema")
    @Nullable
    private Parameter inputSchema;

    public ClaudeTool(String name, String description, Parameter inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public static ClaudeTool create(String name, String description, Parameter inputSchema) {
        return new ClaudeTool(name, description, inputSchema);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public Parameter getInputSchema() {
        return inputSchema;
    }
}

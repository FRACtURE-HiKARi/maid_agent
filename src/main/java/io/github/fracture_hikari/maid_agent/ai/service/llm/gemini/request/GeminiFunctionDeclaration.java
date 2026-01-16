package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/**
 * Gemini API FunctionDeclaration for defining callable functions.
 * Uses TLM's Parameter schema system for proper JSON Schema generation.
 */
public class GeminiFunctionDeclaration {
    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("parameters")
    @Nullable
    private Parameter parameters;

    public GeminiFunctionDeclaration(String name, String description, Parameter parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static GeminiFunctionDeclaration create(String name, String description) {
        return new GeminiFunctionDeclaration(name, description, null);
    }

    public static GeminiFunctionDeclaration create(String name, String description, Parameter parameters) {
        return new GeminiFunctionDeclaration(name, description, parameters);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public Parameter getParameters() {
        return parameters;
    }
}


package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.request;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.google.gson.annotations.SerializedName;

/**
 * Function declaration for Gemini API function calling.
 * Defines a function the model can invoke.
 */
public class GeminiFunctionDeclaration {
    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("parameters")
    private Parameter parameters;

    public static GeminiFunctionDeclaration create() {
        return new GeminiFunctionDeclaration();
    }

    public GeminiFunctionDeclaration setName(String name) {
        this.name = name;
        return this;
    }

    public GeminiFunctionDeclaration setDescription(String description) {
        this.description = description;
        return this;
    }

    public GeminiFunctionDeclaration setParameters(Parameter parameters) {
        this.parameters = parameters;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Parameter getParameters() {
        return parameters;
    }
}

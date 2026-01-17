package io.github.fracture_hikari.maid_agent.maid;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.FunctionCallRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.ai.service.function.ItemSearchFunction;
import io.github.fracture_hikari.maid_agent.ai.service.function.RecipeSearchFunction;
import io.github.fracture_hikari.maid_agent.ai.service.llm.claude.LLMClaudeSite;
import io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.LLMGeminiSite;
import io.github.fracture_hikari.maid_agent.maid.task.AgentTask;

/**
 * Extension class that hooks into TouhouLittleMaid.
 * This class is automatically discovered via the @LittleMaidExtension
 * annotation.
 * TouhouLittleMaid scans for classes with this annotation at startup.
 */
@LittleMaidExtension
public class MaidExtension implements ILittleMaid {
    /**
     * Register custom maid tasks here.
     * Called by TouhouLittleMaid during initialization.
     */
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new AgentTask());
    }

    @Override
    public void registerAIChatSerializer(SerializerRegister register) {
        MaidAgent.LOGGER.info("Registering Gemini LLM serializer...");
        register.register(ServiceType.LLM, LLMGeminiSite.API_TYPE, new LLMGeminiSite.Serializer());
        
        MaidAgent.LOGGER.info("Registering Claude LLM serializer...");
        register.register(ServiceType.LLM, LLMClaudeSite.API_TYPE, new LLMClaudeSite.Serializer());
    }

    @Override
    public void registerAIFunctionCall(FunctionCallRegister register) {
        MaidAgent.LOGGER.info("Registering JEI function calls...");
        register.register(new ItemSearchFunction());
        register.register(new RecipeSearchFunction());
    }
}


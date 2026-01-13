package io.github.fracture_hikari.maid_agent.maid;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import io.github.fracture_hikari.maid_agent.ai.service.llm.gemini.LLMGeminiSite;
import io.github.fracture_hikari.maid_agent.maid.task.AgentTask;
import org.apache.logging.log4j.Logger;
import io.github.fracture_hikari.maid_agent.MaidAgent;

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
    }

    // You can override more methods from ILittleMaid to add:
    // - bindMaidBauble() - Custom baubles/accessories
    // - addMaidBackpack() - Custom backpack types
    // - addExtraMaidBrain() - Extra memory modules for AI state
    // - registerTaskData() - Custom data storage on maids
    // - registerAIFunctionCall() - AI-driven function calls
    // - registerChatBubble() - Custom chat bubble types
    // - addMaidTips() - UI tooltip overlays

}

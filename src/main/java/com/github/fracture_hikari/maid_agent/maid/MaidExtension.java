package com.github.fracture_hikari.maid_agent.maid;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.FunctionCallRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.fracture_hikari.maid_agent.ai.service.function.*;
import com.github.fracture_hikari.maid_agent.ai.service.llm.claude.LLMClaudeSite;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.LLMGeminiSite;
import com.github.fracture_hikari.maid_agent.maid.task.AgentTask;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;

/**
 * Extension class that hooks into TouhouLittleMaid.
 * This class is automatically discovered via the @LittleMaidExtension annotation.
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
        // Register our Agent task - behaviors are defined in AgentTask.createBrainTasks()
        manager.add(new AgentTask());
    }

    @Override
    public void registerAIChatSerializer(SerializerRegister register) {
        register.register(ServiceType.LLM, LLMGeminiSite.API_TYPE, new LLMGeminiSite.Serializer());
        register.register(ServiceType.LLM, LLMClaudeSite.API_TYPE, new LLMClaudeSite.Serializer());
    }

    @Override
    public void registerAIFunctionCall(FunctionCallRegister register) {
        register.register(new ItemSearchFunction());
        register.register(new StorageItemsFunction());
        register.register(new CraftItemFunction());
        register.register(new GetInventoryFunction());
        register.register(new GetNearbyStorageFunction());
        register.register(new GetTaskQueueFunction());
        //register.register(new GetFuelSourcesFunction());
        register.register(new ClearTasksFunction());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override
            public List<MemoryModuleType<?>> getExtraMemoryTypes() {
                // Register memory types for all maids (needed for brain to accept them)
                // Behaviors that USE these memories are registered in AgentTask
                return List.of(
                        MemoryModuleRegistry.TASK_QUEUE.get(),
                        MemoryModuleRegistry.VIEWED_STORAGE.get(),
                        MemoryModuleRegistry.PROCESSING_JOBS.get()
                );
            }
            
            // NOTE: Behaviors are NO LONGER registered globally here.
            // They are now registered in AgentTask.createBrainTasks()
            // so they only run when maid's task is set to "Agent".
        });
    }
}

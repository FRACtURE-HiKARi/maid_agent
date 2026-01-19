package com.github.fracture_hikari.maid_agent.maid;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.FunctionCallRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.datafixers.util.Pair;
import com.github.fracture_hikari.maid_agent.ai.service.function.*;
import com.github.fracture_hikari.maid_agent.ai.service.llm.claude.LLMClaudeSite;
import com.github.fracture_hikari.maid_agent.ai.service.llm.gemini.LLMGeminiSite;
import com.github.fracture_hikari.maid_agent.maid.behavior.StorageMoveTask;
import com.github.fracture_hikari.maid_agent.maid.behavior.StorageWorkTask;
import com.github.fracture_hikari.maid_agent.maid.task.AgentTask;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
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
        register.register(new RecipeSearchFunction());
        register.register(new StorageItemsFunction());
        register.register(new CraftItemFunction());
        register.register(new GetInventoryFunction());
        register.register(new GetNearbyStorageFunction());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override
            public List<MemoryModuleType<?>> getExtraMemoryTypes() {
                return List.of(
                        MemoryModuleRegistry.PENDING_TASK.get(),
                        MemoryModuleRegistry.TASK_QUEUE.get(),
                        MemoryModuleRegistry.VIEWED_STORAGE.get(),
                        MemoryModuleRegistry.TASK_RESULT.get()
                );
            }

            @Override
            public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getWorkBehaviors() {
                // These behaviors run during WORK activity for ALL maid task types
                // Priority 5 = runs after core behaviors but before random walk
                List<Pair<Integer, BehaviorControl<? super EntityMaid>>> list = new java.util.ArrayList<>();
                list.add(Pair.of(5, new StorageMoveTask()));
                list.add(Pair.of(5, new StorageWorkTask()));
                return list;
            }
        });
    }
}

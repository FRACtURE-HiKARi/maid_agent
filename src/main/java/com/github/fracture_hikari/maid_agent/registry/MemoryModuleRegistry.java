package com.github.fracture_hikari.maid_agent.registry;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/**
 * Registry for custom memory module types used by the maid agent.
 */
public class MemoryModuleRegistry {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULES =
            DeferredRegister.create(ForgeRegistries.MEMORY_MODULE_TYPES, MaidAgent.MODID);

    /**
     * Current pending task the maid should execute (fetch/store/craft).
     */
    public static final RegistryObject<MemoryModuleType<PendingTask>> PENDING_TASK =
            MEMORY_MODULES.register("pending_task",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /**
     * Memory of discovered storage locations and their cached contents.
     */
    public static final RegistryObject<MemoryModuleType<ViewedStorageMemory>> VIEWED_STORAGE =
            MEMORY_MODULES.register("viewed_storage",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /**
     * Result message from the last completed task.
     * Used to communicate back to the blocking LLM function call.
     */
    public static final RegistryObject<MemoryModuleType<String>> TASK_RESULT =
            MEMORY_MODULES.register("task_result",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static void register(IEventBus eventBus) {
        MEMORY_MODULES.register(eventBus);
    }
}

package com.github.fracture_hikari.maid_agent.registry;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.storage.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/**
 * Registry for custom memory module types used by the maid agent.
 * 
 * Memory flow:
 * - TASK_QUEUE: Set by StorageItemsFunction, consumed by StorageMoveTask/StorageWorkTask
 * - VIEWED_STORAGE: Set by GetNearbyStorageFunction, read by StorageItemsFunction
 */
public class MemoryModuleRegistry {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULES =
            DeferredRegister.create(ForgeRegistries.MEMORY_MODULE_TYPES, MaidAgent.MODID);

    /**
     * Queue of pending tasks supporting multiple LLM function calls.
     * 
     * Producer: StorageItemsFunction (creates TaskQueue with PendingTasks)
     * Consumer: StorageMoveTask, StorageWorkTask (execute tasks and notify LLM)
     */
    public static final RegistryObject<MemoryModuleType<TaskQueue>> TASK_QUEUE =
            MEMORY_MODULES.register("task_queue",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /**
     * Memory of discovered storage locations and their cached contents.
     * 
     * Producer: GetNearbyStorageFunction (scans and stores nearby storages)
     * Consumer: StorageItemsFunction (looks up storage by index)
     */
    public static final RegistryObject<MemoryModuleType<ViewedStorageMemory>> VIEWED_STORAGE =
            MEMORY_MODULES.register("viewed_storage",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static void register(IEventBus eventBus) {
        MEMORY_MODULES.register(eventBus);
    }
}

package com.github.fracture_hikari.maid_agent.util;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.maid.memory.*;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MemoryUtil {
    public static Optional<PendingTask> peekTask(EntityMaid maid) {
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        return queueOpt.map(TaskQueue::peek);
    }

    public static Set<ProcessingJob> getJobs(EntityMaid maid) {
        Optional<JobMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) return new HashSet<>();
        return memoryOpt.get().getActiveJobs();
    }

    public static Set<BlockPos> getCollectTarget(EntityMaid maid) {
        Optional<JobMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) return new HashSet<>();
        return memoryOpt.get().getOutputEndpoints();
    }

    public static void updateTasks(EntityMaid maid, PendingTask task, boolean success, String message) {
        Optional<TaskQueue> queueOpt = maid.getBrain().getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        queueOpt.ifPresent(queue -> {
            if (success) {
                MaidAgent.LOGGER.info("task {} success with {}", task, message);
            } else {
                MaidAgent.LOGGER.warn("task {} fails with {}", task, message);
            }
            queue.completeTask(task, success, message, task.getActualCount());
        });
    }

    public static void updateJobs(EntityMaid maid, boolean success, String message, ProcessingJob finished) {
        Optional<JobMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isEmpty()) return;
        JobMemory memory = memoryOpt.get();
        memory.completeJob(success, message, finished);
    }

    public static JobMemory getOrCreateJobMemory(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get())
                .orElseGet(() -> {
                    JobMemory memory = new JobMemory(maid);
                    maid.getBrain().setMemory(MemoryModuleRegistry.PROCESSING_JOBS.get(), memory);
                    return memory;
                });
    }

    public static ViewedStorageMemory getOrCreateViewedStorageMemory(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .orElseGet(() -> {
                    ViewedStorageMemory mem = new ViewedStorageMemory();
                    maid.getBrain().setMemory(MemoryModuleRegistry.VIEWED_STORAGE.get(), mem);
                    return mem;
                });
    }
}

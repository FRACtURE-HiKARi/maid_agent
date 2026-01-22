package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingMemory;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;

import java.util.Optional;

/**
 * LLM function to clear all pending tasks and processing jobs.
 * Use this when the maid is stuck or when you want to cancel ongoing work.
 */
public class ClearTasksFunction implements IFunctionCall<ClearTasksFunction.Params> {
    private static final String FUNCTION_ID = "clear_tasks";
    private static final String FUNCTION_DESC = """
            Clear all pending tasks and processing jobs.
            Use this to cancel ongoing work or when the maid is stuck.
            Returns the number of tasks and jobs that were cleared.""";

    @Override
    public String getId() {
        return FUNCTION_ID;
    }

    @Override
    public String getDescription(EntityMaid maid) {
        return FUNCTION_DESC;
    }

    @Override
    public Parameter addParameters(ObjectParameter root, EntityMaid maid) {
        // No parameters needed
        return root;
    }

    @Override
    public Codec<Params> codec() {
        return Codec.unit(new Params());
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        int tasksCleared = 0;
        int jobsCleared = 0;
        
        // Clear task queue
        Optional<TaskQueue> queueOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        if (queueOpt.isPresent()) {
            TaskQueue queue = queueOpt.get();
            tasksCleared = queue.size();
            queue.clear();
            MaidAgent.LOGGER.info("ClearTasksFunction: Cleared {} tasks from queue", tasksCleared);
        }
        
        // Clear processing jobs
        Optional<ProcessingMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        if (memoryOpt.isPresent()) {
            ProcessingMemory memory = memoryOpt.get();
            jobsCleared = memory.getActiveJobs().size();
            memory.clear();
            MaidAgent.LOGGER.info("ClearTasksFunction: Cleared {} processing jobs", jobsCleared);
        }
        
        // Clear movement memories
        maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
        
        if (tasksCleared == 0 && jobsCleared == 0) {
            return new ToolResponse("No tasks or jobs to clear. The maid was already idle.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Cleared:\n");
        if (tasksCleared > 0) {
            sb.append(String.format("- %d task(s) from queue\n", tasksCleared));
        }
        if (jobsCleared > 0) {
            sb.append(String.format("- %d processing job(s)\n", jobsCleared));
        }
        sb.append("The maid is now idle.");
        
        return new ToolResponse(sb.toString());
    }

    public record Params() {}
}

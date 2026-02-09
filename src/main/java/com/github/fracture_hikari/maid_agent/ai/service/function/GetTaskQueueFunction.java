package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.fracture_hikari.maid_agent.maid.memory.JobMemory;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;

import java.util.Optional;
import java.util.Set;

/**
 * LLM function to query the current task queue and processing jobs status.
 * Allows player to ask about progress of ongoing tasks.
 */
public class GetTaskQueueFunction implements IFunctionCall<GetTaskQueueFunction.Params> {
    private static final String FUNCTION_ID = "get_task_queue";
    private static final String FUNCTION_DESC = """
            Check the maid's task queue and processing jobs status.
            Use to see current progress when maid is busy. Don't poll repeatedly; you'll be notified when tasks complete.
            Returns current task, pending count, and completed summary.""";

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
        // Empty params - just return a constant
        return Codec.unit(new Params());
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        StringBuilder sb = new StringBuilder();
        boolean hasContent = false;
        
        // Task Queue section
        TaskQueue queue = TaskQueueHelper.getOrCreateQueue(maid);
        
        if (!queue.isEmpty()) {
            hasContent = true;
            
            sb.append("## Task Queue\n");
            
            // Current task
            PendingTask current = queue.peek();
            if (current != null) {
                sb.append(String.format("Current: %s %s\n",
                        current.getTypeName(),
                        ItemIdUtils.getId(current.getRequestedItem()).replace("minecraft:", "")
                ));
            }
            
            // Queue size
            int pending = queue.size() - 1; // Exclude current
            if (pending > 0) {
                sb.append(String.format("Pending: %d more task(s)\n", pending));
            }
            
            // Completed
            int completed = queue.getCompletedCount();
            if (completed > 0) {
                sb.append(String.format("Completed: %d task(s)\n", completed));
            }
        } else if (queue.getCompletedCount() > 0) {
            // Show batch summary if tasks completed but queue empty
            hasContent = true;
            sb.append(queue.getBatchSummary()).append("\n");
        }
        
        // Processing Jobs section
        Optional<JobMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get());
        
        if (memoryOpt.isPresent()) {
            JobMemory memory = memoryOpt.get();
            
            Set<ProcessingJob> activeJobs = memory.getActiveJobs();
            
            if (!activeJobs.isEmpty()) {
                hasContent = true;
                
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("## Processing Jobs\n");
                
                for (ProcessingJob job : activeJobs) {
                    sb.append(String.format("PENDING: %s at %s - Waiting for output / collection\n",
                            job.getExpectedOutput().getItem().getDescription().getString(),
                            job.getOutputEndpoint().toShortString()));
                }
            }
        }
        
        if (!hasContent) {
            return new ToolResponse("No tasks or processing jobs. The maid is idle.");
        }
        
        return new ToolResponse(sb.toString().trim());
    }

    public record Params() {}
}

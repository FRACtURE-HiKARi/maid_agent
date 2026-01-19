package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.memory.TaskQueue;

import java.util.Optional;

/**
 * LLM function to query the current task queue status.
 * Allows player to ask about progress of ongoing tasks.
 */
public class GetTaskQueueFunction implements IFunctionCall<GetTaskQueueFunction.Params> {
    private static final String FUNCTION_ID = "get_task_queue";
    private static final String FUNCTION_DESC = """
            Check the status of the maid's task queue.
            Returns pending tasks, current task, and recently completed results.
            Use this to check progress when the maid is busy.""";

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
        Optional<TaskQueue> queueOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.TASK_QUEUE.get());
        
        if (queueOpt.isEmpty() || queueOpt.get().isEmpty()) {
            // Check if there are completed results to report
            if (queueOpt.isPresent() && queueOpt.get().getCompletedCount() > 0) {
                return new ToolResponse("All tasks completed.\n" + queueOpt.get().getBatchSummary());
            }
            return new ToolResponse("No tasks in queue. The maid is idle.");
        }
        
        TaskQueue queue = queueOpt.get();
        StringBuilder sb = new StringBuilder();
        
        // Current task
        PendingTask current = queue.peek();
        if (current != null) {
            sb.append(String.format("Current task: %s %s (status: %s)\n",
                    current.getType().name().toLowerCase(),
                    current.getItemId().replace("minecraft:", ""),
                    current.getStatus().name().toLowerCase()));
        }
        
        // Queue size
        int pending = queue.size() - 1; // Exclude current
        if (pending > 0) {
            sb.append(String.format("Pending: %d more task(s) in queue\n", pending));
        }
        
        // Completed count in this batch
        int completed = queue.getCompletedCount();
        if (completed > 0) {
            sb.append(String.format("Completed: %d task(s) so far\n", completed));
        }
        
        // Total in batch
        sb.append(String.format("Total batch: %d task(s)", queue.getTotalTasksInBatch()));
        
        return new ToolResponse(sb.toString());
    }

    public record Params() {}
}

package io.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import io.github.fracture_hikari.maid_agent.storage.memory.PendingTask;

/**
 * LLM function to craft items at a workstation.
 * 
 * NON-BLOCKING: Queues task and returns immediately.
 * Crafting behaviors will process the task asynchronously.
 * On completion, AIChatCallback triggers new AI conversation with result.
 */
public class CraftItemFunction implements IFunctionCall<CraftItemFunction.Params> {
    private static final String FUNCTION_ID = "craft_item";
    private static final String FUNCTION_DESC = """
            Craft items at a nearby workstation (crafting table, furnace, etc.).
            The maid will walk to the workstation, gather ingredients, and craft.
            Requires ingredients in the maid's inventory.
            This queues the task - the maid will notify you when done.""";
    
    private static final String ITEM_PARAM_ID = "item_id";
    private static final String ITEM_PARAM_DESC = """
            item_id (string, required): The item to craft in namespace:name format.
            Example: minecraft:stick, minecraft:iron_pickaxe""";
    
    private static final String COUNT_PARAM_ID = "count";
    private static final String COUNT_PARAM_DESC = """
            count (integer, optional): Number of items to craft. Default: 1.""";

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
        StringParameter itemParam = StringParameter.create();
        itemParam.setDescription(ITEM_PARAM_DESC);
        itemParam.setMinLength(1);
        root.addProperties(ITEM_PARAM_ID, itemParam);

        IntegerParameter countParam = IntegerParameter.create();
        countParam.setDescription(COUNT_PARAM_DESC);
        countParam.setMinimum(1);
        countParam.setMaximum(64);
        root.addProperties(COUNT_PARAM_ID, countParam, false);

        return root;
    }

    @Override
    public Codec<Params> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf(ITEM_PARAM_ID).forGetter(Params::itemId),
                        Codec.INT.optionalFieldOf(COUNT_PARAM_ID, 1).forGetter(Params::count)
                ).apply(instance, Params::new));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        // Check if there's already a pending task
        boolean hasExistingTask = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PENDING_TASK.get())
                .filter(task -> !task.isComplete())
                .isPresent();
        
        if (hasExistingTask) {
            return new ToolResponse("Another task is already in progress. Please wait for it to complete.");
        }

        // Create and queue new craft task (non-blocking!)
        PendingTask task = new PendingTask(
                PendingTask.TaskType.CRAFT,
                params.itemId(),
                params.count()
        );
        
        maid.getBrain().setMemory(MemoryModuleRegistry.PENDING_TASK.get(), task);

        // Return immediately - behavior will process and callback when done
        return new ToolResponse(String.format(
                "Task queued: I will craft %d %s. I'll let you know when I'm done.",
                params.count(), params.itemId().replace("minecraft:", "")
        ));
    }

    public record Params(String itemId, int count) {}
}

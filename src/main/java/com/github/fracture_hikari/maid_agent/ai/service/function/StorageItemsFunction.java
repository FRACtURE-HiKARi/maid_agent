package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ArrayParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.FetchTask;
import com.github.fracture_hikari.maid_agent.maid.memory.StoreTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * LLM function to manage storage operations (fetch or store items).
 * 
 * Supports both single operations and batch operations via 'operations' array.
 * For multi-step tasks (e.g., move items), use the array to queue all steps.
 * Maid will execute sequentially and report results when done.
 */
public class StorageItemsFunction implements IFunctionCall<StorageItemsFunction.Params> {
    private static final String FUNCTION_ID = "storage_items";
    private static final String FUNCTION_DESC = """
            Fetch or store items from/to containers. Requires get_nearby_storage first.
            Supports batching: [{action,storage_index,item_id,count}, ...] for multi-step operations.
            Use 'fetch' to take items from storage, 'store' to put items into storage.
            Returns: completion summary with [FETCH/STORE] item_id xCount - Success/Failed.""";

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
        // Define the operation object structure
        ObjectParameter operationSchema = getOperationSchema();

        // Operations array
        ArrayParameter operationsArray = ArrayParameter.create();
        operationsArray.setDescription("""
                Array of operations to execute in sequence.
                Each operation: {action, storage_index, item_id, count}
                Single example: [{"action":"fetch","item_id":"minecraft:diamond","storage_index":0}]
                Batch example: [{"action":"fetch","item_id":"minecraft:torch","storage_index":0,"count":64},
                               {"action":"store","item_id":"minecraft:torch","storage_index":1,"count":64}]""");
        operationsArray.setItems(operationSchema);
        operationsArray.setMinItems(1);
        operationsArray.setMaxItems(10);
        root.addProperties("operations", operationsArray);
        
        return root;
    }

    private static @NotNull ObjectParameter getOperationSchema() {
        ObjectParameter operationSchema = ObjectParameter.create();

        StringParameter actionParam = StringParameter.create();
        actionParam.setDescription("'fetch' to get items from storage, 'store' to put items into storage");
        actionParam.addEnumValues("fetch", "store");
        operationSchema.addProperties("action", actionParam);

        IntegerParameter storageIndexParam = IntegerParameter.create();
        storageIndexParam.setDescription("Index of storage from get_nearby_storage result (0, 1, 2...)");
        storageIndexParam.setMinimum(0);
        storageIndexParam.setMaximum(20);
        operationSchema.addProperties("storage_index", storageIndexParam);

        StringParameter itemParam = StringParameter.create();
        itemParam.setDescription("Item ID in namespace:name format (e.g. minecraft:torch)");
        itemParam.setMinLength(1);
        operationSchema.addProperties("item_id", itemParam);

        IntegerParameter countParam = IntegerParameter.create();
        countParam.setDescription("Number of items. Default: 1 for fetch, all for store.");
        countParam.setMinimum(0);
        countParam.setMaximum(2304);
        operationSchema.addProperties("count", countParam);
        return operationSchema;
    }

    @Override
    public Codec<Params> codec() {
        Codec<Operation> operationCodec = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("action").forGetter(Operation::action),
                        Codec.INT.fieldOf("storage_index").forGetter(Operation::storageIndex),
                        Codec.STRING.fieldOf("item_id").forGetter(Operation::itemId),
                        Codec.INT.optionalFieldOf("count", 0).forGetter(Operation::count)
                ).apply(instance, Operation::new));
        
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        operationCodec.listOf().fieldOf("operations").forGetter(Params::operations)
                ).apply(instance, Params::new));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) { return null; }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid, String toolCallId) {
        // Storage operations require maid_storage_manager
        if (!Integrations.maidStorageManager()) {
            return new ToolResponse(
                    Integrations.getMsmRequiredMessage());
        }
        
        List<Operation> operations = params.operations();
        
        if (operations == null || operations.isEmpty()) {
            return new ToolResponse("No operations provided. Use 'operations' array with at least one operation.");
        }
        
        // Validate storage memory exists
        Optional<ViewedStorageMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get());
        
        if (memoryOpt.isEmpty()) {
            return new ToolResponse("No storages discovered. Use get_nearby_storage first.");
        }
        
        ViewedStorageMemory memory = memoryOpt.get();
        
        // Get or create TaskQueue
        TaskQueue taskQueue = TaskQueueHelper.getOrCreateQueue(maid);
        
        boolean wasEmpty = taskQueue.isEmpty();
        StringBuilder errors = new StringBuilder();
        List<PendingTask> tasksToQueue = new java.util.ArrayList<>();
        
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            
            // Validate action
            boolean isFetch;
            int effectiveCount;
            
            if ("fetch".equalsIgnoreCase(op.action())) {
                isFetch = true;
                effectiveCount = op.count() <= 0 ? 1 : op.count();
            } else if ("store".equalsIgnoreCase(op.action())) {
                isFetch = false;
                effectiveCount = op.count() <= 0 ? Integer.MAX_VALUE : op.count();
            } else {
                errors.append(String.format("Skipped operation %d: invalid action '%s'. ", i + 1, op.action()));
                continue;
            }
            
            // Validate storage index
            Optional<WorkBlockTarget> targetOpt = memory.getStorageByIndex(op.storageIndex());
            if (targetOpt.isEmpty()) {
                errors.append(String.format("Skipped operation %d: invalid storage_index %d. ", i + 1, op.storageIndex()));
                continue;
            }
            
            WorkBlockTarget target = targetOpt.get();
            
            // Create ItemStack first
            net.minecraft.world.item.ItemStack stack = com.github.fracture_hikari.maid_agent.util.ItemIdUtils.createStack(op.itemId(), effectiveCount);
            if (stack.isEmpty()) {
                errors.append(String.format("Skipped operation %d: invalid item '%s'. ", i + 1, op.itemId()));
                continue;
            }

            // Check for similar task already in queue (warn but allow)
            Class<? extends PendingTask> taskClass = isFetch ? FetchTask.class : StoreTask.class;
            if (taskQueue.hasSimilarTask(taskClass, stack, op.storageIndex())) {
                errors.append(String.format("Note: Similar %s for %s already queued. ", 
                        op.action(), op.itemId().replace("minecraft:", "")));
            }
            
            // Create task
            PendingTask task = isFetch ? new FetchTask(stack, target) : new StoreTask(stack, target);
            tasksToQueue.add(task);
            
            MaidAgent.LOGGER.info("Prepared operation: {} {} from storage[{}]", 
                    isFetch ? "FETCH" : "STORE", op.itemId(), op.storageIndex());
        }
        
        if (tasksToQueue.isEmpty()) {
            return new ToolResponse("No valid operations to queue. " + errors);
        }
        
        // Enqueue all tasks with toolCallId tracking
        taskQueue.enqueue(tasksToQueue, toolCallId);
        
        // Start walking for first task
        if (wasEmpty && !tasksToQueue.isEmpty()) {
            tasksToQueue.get(0).getTarget().ifPresent(target -> 
                TaskQueueHelper.setMovementTarget(maid, target.getPos()));
        }
        
        MaidAgent.LOGGER.info("Queued {} operations for toolCallId {}", tasksToQueue.size(), toolCallId);
        
        // Return PENDING to wait for async completion
        return ToolResponse.PENDING;
    }

    public record Operation(String action, int storageIndex, String itemId, int count) {}
    public record Params(List<Operation> operations) {}
}

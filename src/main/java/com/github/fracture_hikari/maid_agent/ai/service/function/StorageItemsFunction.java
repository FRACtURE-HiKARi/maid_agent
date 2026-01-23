package com.github.fracture_hikari.maid_agent.ai.service.function;

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
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;

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
            Fetch or store items from/to storage containers.
            First use get_nearby_storage to see available storages.
            
            Supports single or batch operations:
            - Single: provide one operation object in the 'operations' array
            - Batch: provide multiple operations for sequential execution (e.g., fetch then store)
            
            The maid will execute all operations in order and notify you when complete.""";

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
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        // Storage operations require maid_storage_manager
        if (!com.github.fracture_hikari.maid_agent.compat.Integrations.maidStorageManager()) {
            return new ToolResponse(
                    com.github.fracture_hikari.maid_agent.compat.Integrations.getMsmRequiredMessage());
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
        StringBuilder response = new StringBuilder();
        int queued = 0;
        
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            
            // Validate action
            PendingTask.TaskType taskType;
            int effectiveCount;
            
            if ("fetch".equalsIgnoreCase(op.action())) {
                taskType = PendingTask.TaskType.FETCH;
                effectiveCount = op.count() <= 0 ? 1 : op.count();
            } else if ("store".equalsIgnoreCase(op.action())) {
                taskType = PendingTask.TaskType.STORE;
                effectiveCount = op.count() <= 0 ? Integer.MAX_VALUE : op.count();
            } else {
                response.append(String.format("Skipped operation %d: invalid action '%s'. ", i + 1, op.action()));
                continue;
            }
            
            // Validate storage index
            Optional<WorkBlockTarget> targetOpt = memory.getStorageByIndex(op.storageIndex());
            if (targetOpt.isEmpty()) {
                response.append(String.format("Skipped operation %d: invalid storage_index %d. ", i + 1, op.storageIndex()));
                continue;
            }
            
            WorkBlockTarget target = targetOpt.get();
            
            // Check for similar task already in queue (warn but allow - could be multiple stacks)
            if (taskQueue.hasSimilarTask(taskType, op.itemId(), op.storageIndex())) {
                response.append(String.format("Note: Similar %s for %s already queued. ", 
                        op.action(), op.itemId().replace("minecraft:", "")));
            }
            
            // Create task and add to queue
            PendingTask task = new PendingTask(maid, taskType, op.itemId(), effectiveCount);
            task.setTarget(target);
            int position = taskQueue.enqueue(task);
            queued++;
            
            // Start walking for first task
            if (wasEmpty && queued == 1) {
                TaskQueueHelper.setMovementTarget(maid, target.getPos());
            }
            
            MaidAgent.LOGGER.info("Queued operation {}: {} {} from storage[{}]", 
                    position, taskType, op.itemId(), op.storageIndex());
        }
        
        if (queued == 0) {
            return new ToolResponse("No valid operations to queue. " + response);
        }
        
        response.append(String.format("Queued %d operation(s). Will notify when complete.", queued));
        return new ToolResponse(response.toString());
    }

    public record Operation(String action, int storageIndex, String itemId, int count) {}
    public record Params(List<Operation> operations) {}
}

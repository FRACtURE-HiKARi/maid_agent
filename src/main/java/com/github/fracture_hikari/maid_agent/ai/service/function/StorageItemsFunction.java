package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ArrayParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.StorageTarget;
import com.github.fracture_hikari.maid_agent.storage.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.storage.memory.TaskQueue;
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

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
    
    private static final float WALK_SPEED = 0.6f;

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
        TaskQueue taskQueue = maid.getBrain()
                .getMemory(MemoryModuleRegistry.TASK_QUEUE.get())
                .orElseGet(() -> {
                    TaskQueue newQueue = new TaskQueue(maid);
                    maid.getBrain().setMemory(MemoryModuleRegistry.TASK_QUEUE.get(), newQueue);
                    return newQueue;
                });
        
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
            Optional<StorageTarget> targetOpt = memory.getStorageByIndex(op.storageIndex());
            if (targetOpt.isEmpty()) {
                response.append(String.format("Skipped operation %d: invalid storage_index %d. ", i + 1, op.storageIndex()));
                continue;
            }
            
            StorageTarget target = targetOpt.get();
            
            // Create task and add to queue
            PendingTask task = new PendingTask(maid, taskType, op.itemId(), effectiveCount);
            task.setTarget(target);
            task.setStatus(PendingTask.TaskStatus.PENDING);
            int position = taskQueue.enqueue(task);
            queued++;
            
            // Start walking for first task
            if (wasEmpty && queued == 1) {
                task.setStatus(PendingTask.TaskStatus.MOVING);
                BlockPos targetPos = target.getPos();
                maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(targetPos));
                BehaviorUtils.setWalkAndLookTargetMemories(maid, targetPos, WALK_SPEED, 1);
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

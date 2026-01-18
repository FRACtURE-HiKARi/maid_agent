package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
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
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

import java.util.Optional;

/**
 * LLM function to manage storage operations (fetch or store items).
 * 
 * NON-BLOCKING: Queues task and returns immediately.
 * Uses storage_index from get_nearby_storage to specify target storage.
 * Sets TARGET_POS directly so maid walks to the specified storage.
 */
public class StorageItemsFunction implements IFunctionCall<StorageItemsFunction.Params> {
    private static final String FUNCTION_ID = "storage_items";
    private static final String FUNCTION_DESC = """
            Fetch or store items from/to a storage container.
            First use get_nearby_storage to see available storages, then use storage_index to specify which one.
            The maid will walk to the storage and perform the operation.""";
    
    private static final String ACTION_PARAM_ID = "action";
    private static final String ACTION_PARAM_DESC = """
            action (string, required): The operation to perform - 'fetch' or 'store'.
            - fetch: Get items from storage into maid's inventory
            - store: Put items from maid's inventory into storage""";
    
    private static final String STORAGE_INDEX_PARAM_ID = "storage_index";
    private static final String STORAGE_INDEX_PARAM_DESC = """
            storage_index (integer, required): Index of the storage from get_nearby_storage result.
            Use [0] for the first storage, [1] for the second, etc.""";
    
    private static final String ITEM_PARAM_ID = "item_id";
    private static final String ITEM_PARAM_DESC = """
            item_id (string, required): The item in namespace:name format.
            Example: minecraft:diamond, minecraft:oak_planks""";
    
    private static final String COUNT_PARAM_ID = "count";
    private static final String COUNT_PARAM_DESC = """
            count (integer, optional): Number of items. Default: 1 for fetch, all for store.""";

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
        StringParameter actionParam = StringParameter.create();
        actionParam.setDescription(ACTION_PARAM_DESC);
        actionParam.addEnumValues("fetch", "store");
        root.addProperties(ACTION_PARAM_ID, actionParam);

        IntegerParameter storageIndexParam = IntegerParameter.create();
        storageIndexParam.setDescription(STORAGE_INDEX_PARAM_DESC);
        storageIndexParam.setMinimum(0);
        storageIndexParam.setMaximum(20);
        root.addProperties(STORAGE_INDEX_PARAM_ID, storageIndexParam);

        StringParameter itemParam = StringParameter.create();
        itemParam.setDescription(ITEM_PARAM_DESC);
        itemParam.setMinLength(1);
        root.addProperties(ITEM_PARAM_ID, itemParam);

        IntegerParameter countParam = IntegerParameter.create();
        countParam.setDescription(COUNT_PARAM_DESC);
        countParam.setMinimum(0);
        countParam.setMaximum(64 * 36);
        root.addProperties(COUNT_PARAM_ID, countParam, false);

        return root;
    }

    @Override
    public Codec<Params> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf(ACTION_PARAM_ID).forGetter(Params::action),
                        Codec.INT.fieldOf(STORAGE_INDEX_PARAM_ID).forGetter(Params::storageIndex),
                        Codec.STRING.fieldOf(ITEM_PARAM_ID).forGetter(Params::itemId),
                        Codec.INT.optionalFieldOf(COUNT_PARAM_ID, 0).forGetter(Params::count)
                ).apply(instance, Params::new));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        // Validate action
        PendingTask.TaskType taskType;
        int effectiveCount;
        String actionVerb;
        
        if ("fetch".equalsIgnoreCase(params.action())) {
            taskType = PendingTask.TaskType.FETCH;
            effectiveCount = params.count() <= 0 ? 1 : params.count();
            actionVerb = "fetch";
        } else if ("store".equalsIgnoreCase(params.action())) {
            taskType = PendingTask.TaskType.STORE;
            effectiveCount = params.count() <= 0 ? Integer.MAX_VALUE : params.count();
            actionVerb = "store";
        } else {
            return new ToolResponse("Invalid action: '" + params.action() + "'. Use 'fetch' or 'store'.");
        }

        // Look up storage by index from ViewedStorageMemory
        Optional<ViewedStorageMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get());
        
        if (memoryOpt.isEmpty()) {
            return new ToolResponse("No storages discovered. Use get_nearby_storage first.");
        }
        
        ViewedStorageMemory memory = memoryOpt.get();
        Optional<StorageTarget> targetOpt = memory.getStorageByIndex(params.storageIndex());
        
        if (targetOpt.isEmpty()) {
            int count = memory.getStorageCount();
            return new ToolResponse(String.format(
                    "Invalid storage_index %d. Only %d storage(s) available (0-%d). Use get_nearby_storage to refresh.",
                    params.storageIndex(), count, count - 1));
        }
        
        StorageTarget target = targetOpt.get();
        
        // Check if there's already a pending task
        boolean hasExistingTask = maid.getBrain()
                .getMemory(MemoryModuleRegistry.PENDING_TASK.get())
                .filter(task -> !task.isComplete())
                .isPresent();
        
        if (hasExistingTask) {
            MaidAgent.LOGGER.warn("erasing previous pending task memory");
            maid.getBrain().eraseMemory(MemoryModuleRegistry.PENDING_TASK.get());
        }

        // Create task with target already set
        PendingTask task = new PendingTask(taskType, params.itemId(), effectiveCount);
        task.setTarget(target);
        task.setStatus(PendingTask.TaskStatus.MOVING);
        maid.getBrain().setMemory(MemoryModuleRegistry.PENDING_TASK.get(), task);
        
        // Set TARGET_POS and WALK_TARGET directly so maid starts walking
        BlockPos targetPos = target.getPos();
        maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(targetPos));
        BehaviorUtils.setWalkAndLookTargetMemories(maid, targetPos, WALK_SPEED, 1);
        
        MaidAgent.LOGGER.info("Storage task queued: {} {} {} at storage[{}] pos={}",
                taskType, params.itemId(), effectiveCount, params.storageIndex(), targetPos);

        // Return immediately
        String countDesc = effectiveCount == Integer.MAX_VALUE ? "all" : String.valueOf(effectiveCount);
        return new ToolResponse(String.format(
                "Task queued: %s %s %s from storage[%d] at (%d,%d,%d).",
                actionVerb, countDesc, params.itemId().replace("minecraft:", ""),
                params.storageIndex(), targetPos.getX(), targetPos.getY(), targetPos.getZ()
        ));
    }

    public record Params(String action, int storageIndex, String itemId, int count) {}
}

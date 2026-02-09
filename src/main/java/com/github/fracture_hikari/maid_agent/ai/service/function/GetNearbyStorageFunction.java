package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.compat.Integrations;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.fracture_hikari.maid_agent.util.TaskQueueHelper;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.ExploreAllTask;
import com.github.fracture_hikari.maid_agent.maid.memory.TaskQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * LLM function to discover nearby storage blocks.
 * 
 * Launches a single EXPLORE_ALL task that makes the maid dynamically find and
 * explore all nearby storages using BFS pathfinding.
 * 
 * Results are delivered via AI callback when exploration completes.
 */
public class GetNearbyStorageFunction implements IFunctionCall<GetNearbyStorageFunction.Params> {
    private static final String FUNCTION_ID = "get_nearby_storage";
    private static final String FUNCTION_DESC = """
            Discover storage containers (chests, barrels, etc.) near the maid.
            Use before storage_items to get available storage indices.
            The maid walks to scan containers within the radius.
            Returns: list of containers with index, position, type, and contents.""";
    
    private static final String RADIUS_PARAM_ID = "radius";
    private static final String RADIUS_PARAM_DESC = """
            Search radius in blocks. Default: 16. Max: 32.""";

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
        IntegerParameter radiusParam = IntegerParameter.create();
        radiusParam.setDescription(RADIUS_PARAM_DESC);
        radiusParam.setMinimum(1);
        radiusParam.setMaximum(32);
        root.addProperties(RADIUS_PARAM_ID, radiusParam, false);
        return root;
    }

    @Override
    public Codec<Params> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.optionalFieldOf(RADIUS_PARAM_ID, 16).forGetter(Params::radius)
                ).apply(instance, Params::new));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        return null;
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid, String toolCallId) {
        // Storage operations require maid_storage_manager
        if (!Integrations.maidStorageManager()) {
            return new ToolResponse(Integrations.getMsmRequiredMessage());
        }

        if (!(maid.level() instanceof ServerLevel)) {
            return new ToolResponse("Cannot scan storage - not on server");
        }

        // Get or create TaskQueue and check for existing explore task
        TaskQueue queue = TaskQueueHelper.getOrCreateQueue(maid);

        // Check if already exploring
        if (!queue.isEmpty()) {
            PendingTask currentTask = queue.peek();
            if (currentTask instanceof ExploreAllTask) {
                return new ToolResponse("Already exploring storages. Please wait for the results.");
            }
        }

        // Clear previous storage memory for fresh exploration
        ViewedStorageMemory memory = MemoryUtil.getOrCreateViewedStorageMemory(maid);
        memory.clearStorages();
        memory.resetVisited();

        int radius = Math.min(params.radius(), 32);
        
        // Create single EXPLORE_ALL task with exploration radius
        ExploreAllTask exploreTask = new ExploreAllTask(radius, toolCallId);
        
        queue.enqueue(exploreTask);
        
        MaidAgent.LOGGER.info("Launched EXPLORE_ALL task with radius {} for storage discovery", radius);
        
        return ToolResponse.PENDING;
    }

    public record Params(int radius) {
        public Params() {
            this(16);  // Default radius
        }
    }
}

package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.IStorageHandler;
import com.github.fracture_hikari.maid_agent.storage.StorageManager;
import com.github.fracture_hikari.maid_agent.storage.StorageTarget;
import com.github.fracture_hikari.maid_agent.storage.memory.ViewedStorageMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * LLM function to discover and list nearby storage blocks.
 * Stores discovered storages in ViewedStorageMemory so they can be referenced by index.
 */
public class GetNearbyStorageFunction implements IFunctionCall<GetNearbyStorageFunction.Params> {
    private static final String FUNCTION_ID = "get_nearby_storage";
    private static final String FUNCTION_DESC = """
            Discover and list nearby storage containers (chests, barrels, etc.).
            Returns a numbered list of storage locations and their contents.
            Use the storage_index in fetch/store operations.""";
    
    private static final String RADIUS_PARAM_ID = "radius";
    private static final String RADIUS_PARAM_DESC = """
            radius (integer, optional): Search radius in blocks. Default: 16. Max: 32.""";

    private static final int MAX_STORAGES = 10;

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
        if (!(maid.level() instanceof ServerLevel level)) {
            return new ToolResponse("Cannot scan storage - not on server");
        }

        BlockPos maidPos = maid.blockPosition();
        int radius = Math.min(params.radius(), 32);
        
        List<StorageInfo> storages = new ArrayList<>();
        
        // Scan for storage blocks
        for (int x = -radius; x <= radius && storages.size() < MAX_STORAGES; x++) {
            for (int y = -radius / 2; y <= radius / 2 && storages.size() < MAX_STORAGES; y++) {
                for (int z = -radius; z <= radius && storages.size() < MAX_STORAGES; z++) {
                    BlockPos checkPos = maidPos.offset(x, y, z);
                    
                    Optional<StorageTarget> targetOpt = StorageManager.getInstance()
                            .isValidTarget(level, checkPos, null);
                    
                    if (targetOpt.isPresent()) {
                        StorageTarget target = targetOpt.get();
                        IStorageHandler handler = StorageManager.getInstance()
                                .getHandler(target.getType())
                                .orElse(null);
                        
                        if (handler != null) {
                            List<ItemStack> contents = handler.getContents(level, checkPos, null);
                            storages.add(new StorageInfo(target, contents, 
                                    (int) Math.sqrt(checkPos.distSqr(maidPos))));
                        }
                    }
                }
            }
        }
        
        if (storages.isEmpty()) {
            return new ToolResponse("No storage containers found within " + radius + " blocks.");
        }
        
        // Sort by distance
        storages.sort(Comparator.comparingInt(s -> s.distance));
        
        // Store in ViewedStorageMemory for later reference by index
        ViewedStorageMemory viewedStorage = maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get())
                .orElseGet(() -> {
                    ViewedStorageMemory mem = new ViewedStorageMemory();
                    maid.getBrain().setMemory(MemoryModuleRegistry.VIEWED_STORAGE.get(), mem);
                    return mem;
                });
        
        // Clear old entries and add new ones
        viewedStorage.clearStorages();
        for (StorageInfo info : storages) {
            viewedStorage.addStorage(info.target, info.contents);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d storage(s) within %d blocks:\\n", 
                storages.size(), radius));
        sb.append("Use storage_index parameter in storage_items to specify target.\\n\\n");
        
        for (int i = 0; i < storages.size(); i++) {
            StorageInfo info = storages.get(i);
            BlockPos pos = info.target.getPos();
            sb.append(String.format("[%d] %s at (%d,%d,%d) ~%d blocks\\n", 
                    i, info.target.getType().getPath(), pos.getX(), pos.getY(), pos.getZ(), info.distance));
            
            if (info.contents.isEmpty()) {
                sb.append("    Empty\\n");
            } else {
                // Summarize contents (group by item ID)
                Map<String, Integer> itemCounts = new LinkedHashMap<>();
                for (ItemStack stack : info.contents) {
                    if (!stack.isEmpty()) {
                        // Use item registry name (namespace:name) so LLM can use it in operations
                        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                .getKey(stack.getItem()).toString();
                        itemCounts.merge(itemId, stack.getCount(), Integer::sum);
                    }
                }
                
                int shown = 0;
                for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
                    if (shown >= 5) {
                        sb.append(String.format("    +%d more types\\n", itemCounts.size() - shown));
                        break;
                    }
                    sb.append(String.format("    %s x%d\\n", entry.getKey(), entry.getValue()));
                    shown++;
                }
            }
        }
        MaidAgent.LOGGER.info(sb.toString().trim());
        return new ToolResponse(sb.toString().trim());
    }

    private record StorageInfo(StorageTarget target, List<ItemStack> contents, int distance) {}

    public record Params(int radius) {}
}

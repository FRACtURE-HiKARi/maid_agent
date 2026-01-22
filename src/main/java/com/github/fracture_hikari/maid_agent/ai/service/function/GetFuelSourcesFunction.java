package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import com.github.fracture_hikari.maid_agent.maid.memory.ViewedStorageMemory;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

import java.util.*;

/**
 * LLM function to find fuel items in known storages.
 * Returns fuel information with items_per_fuel (how many items each fuel can process).
 */
@Deprecated
public class GetFuelSourcesFunction implements IFunctionCall<GetFuelSourcesFunction.Params> {
    private static final String FUNCTION_ID = "get_fuel_sources";
    private static final String FUNCTION_DESC = """
            Find fuel items in known storages for furnace/smelting operations.
            Returns storages containing fuel with burn efficiency.
            Note: smoker/blast_furnace process 2x faster (same fuel efficiency).""";

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
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.optionalFieldOf("_unused", false).forGetter(p -> false)
                ).apply(instance, b -> new Params()));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        // Requires viewed storage memory
        Optional<ViewedStorageMemory> memoryOpt = maid.getBrain()
                .getMemory(MemoryModuleRegistry.VIEWED_STORAGE.get());
        
        if (memoryOpt.isEmpty() || memoryOpt.get().getStorageCount() == 0) {
            return new ToolResponse(
                    "No storage data available. Call get_nearby_storage first to scan storages.");
        }
        
        ViewedStorageMemory memory = memoryOpt.get();
        List<StorageFuelInfo> fuelStorages = new ArrayList<>();
        
        // Scan all known storages for fuel
        for (int i = 0; i < memory.getStorageCount(); i++) {
            Optional<WorkBlockTarget> targetOpt = memory.getStorageByIndex(i);
            if (targetOpt.isEmpty()) continue;
            
            WorkBlockTarget target = targetOpt.get();
            List<ViewedStorageMemory.ItemCount> contents = memory.getContents(target);
            
            List<FuelItem> fuels = new ArrayList<>();
            for (ViewedStorageMemory.ItemCount ic : contents) {
                int burnTime = ForgeHooks.getBurnTime(ic.item(), RecipeType.SMELTING);
                if (burnTime > 0) {
                    // items_per_fuel = burn_time / 200 (200 ticks = 10 seconds = 1 smelt)
                    int itemsPerFuel = burnTime / 200;
                    if (itemsPerFuel < 1) itemsPerFuel = 1;
                    
                    String itemId = ItemIdUtils.getId(ic.item());
                    fuels.add(new FuelItem(itemId, ic.count(), itemsPerFuel));
                }
            }
            
            if (!fuels.isEmpty()) {
                fuelStorages.add(new StorageFuelInfo(i, fuels));
            }
        }
        
        if (fuelStorages.isEmpty()) {
            return new ToolResponse("No fuel items found in known storages.");
        }
        
        // Format response
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found fuel in %d storage(s):\n", fuelStorages.size()));
        sb.append("Note: smoker/blast_furnace process 2x faster (same items_per_fuel).\n\n");
        
        for (StorageFuelInfo storage : fuelStorages) {
            sb.append(String.format("[Storage %d] Fuels:\n", storage.storageIndex));
            for (FuelItem fuel : storage.fuels) {
                sb.append(String.format("  - %s x%d (processes %d items/fuel)\n",
                        fuel.itemId, fuel.count, fuel.itemsPerFuel));
            }
        }
        
        return new ToolResponse(sb.toString());
    }
    
    private record FuelItem(String itemId, int count, int itemsPerFuel) {}
    private record StorageFuelInfo(int storageIndex, List<FuelItem> fuels) {}

    public record Params() {}
}

package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.util.InventoryUtils;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * LLM function to get the maid's current inventory contents.
 * This is a synchronous function that returns immediately.
 */
public class GetInventoryFunction implements IFunctionCall<GetInventoryFunction.Params> {
    private static final String FUNCTION_ID = "get_inventory";
    private static final String FUNCTION_DESC = """
            Get the maid's current inventory contents.
            Returns a list of items with their quantities.""";

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
                instance.point(new Params()));
    }

    @Override
    public ToolResponse onToolCall(Params params, EntityMaid maid) {
        Map<String, InventoryUtils.ItemInfo> items = InventoryUtils.aggregateWithNames(
                maid.getAvailableInv(false));
        
        if (items.isEmpty()) {
            return new ToolResponse("Maid's inventory is empty.");
        }
        
        StringBuilder sb = new StringBuilder("Maid's inventory:\n");
        items.values().forEach(info -> 
            sb.append(String.format("- %s (%s) x%d\n", info.displayName(), info.itemId(), info.count()))
        );
        
        return new ToolResponse(sb.toString().trim());
    }

    public record Params() {}
}

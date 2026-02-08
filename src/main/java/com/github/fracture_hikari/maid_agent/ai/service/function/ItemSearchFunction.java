package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.fracture_hikari.maid_agent.util.JeiRuntimeHolder;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.config.JeiConfig;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM function to search for items by keyword.
 * Uses JEI when available, falls back to vanilla registry scan otherwise.
 */
public class ItemSearchFunction implements IFunctionCall<ItemSearchFunction.Result> {
    private static final String FUNCTION_ID = "item_search";
    private static final String FUNCTION_DESC = """
            Search for item IDs by keyword. Use to find exact item IDs for other tools.
            With JEI: supports @modname, #tagname, $tooltip syntax.
            Returns matching items with namespace:name format for use in craft_item, storage_items.""";
    private static final String KEYWORD_PARAM_ID = "keyword";
    private static final String KEYWORD_PARAM_DESC = """
            keyword (string, required): The search term to find items. 
            Searches item names and IDs. With JEI: supports @modname, #tagname, $tooltip syntax.""";

    private static final String NO_RESULTS = "No items found matching '%s'";
    private static final String SUCCESS = "Found %d item(s) matching '%s':\n%s";

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
        StringParameter keyword = StringParameter.create();
        keyword.setDescription(KEYWORD_PARAM_DESC);
        keyword.setMinLength(1);
        root.addProperties(KEYWORD_PARAM_ID, keyword);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(Codec.STRING.fieldOf(KEYWORD_PARAM_ID).forGetter(Result::keyword))
                        .apply(instance, Result::new));
    }

    @Override
    public ToolResponse onToolCall(Result result, EntityMaid maid) {
        int maxResults = JeiConfig.ITEM_SEARCH_MAX_RESULTS.get();
        String keyword = result.keyword.toLowerCase();
        
        // Try JEI first, fall back to vanilla
        if (JeiRuntimeHolder.isAvailable()) {
            IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElse(null);
            if (runtime != null) {
                return searchWithJei(runtime, keyword, maxResults);
            }
        }
        
        // Vanilla fallback
        return searchVanillaRegistry(keyword, maxResults);
    }
    
    private ToolResponse searchWithJei(IJeiRuntime runtime, String keyword, int maxResults) {
        IIngredientFilter filter = runtime.getIngredientFilter();
        String originalFilter = filter.getFilterText();
        
        try {
            filter.setFilterText(keyword);
            List<ItemStack> filteredItems = filter.getFilteredIngredients(VanillaTypes.ITEM_STACK);
            
            if (filteredItems.isEmpty()) {
                return new ToolResponse(NO_RESULTS.formatted(keyword));
            }

            List<String> itemInfos = filteredItems.stream()
                    .limit(maxResults)
                    .map(this::formatItemInfo)
                    .collect(Collectors.toList());

            String resultText = String.join("\n", itemInfos);
            int totalFound = filteredItems.size();
            String suffix = totalFound > maxResults 
                    ? String.format("\n... and %d more items", totalFound - maxResults) 
                    : "";
            
            return new ToolResponse(SUCCESS.formatted(
                    Math.min(totalFound, maxResults), 
                    keyword, 
                    resultText + suffix));
        } finally {
            filter.setFilterText(originalFilter);
        }
    }
    
    private ToolResponse searchVanillaRegistry(String keyword, int maxResults) {
        List<String> matches = BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .filter(stack -> {
                    String name = stack.getHoverName().getString().toLowerCase();
                    String id = ItemIdUtils.getId(stack).toLowerCase();
                    return name.contains(keyword) || id.contains(keyword);
                })
                .limit(maxResults)
                .map(this::formatItemInfo)
                .toList();
        
        if (matches.isEmpty()) {
            return new ToolResponse(NO_RESULTS.formatted(keyword));
        }
        
        return new ToolResponse(SUCCESS.formatted(matches.size(), keyword, String.join("\n", matches)));
    }

    private String formatItemInfo(ItemStack stack) {
        String itemId = ItemIdUtils.getId(stack);
        String displayName = stack.getHoverName().getString();
        return String.format("- %s (%s)", displayName, itemId);
    }

    public record Result(String keyword) {
    }
}


package com.github.fracture_hikari.maid_agent.ai.service.function;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import com.github.fracture_hikari.maid_agent.util.JeiRuntimeHolder;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.github.fracture_hikari.maid_agent.config.JeiConfig;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM function to search for items by keyword using JEI's ingredient filter.
 * Returns matching items with their namespace:name identifiers.
 */
public class ItemSearchFunction implements IFunctionCall<ItemSearchFunction.Result> {
    private static final String FUNCTION_ID = "jei_item_search";
    private static final String FUNCTION_DESC = """
            Search for items by keyword. Returns a list of matching items with their resource locations.
            Use this to find items when you know part of the name but not the exact item ID.""";
    private static final String KEYWORD_PARAM_ID = "keyword";
    private static final String KEYWORD_PARAM_DESC = """
            keyword (string, required): The search term to find items. 
            Supports JEI search syntax: plain text for name search, @modname for mod search, 
            #tagname for tag search, $tooltip for tooltip search.""";

    private static final String JEI_UNAVAILABLE = "JEI is not available. Cannot search for items.";
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
        if (!JeiRuntimeHolder.isAvailable()) {
            return new ToolResponse(JEI_UNAVAILABLE);
        }

        IJeiRuntime runtime = JeiRuntimeHolder.getRuntime().orElse(null);
        if (runtime == null) {
            return new ToolResponse(JEI_UNAVAILABLE);
        }

        String keyword = result.keyword.toLowerCase();
        IIngredientFilter filter = runtime.getIngredientFilter();
        
        // Save current filter text to restore later
        String originalFilter = filter.getFilterText();
        
        try {
            filter.setFilterText(keyword);
            List<ItemStack> filteredItems = filter.getFilteredIngredients(VanillaTypes.ITEM_STACK);
            
            if (filteredItems.isEmpty()) {
                return new ToolResponse(NO_RESULTS.formatted(result.keyword));
            }

            int maxResults = JeiConfig.ITEM_SEARCH_MAX_RESULTS.get();
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
                    result.keyword, 
                    resultText + suffix));
        } finally {
            // Restore original filter
            filter.setFilterText(originalFilter);
        }
    }

    private String formatItemInfo(ItemStack stack) {
        String itemId = ItemIdUtils.getId(stack);
        String displayName = stack.getHoverName().getString();
        return String.format("- %s (%s)", displayName, itemId);
    }

    public record Result(String keyword) {
    }
}

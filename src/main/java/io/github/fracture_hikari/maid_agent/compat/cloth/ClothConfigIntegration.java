package io.github.fracture_hikari.maid_agent.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.fracture_hikari.maid_agent.config.JeiConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Cloth Config integration for Maid Agent settings.
 * Registers config entries in TouhouLittleMaid's config screen.
 */
@OnlyIn(Dist.CLIENT)
public class ClothConfigIntegration {
    
    @SubscribeEvent
    public static void onAddClothConfig(AddClothConfigEvent event) {
        ConfigEntryBuilder entryBuilder = event.getEntryBuilder();
        
        // Create "Maid Agent" category
        ConfigCategory maidAgent = event.getRoot().getOrCreateCategory(
                Component.translatable("config.maid_agent.category"));
        
        // JEI Integration subcategory
        addJeiConfig(entryBuilder, maidAgent);
    }
    
    private static void addJeiConfig(ConfigEntryBuilder entryBuilder, ConfigCategory category) {
        SubCategoryBuilder builder = entryBuilder.startSubCategory(
                Component.translatable("config.maid_agent.jei"));
        builder.setExpanded(true);
        
        // Item Search Max Results
        builder.add(entryBuilder.startIntSlider(
                        Component.translatable("config.maid_agent.jei.item_search_max"),
                        JeiConfig.ITEM_SEARCH_MAX_RESULTS.get(), 1, 100)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("config.maid_agent.jei.item_search_max.tooltip"))
                .setSaveConsumer(JeiConfig.ITEM_SEARCH_MAX_RESULTS::set)
                .build());
        
        // Recipe Search Max Results
        builder.add(entryBuilder.startIntSlider(
                        Component.translatable("config.maid_agent.jei.recipe_search_max"),
                        JeiConfig.RECIPE_SEARCH_MAX_RESULTS.get(), 1, 50)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.maid_agent.jei.recipe_search_max.tooltip"))
                .setSaveConsumer(JeiConfig.RECIPE_SEARCH_MAX_RESULTS::set)
                .build());
        
        category.addEntry(builder.build());
    }
}

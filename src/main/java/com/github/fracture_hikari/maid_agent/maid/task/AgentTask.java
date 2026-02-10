package com.github.fracture_hikari.maid_agent.maid.task;

import com.github.fracture_hikari.maid_agent.maid.behavior.*;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Maid "Agent" task - enables LLM-controlled storage and crafting operations.
 * 
 * Behaviors:
 * - StorageMoveTask: move to target location from TASK_QUEUE
 * - StorageWorkTask: FETCH/STORE operations
 * - CraftingWorkTask: CRAFT at crafting table
 * - InsertProcessingTask: PROCESS phase 1 - insert to furnace
 * - CollectProcessingTask: PROCESS phase 2 - collect from furnace
 */
public class AgentTask implements IMaidTask {

    public static final ResourceLocation TASK_ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "agent");

    @Override
    public @NotNull ResourceLocation getUid() {
        return TASK_ID;
    }

    @Override
    public @NotNull ItemStack getIcon() {
        return Items.ENDER_EYE.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(@NotNull EntityMaid maid) {
        return InitSounds.MAID_IDLE.get();
    }

    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> behaviors = new ArrayList<>();
        
        // Movement behavior for TaskQueue (priority 5 - higher)
        behaviors.add(Pair.of(5, new WorkBlockMoveTask()));  // Handles FETCH/STORE/CRAFT/PROCESS movement
        behaviors.add(Pair.of(5, new ExploreStoragesTask())); // Handles EXPLORE_ALL - dynamic storage discovery
        
        // Work behaviors (priority 5 - each handles specific task types when arrived)
        behaviors.add(Pair.of(5, new StorageWorkTask()));       // FETCH/STORE/EXPLORE operations
        behaviors.add(Pair.of(5, new CraftingWorkTask()));      // CRAFT at crafting table
        behaviors.add(Pair.of(5, new InsertWorkTask()));  // PROCESS insert phase

        behaviors.add(Pair.of(10, new CollectMoveTask())); // PROCESS collect phase
        behaviors.add(Pair.of(5, new CollectWorkTask()));
        return behaviors;
    }

    /**
     * Behaviors that run when the maid is riding something.
     * Currently no ride-specific behaviors for agent task.
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>();
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    @Override
    public boolean enableLookAndRandomWalk(@NotNull EntityMaid maid) {
        // Allow idle behaviors when no tasks are queued
        return true;
    }

    @Override
    public boolean enablePanic(@NotNull EntityMaid maid) {
        return true;
    }

    @Override
    public boolean enableEating(@NotNull EntityMaid maid) {
        return true;
    }
}

package com.github.fracture_hikari.maid_agent.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.maid.behavior.StorageMoveTask;
import com.github.fracture_hikari.maid_agent.maid.behavior.StorageWorkTask;
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
 * Maid "Agent" task - enables LLM-controlled storage operations.
 * 
 * When this task is selected for a maid, she can execute storage commands
 * from the LLM (via chat). The behaviors registered here consume memories
 * set by our LLM functions (StorageItemsFunction, GetNearbyStorageFunction).
 * 
 * Memory flow:
 * - StorageItemsFunction → sets TASK_QUEUE memory
 * - StorageMoveTask → reads TASK_QUEUE, moves maid to storage
 * - StorageWorkTask → reads TASK_QUEUE, executes fetch/store, triggers LLM callback
 */
public class AgentTask implements IMaidTask {

    // Unique identifier for this task
    public static final ResourceLocation TASK_ID = ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "agent");

    @Override
    public @NotNull ResourceLocation getUid() {
        return TASK_ID;
    }

    /**
     * Icon shown in the task selection GUI.
     * Using Ender Eye to represent "AI/Agent" capability.
     */
    @Override
    public @NotNull ItemStack getIcon() {
        return Items.ENDER_EYE.getDefaultInstance();
    }

    /**
     * Ambient sound played while maid is in this task.
     */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(@NotNull EntityMaid maid) {
        return InitSounds.MAID_IDLE.get();
    }

    /**
     * Create the AI behaviors for this task.
     * 
     * These behaviors run ONLY when the maid's task is set to "Agent".
     * They consume memories set by our LLM functions.
     * 
     * @param maid The maid entity
     * @return List of behaviors for storage operations
     */
    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> behaviors = new ArrayList<>();
        
        // Priority 5 = runs after core behaviors but before random walk
        // StorageMoveTask: finds and walks to storage when TASK_QUEUE has pending tasks
        behaviors.add(Pair.of(5, new StorageMoveTask()));
        
        // StorageWorkTask: executes fetch/store when arrived at storage
        behaviors.add(Pair.of(5, new StorageWorkTask()));
        
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

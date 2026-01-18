package io.github.fracture_hikari.maid_agent.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import io.github.fracture_hikari.maid_agent.MaidAgent;
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
 * A custom maid task (job) for the agent functionality.
 * 
 * NOTE: Storage behaviors are now injected via IExtraMaidBrain.getWorkBehaviors()
 * in MaidExtension.java, so they work for ALL task types.
 * 
 * This task is kept for compatibility but behaviors are now universal.
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
     * Storage behaviors are now registered via IExtraMaidBrain.getWorkBehaviors()
     * so they work universally for all task types.
     * 
     * @param maid The maid entity
     * @return List of task-specific behaviors (empty - using universal behaviors)
     */
    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // Behaviors are now injected via IExtraMaidBrain.getWorkBehaviors()
        // See MaidExtension.java
        return new ArrayList<>();
    }

    /**
     * Behaviors that run when the maid is riding something.
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

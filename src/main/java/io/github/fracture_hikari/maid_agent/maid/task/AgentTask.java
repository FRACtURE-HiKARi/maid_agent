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
 * This is the core interface for defining what a maid does.
 * The task provides:
 * - A unique ID
 * - An icon for the task selection UI
 * - Brain behaviors (AI) that run when this task is active
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
     * You can use any item as the icon.
     */
    @Override
    public @NotNull ItemStack getIcon() {
        // TODO: Choose a more fitting icon or create a custom item
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
     * Each Pair contains:
     * - Integer: Priority (lower = higher priority, runs first)
     * - BehaviorControl: The actual behavior logic
     * 
     * Behaviors run during the WORK activity when this task is selected.
     * 
     * @param maid The maid entity
     * @return List of prioritized behaviors
     */
    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        ArrayList<Pair<Integer, BehaviorControl<? super EntityMaid>>> list = new ArrayList<>();

        // TODO: Add your custom behaviors here
        // Example:
        // list.add(Pair.of(10, new AgentMoveBehavior()));
        // list.add(Pair.of(10, new AgentWorkBehavior()));

        return list;
    }

    /**
     * Behaviors that run when the maid is riding something (vehicle, seat, etc.)
     * These behaviors cannot make the maid move - only stationary actions.
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        ArrayList<Pair<Integer, BehaviorControl<? super EntityMaid>>> list = new ArrayList<>();

        // TODO: Add ride-mode behaviors if needed

        return list;
    }

    /**
     * Whether this task is currently available for selection.
     * You can add conditions here (e.g., require a specific item).
     */
    @Override
    public boolean isEnable(EntityMaid maid) {
        return true; // Always available
    }

    /**
     * Whether random walking/looking is enabled during this task.
     * Set to false if the maid needs to focus on specific work.
     */
    @Override
    public boolean enableLookAndRandomWalk(@NotNull EntityMaid maid) {
        return true;
    }

    /**
     * Whether panic behavior (running when hurt) is enabled.
     * Combat tasks typically disable this.
     */
    @Override
    public boolean enablePanic(@NotNull EntityMaid maid) {
        return true;
    }

    /**
     * Whether eating behavior is enabled.
     * Some tasks may need to disable this during critical work.
     */
    @Override
    public boolean enableEating(@NotNull EntityMaid maid) {
        return true;
    }
}

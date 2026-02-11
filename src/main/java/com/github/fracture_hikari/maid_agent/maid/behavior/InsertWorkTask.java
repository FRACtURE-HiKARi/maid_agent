package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.*;
import com.github.fracture_hikari.maid_agent.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;

/**
 * Behavior for inserting ingredients into furnace/processing machines.
 * Phase 1 of PROCESS task: insert ingredients + fuel, then wait.
 */
public class InsertWorkTask extends AbstractWorkTask {
    
    // Furnace slot indices
    private static final int FURNACE_INPUT_SLOT = 0;
    private static final int FURNACE_FUEL_SLOT = 1;
    private static final int FURNACE_OUTPUT_SLOT = 2;
    
    // Approximate ticks per smelt (200 ticks = 10 seconds)
    private static final int TICKS_PER_SMELT = 200;

    public InsertWorkTask() {
        super();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return false;
        
        if (!(taskOpt.get() instanceof ProcessTask pt)) {
            return false;
        }
        ProcessTask.WorkstationType ws = pt.getWorkstationType();
        return ws == ProcessTask.WorkstationType.FURNACE
            || ws == ProcessTask.WorkstationType.SMOKER
            || ws == ProcessTask.WorkstationType.BLAST_FURNACE;
    }

    @Override
    protected void handle(ServerLevel level, EntityMaid maid) {
        Optional<PendingTask> taskOpt = MemoryUtil.peekTask(maid);
        if (taskOpt.isEmpty()) return;
        
        if (!(taskOpt.get() instanceof ProcessTask task)) return;

        Optional<WorkBlockTarget> targetOpt = task.getTarget();
        if (targetOpt.isEmpty()) {
            return;
        }

        BlockPos machinePos = getWorkBlockPos(level, maid, task);
        BlockEntity be = level.getBlockEntity(machinePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            MemoryUtil.updateTasks(maid, task, false, "Failed to process: block is not a furnace");
            return;
        }
        
        // Get pre-evaluated slot mappings from task (set by CraftItemFunction)
        List<SlotMapping> slotMappings = task.getSlotMappings();
        if (slotMappings == null || slotMappings.isEmpty()) {
            MemoryUtil.updateTasks(maid, task, false, "Failed to process: no slot mappings specified in task");
            return;
        }
        
        IItemHandler maidInv = maid.getAvailableInv(false);
        int inputInserted = 0;
        int fuelInserted = 0;
        String inputItemId = null;
        
        // Process each slot mapping - no burn-time detection, use explicit slots
        for (var mapping : slotMappings) {
            ItemStack itemToInsert = mapping.item();
            int needed = mapping.getCount();
            int targetSlot = mapping.slot();
            
            int inserted = insertItem(maidInv, furnace, itemToInsert, needed, targetSlot);
            
            if (targetSlot == FURNACE_INPUT_SLOT) {
                inputInserted += inserted;
                inputItemId = ItemIdUtils.getId(itemToInsert);
                MaidAgent.LOGGER.debug("InsertProcessingTask: Inserted {} input {} to slot {}", inserted, inputItemId, targetSlot);
            } else if (targetSlot == FURNACE_FUEL_SLOT) {
                fuelInserted += inserted;
                MaidAgent.LOGGER.debug("InsertProcessingTask: Inserted {} fuel {} to slot {}", inserted, ItemIdUtils.getId(itemToInsert), targetSlot);
            }
        }
        
        if (inputInserted == 0) {
            MemoryUtil.updateTasks(maid, task, false, "no ingredients.");
            return;
        }
        
        if (fuelInserted == 0) {
            MaidAgent.LOGGER.warn("No fuel inserted, furnace may not process");
        }
        
        // Create processing job in memory
        JobMemory memory = MemoryUtil.getOrCreateJobMemory(maid);
        ItemStack expectedOutput = task.getRequestedItem().copy();
        expectedOutput.setCount(inputInserted);
        
        int estimatedTicks = inputInserted * TICKS_PER_SMELT;
        // Output slot for furnace is 2
        ProcessingJob job = new ProcessingJob(maid, machinePos, FURNACE_OUTPUT_SLOT, expectedOutput, task);
        memory.addJob(job);

        MaidAgent.LOGGER.debug("Started processing job: {} input ({}), {} fuel, estimated {} ticks",
                inputInserted, inputItemId, fuelInserted, estimatedTicks);
        
        task.setActualCount(inputInserted);
    }
    
    /**
     * Insert a specific item into furnace slot.
     */
    private int insertItem(IItemHandler maidInv, AbstractFurnaceBlockEntity furnace, 
                                ItemStack targetItem, int maxCount, int furnaceSlot) {
        int inserted = 0;
        
        for (int slot = 0; slot < maidInv.getSlots() && inserted < maxCount; slot++) {
            ItemStack inSlot = maidInv.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            
            // Check if item matches target
            if (!ItemStack.isSameItem(inSlot, targetItem)) continue;
            
            int toExtract = Math.min(maxCount - inserted, inSlot.getCount());
            ItemStack extracted = maidInv.extractItem(slot, toExtract, false);
            if (extracted.isEmpty()) continue;
            
            ItemStack existing = furnace.getItem(furnaceSlot);
            if (existing.isEmpty()) {
                furnace.setItem(furnaceSlot, extracted);
                inserted += extracted.getCount();
            } else if (ItemStack.isSameItem(existing, extracted)) {
                int canAdd = Math.min(extracted.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(canAdd);
                inserted += canAdd;
                // Put back excess
                if (canAdd < extracted.getCount()) {
                    extracted.shrink(canAdd);
                    for (int i = 0; i < maidInv.getSlots(); i++) {
                        extracted = maidInv.insertItem(i, extracted, false);
                        if (extracted.isEmpty()) break;
                    }
                }
            } else {
                // Put back - slot occupied with different item
                for (int i = 0; i < maidInv.getSlots(); i++) {
                    extracted = maidInv.insertItem(i, extracted, false);
                    if (extracted.isEmpty()) break;
                }
            }
        }
        
        return inserted;
    }
}

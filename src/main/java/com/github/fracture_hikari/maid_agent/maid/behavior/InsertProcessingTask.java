package com.github.fracture_hikari.maid_agent.maid.behavior;

import com.github.fracture_hikari.maid_agent.maid.memory.SlotMapping;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.github.fracture_hikari.maid_agent.registry.MemoryModuleRegistry;
import com.github.fracture_hikari.maid_agent.maid.memory.PendingTask;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingJob;
import com.github.fracture_hikari.maid_agent.maid.memory.ProcessingMemory;
import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

/**
 * Behavior for inserting ingredients into furnace/processing machines.
 * Phase 1 of PROCESS task: insert ingredients + fuel, then wait.
 */
public class InsertProcessingTask extends AbstractWorkTask {
    
    // Furnace slot indices
    private static final int FURNACE_INPUT_SLOT = 0;
    private static final int FURNACE_FUEL_SLOT = 1;
    private static final int FURNACE_OUTPUT_SLOT = 2;
    
    // Approximate ticks per smelt (200 ticks = 10 seconds)
    private static final int TICKS_PER_SMELT = 200;

    public InsertProcessingTask() {
        super();
    }

    @Override
    protected boolean canHandle(PendingTask task) {
        if (task.getType() != PendingTask.TaskType.PROCESS) {
            return false;
        }
        PendingTask.WorkstationType ws = task.getWorkstationType();
        return ws == PendingTask.WorkstationType.FURNACE
            || ws == PendingTask.WorkstationType.SMOKER
            || ws == PendingTask.WorkstationType.BLAST_FURNACE;
    }

    @Override
    protected String performWork(ServerLevel level, EntityMaid maid, PendingTask task) {
        BlockPos machinePos = task.getTarget() != null ? task.getTarget().getPos() : null;
        if (machinePos == null) {
            task.fail("No target");
            return "Failed to process: no furnace location";
        }
        
        BlockEntity be = level.getBlockEntity(machinePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            task.fail("Not a furnace");
            return "Failed to process: block is not a furnace";
        }
        
        // Get pre-evaluated slot mappings from task (set by CraftItemFunction)
        java.util.List<SlotMapping> slotMappings = task.getSlotMappings();
        if (slotMappings == null || slotMappings.isEmpty()) {
            task.fail("No slot mappings");
            return "Failed to process: no slot mappings specified in task";
        }
        
        IItemHandler maidInv = maid.getAvailableInv(false);
        int inputInserted = 0;
        int fuelInserted = 0;
        String inputItemId = null;
        
        // Process each slot mapping - no burn-time detection, use explicit slots
        for (var mapping : slotMappings) {
            String itemId = mapping.getItemId();
            int needed = mapping.getCount();
            int targetSlot = mapping.slot();
            
            int inserted = insertItemById(maidInv, furnace, itemId, needed, targetSlot);
            
            if (targetSlot == FURNACE_INPUT_SLOT) {
                inputInserted += inserted;
                inputItemId = itemId;
                MaidAgent.LOGGER.debug("InsertProcessingTask: Inserted {} input {} to slot {}", inserted, itemId, targetSlot);
            } else if (targetSlot == FURNACE_FUEL_SLOT) {
                fuelInserted += inserted;
                MaidAgent.LOGGER.debug("InsertProcessingTask: Inserted {} fuel {} to slot {}", inserted, itemId, targetSlot);
            }
        }
        
        if (inputInserted == 0) {
            task.fail("No ingredients");
            return "Failed to process: no matching ingredients in inventory";
        }
        
        if (fuelInserted == 0) {
            MaidAgent.LOGGER.warn("No fuel inserted, furnace may not process");
        }
        
        // Create processing job in memory
        ProcessingMemory memory = getOrCreateMemory(maid);
        ItemStack expectedOutput = ItemIdUtils.createStack(task.getItemId());
        expectedOutput.setCount(inputInserted);
        
        int estimatedTicks = inputInserted * TICKS_PER_SMELT;
        // Output slot for furnace is 2
        ProcessingJob job = new ProcessingJob(machinePos, FURNACE_OUTPUT_SLOT, expectedOutput);
        memory.addJob(job);
        
        MaidAgent.LOGGER.info("Started processing job: {} input ({}), {} fuel, estimated {} ticks", 
                inputInserted, inputItemId, fuelInserted, estimatedTicks);
        
        task.complete("Inserted ingredients", inputInserted);
        return String.format("Inserted %d items + %d fuel into furnace. Processing will take ~%d seconds.", 
                inputInserted, fuelInserted, estimatedTicks / 20);
    }
    
    /**
     * Insert a specific item by ID into furnace slot.
     */
    private int insertItemById(IItemHandler maidInv, AbstractFurnaceBlockEntity furnace, 
                                String targetItemId, int maxCount, int furnaceSlot) {
        int inserted = 0;
        
        for (int slot = 0; slot < maidInv.getSlots() && inserted < maxCount; slot++) {
            ItemStack inSlot = maidInv.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            
            String slotItemId = ItemIdUtils.getId(inSlot);
            if (!slotItemId.equals(targetItemId)) continue;
            
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
    
    private ProcessingMemory getOrCreateMemory(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleRegistry.PROCESSING_JOBS.get())
                .orElseGet(() -> {
                    ProcessingMemory memory = new ProcessingMemory();
                    maid.getBrain().setMemory(MemoryModuleRegistry.PROCESSING_JOBS.get(), memory);
                    return memory;
                });
    }
}

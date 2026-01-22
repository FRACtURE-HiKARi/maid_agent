package com.github.fracture_hikari.maid_agent.maid.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Represents an active processing job (furnace, machine, etc.).
 * Created when ingredients are inserted, completed when output is collected.
 * 
 * Timing is handled by behavior cooldown, not job timer.
 */
public class ProcessingJob {
    
    public enum ProcessingState {
        WAITING,      // Ingredients inserted, waiting for output
        COLLECTED     // Done
    }
    
    private final String id;
    private final BlockPos outputEndpoint;   // Where to collect output
    private final int outputSlot;            // Slot to read output from
    private final ItemStack expectedOutput;
    private ProcessingState state;
    private int collectedCount;              // How many items collected so far
    
    /**
     * Create a new processing job.
     */
    public ProcessingJob(BlockPos outputEndpoint, int outputSlot, ItemStack expectedOutput) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.outputEndpoint = outputEndpoint;
        this.outputSlot = outputSlot;
        this.expectedOutput = expectedOutput.copy();
        this.state = ProcessingState.WAITING;
        this.collectedCount = 0;
    }
    
    public String getId() {
        return id;
    }
    
    public BlockPos getOutputEndpoint() {
        return outputEndpoint;
    }
    
    public int getOutputSlot() {
        return outputSlot;
    }
    
    public ItemStack getExpectedOutput() {
        return expectedOutput;
    }
    
    public ProcessingState getState() {
        return state;
    }
    
    public void setState(ProcessingState state) {
        this.state = state;
    }
    
    /**
     * Mark as collected (done).
     */
    public void markCollected() {
        this.state = ProcessingState.COLLECTED;
    }
    
    /**
     * Get expected output count.
     */
    public int getExpectedCount() {
        return expectedOutput.getCount();
    }
    
    /**
     * Get how many items have been collected so far.
     */
    public int getCollectedCount() {
        return collectedCount;
    }
    
    /**
     * Add to collected count and check if fully collected.
     * @return true if fully collected
     */
    public boolean addCollected(int count) {
        this.collectedCount += count;
        return isFullyCollected();
    }
    
    /**
     * Check if expected output has been fully collected.
     */
    public boolean isFullyCollected() {
        return collectedCount >= expectedOutput.getCount();
    }
    
    /**
     * Get remaining items to collect.
     */
    public int getRemainingToCollect() {
        return Math.max(0, expectedOutput.getCount() - collectedCount);
    }
    
    @Override
    public String toString() {
        return String.format("ProcessingJob{id=%s, state=%s, output=%s, collected=%d/%d}", 
                id, state, expectedOutput.getItem(), collectedCount, expectedOutput.getCount());
    }
}

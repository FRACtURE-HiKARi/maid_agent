package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Memory that tracks active processing jobs for a maid.
 * Used by behaviors to collect outputs.
 */
public class JobMemory {
    
    private final Set<ProcessingJob> activeJobs = new HashSet<>();
    private Set<BlockPos> outputEndpoints = new HashSet<>();
    private final EntityMaid maid;

    public JobMemory(EntityMaid maid) {
        this.maid = maid;
    }

    /**
     * Add a new processing job.
     */
    public void addJob(ProcessingJob job) {
        activeJobs.add(job);
        outputEndpoints.add(job.getOutputEndpoint());
    }
    
    /**
     * Get all active jobs (WAITING state - not yet fully collected).
     */
    public Set<ProcessingJob> getActiveJobs() {
        return activeJobs;
    }

    public Set<BlockPos> getOutputEndpoints() {
        return outputEndpoints;
    }
    
    /**
     * Check if there are any active jobs.
     */
    public boolean hasActiveJobs() {
        return !activeJobs.isEmpty();
    }

    private void updateOutputEndpoints() {
        outputEndpoints = activeJobs.stream().map(ProcessingJob::getOutputEndpoint).collect(Collectors.toSet());
    }

    public void removeJob(ProcessingJob job) {
        activeJobs.remove(job);
        updateOutputEndpoints();
    }
    
    /**
     * Get total job count.
     */
    public int size() {
        return activeJobs.size();
    }
    
    /**
     * Check if memory is empty.
     */
    public boolean isEmpty() {
        return activeJobs.isEmpty();
    }
    
    /**
     * Clear all jobs.
     */
    public void clear() {
        activeJobs.clear();
        updateOutputEndpoints();
    }
    
    public void completeJob(boolean success, String message, ProcessingJob finished) {
        // Just notify the blocked task, if any.
        // TaskQueue handles the actual LLM notification when the task completes.
        finished.updateBlockedTask(success, message);
        removeJob(finished);
    }
}

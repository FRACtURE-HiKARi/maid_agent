package com.github.fracture_hikari.maid_agent.maid.memory;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Memory that tracks active processing jobs for a maid.
 * Used by behaviors to collect outputs.
 */
public class ProcessingMemory {
    
    private final List<ProcessingJob> jobs = new ArrayList<>();
    
    /**
     * Add a new processing job.
     */
    public void addJob(ProcessingJob job) {
        jobs.add(job);
    }
    
    /**
     * Get all active jobs (WAITING state - not yet fully collected).
     */
    public List<ProcessingJob> getActiveJobs() {
        return jobs.stream()
                .filter(j -> j.getState() == ProcessingJob.ProcessingState.WAITING)
                .toList();
    }
    
    /**
     * Check if there are any active jobs.
     */
    public boolean hasActiveJobs() {
        return jobs.stream().anyMatch(j -> 
                j.getState() == ProcessingJob.ProcessingState.WAITING);
    }
    
    /**
     * Find job by output endpoint position.
     */
    public ProcessingJob findByOutputEndpoint(BlockPos pos) {
        return jobs.stream()
                .filter(j -> j.getOutputEndpoint().equals(pos))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find job by ID.
     */
    public ProcessingJob findById(String id) {
        return jobs.stream()
                .filter(j -> j.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Remove completed jobs.
     */
    public void cleanupCompleted() {
        jobs.removeIf(j -> j.getState() == ProcessingJob.ProcessingState.COLLECTED);
    }
    
    /**
     * Remove job by ID.
     */
    public boolean removeJob(String id) {
        Iterator<ProcessingJob> it = jobs.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(id)) {
                it.remove();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get total job count.
     */
    public int size() {
        return jobs.size();
    }
    
    /**
     * Check if memory is empty.
     */
    public boolean isEmpty() {
        return jobs.isEmpty();
    }
    
    /**
     * Clear all jobs.
     */
    public void clear() {
        jobs.clear();
    }
}

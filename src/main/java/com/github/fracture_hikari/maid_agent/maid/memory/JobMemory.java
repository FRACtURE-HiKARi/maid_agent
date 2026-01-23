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
public class JobMemory extends AbstractJobContainerWithAICallback<JobMemory.JobResult> {
    
    private final Set<ProcessingJob> activeJobs = new HashSet<>();
    private Set<BlockPos> outputEndpoints = new HashSet<>();

    public JobMemory(EntityMaid maid) {
        super(maid);
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
        super.clear();
    }
    
    public String getBatchSummary() {
        if (completedResults.isEmpty()) {
            return "No jobs completed.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Completed %d job(s):\n", completedResults.size()));

        int successCount = 0;
        int failCount = 0;
        int pendingCount = 0;

        for (int i = 0; i < completedResults.size(); i++) {
            JobResult result = completedResults.get(i);
            String status = result.success ? "Success" : "Failed";
            if (result.success) successCount++; else failCount++;

            sb.append(String.format("job %d at %s (%d/%s) - %s\n",
                    i + 1,
                    result.outputEndPoint,
                    result.actualCount,
                    result.expected,
                    status
            ));

            if (result.message != null && !result.message.isEmpty()) {
                sb.append(" (").append(result.message).append(")");
            }
            sb.append("\n");
        }

        for (ProcessingJob job: activeJobs) {
            sb.append(String.format("job %d at %s (%d/%s) - Pending\n",
                    pendingCount + 1,
                    job.getOutputEndpoint(),
                    job.getCollectedCount(),
                    job.getExpectedOutput()
            ));
            pendingCount++;
        }
        if (failCount > 0) {
            sb.append(String.format("\nSummary: %d succeeded, %d failed, %d pending", successCount, failCount, pendingCount));
        }

        return sb.toString().trim();

    }



    public void completeJob(boolean success, String message, ProcessingJob finished) {
        completedResults.add(new JobResult(
           finished.getOutputEndpoint(),
           finished.getId(),
           finished.getExpectedOutput(),
           finished.getCollectedCount(),
           success,
           message
        ));
        removeJob(finished);
        if (activeJobs.isEmpty()) {
            notifyBatchComplete();
        }
    }

    public record JobResult(
            BlockPos outputEndPoint,
            String id,
            ItemStack expected,
            int actualCount,
            boolean success,
            String message
    ) {}
}

package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.ai.AIChatCallback;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for memory components that track jobs/tasks and report back to AI.
 * @param <T> The type of result object tracked (e.g. JobResult, TaskResult)
 */
public abstract class AbstractJobContainerWithAICallback<T> {

    protected final List<T> completedResults = new ArrayList<>();
    protected AIChatCallback callback;

    public AbstractJobContainerWithAICallback(EntityMaid maid) {
        this.callback = new AIChatCallback(maid);
    }

    /**
     * Get a summary of the executed batch for the AI.
     */
    public abstract String getBatchSummary();

    /**
     * Notify LLM with batch summary.
     */
    public void notifyBatchComplete() {
        callback.notifyTaskComplete(getBatchSummary());
    }

    /**
     * Clear all state (jobs and results).
     */
    public void clear() {
        completedResults.clear();
    }

    /**
     * Get number of active items.
     */
    public abstract int size();

    /**
     * Check if empty.
     */
    public abstract boolean isEmpty();
}

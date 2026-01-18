package com.github.fracture_hikari.maid_agent.storage.memory;

import com.github.fracture_hikari.maid_agent.ai.AIChatCallback;
import com.github.fracture_hikari.maid_agent.storage.StorageTarget;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pending task for the maid to execute.
 * Set by LLM function calls, consumed by behaviors.
 */
public class PendingTask {
    
    public enum TaskType {
        FETCH,   // Get items from storage
        STORE,   // Put items into storage
        CRAFT    // Craft items at workstation
    }

    public enum TaskStatus {
        PENDING,    // Task just created, waiting to start
        MOVING,     // Maid is walking to target
        WORKING,    // Maid is interacting with target
        COMPLETED,  // Task finished successfully
        FAILED      // Task failed
    }

    private final TaskType type;
    private final String itemId;
    private final int count;
    @Nullable
    private StorageTarget target;
    private TaskStatus status;
    @Nullable
    private String resultMessage;
    private int actualCount; // Actual items fetched/stored/crafted
    private AIChatCallback callback;

    public PendingTask(EntityMaid maid, TaskType type, String itemId, int count) {
        this(maid, type, itemId, count, null);
    }

    public PendingTask(EntityMaid maid, TaskType type, String itemId, int count, @Nullable StorageTarget target) {
        this.type = type;
        this.itemId = itemId;
        this.count = count;
        this.target = target;
        this.status = TaskStatus.PENDING;
        this.resultMessage = null;
        this.actualCount = 0;
        this.callback = new AIChatCallback(maid); // TODO: replace this with proper client info.
    }

    public void notifyComplete() {
        this.callback.notifyTaskComplete(resultMessage);
    }
    public TaskType getType() {
        return type;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    @Nullable
    public StorageTarget getTarget() {
        return target;
    }

    public void setTarget(@Nullable StorageTarget target) {
        this.target = target;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @Nullable
    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(@Nullable String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public int getActualCount() {
        return actualCount;
    }

    public void setActualCount(int actualCount) {
        this.actualCount = actualCount;
    }

    public boolean isComplete() {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED;
    }

    public void complete(String message, int actualCount) {
        this.status = TaskStatus.COMPLETED;
        this.resultMessage = message;
        this.actualCount = actualCount;
    }

    public void fail(String message) {
        this.status = TaskStatus.FAILED;
        this.resultMessage = message;
    }

    @Override
    public String toString() {
        return String.format("PendingTask{type=%s, item=%s, count=%d, status=%s}",
                type, itemId, count, status);
    }
}

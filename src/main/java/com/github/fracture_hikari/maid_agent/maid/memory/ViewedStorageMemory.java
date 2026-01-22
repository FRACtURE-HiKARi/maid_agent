package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.storage.WorkBlockTarget;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Memory of discovered storage locations and their cached contents.
 * Updated when maid explores/interacts with storage blocks.
 */
public class ViewedStorageMemory {
    
    /**
     * Cached item count at a storage location.
     */
    public record ItemCount(ItemStack item, int count) {
        public ItemCount(ItemStack item) {
            this(item, item.getCount());
        }
    }

    // Map from storage target to list of items found there
    private final Map<WorkBlockTarget, List<ItemCount>> storageContents = new HashMap<>();
    
    // Positions we've already visited during this exploration session
    private final Set<WorkBlockTarget> visitedPositions = new HashSet<>();
    
    // Ordered list of storages for LLM indexed access
    private final List<WorkBlockTarget> indexedStorages = new ArrayList<>();
    
    // Timestamp of last update (game time in ticks)
    private long lastUpdated = 0;

    public ViewedStorageMemory() {
    }
    
    /**
     * Get the game time when this memory was last updated.
     */
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    /**
     * Set the last updated timestamp.
     */
    public void setLastUpdated(long gameTime) {
        this.lastUpdated = gameTime;
    }
    
    /**
     * Get staleness in seconds since last update.
     * @param currentGameTime Current game time in ticks
     * @return Seconds since last update
     */
    public int getStalenessSeconds(long currentGameTime) {
        return (int) ((currentGameTime - lastUpdated) / 20);
    }

    /**
     * Record items found at a storage location.
     */
    public void setContents(WorkBlockTarget target, List<ItemStack> items) {
        List<ItemCount> counts = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                // Merge same items
                boolean found = false;
                for (int i = 0; i < counts.size(); i++) {
                    if (ItemStack.isSameItemSameTags(counts.get(i).item(), stack)) {
                        counts.set(i, new ItemCount(counts.get(i).item(), 
                                counts.get(i).count() + stack.getCount()));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    counts.add(new ItemCount(stack.copyWithCount(1), stack.getCount()));
                }
            }
        }
        storageContents.put(target, counts);
        visitedPositions.add(target);
    }

    /**
     * Get cached contents of a storage location.
     */
    public List<ItemCount> getContents(WorkBlockTarget target) {
        return storageContents.getOrDefault(target, Collections.emptyList());
    }

    /**
     * Get all known storage locations.
     */
    public Set<WorkBlockTarget> getKnownStorages() {
        return new HashSet<>(storageContents.keySet());
    }

    /**
     * Find storage locations that contain a specific item.
     */
    public List<WorkBlockTarget> findStoragesWithItem(ItemStack target) {
        List<WorkBlockTarget> result = new ArrayList<>();
        for (Map.Entry<WorkBlockTarget, List<ItemCount>> entry : storageContents.entrySet()) {
            for (ItemCount ic : entry.getValue()) {
                if (ItemStack.isSameItemSameTags(ic.item(), target) && ic.count() > 0) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Get total count of an item across all known storages.
     */
    public int getTotalItemCount(ItemStack target) {
        int total = 0;
        for (List<ItemCount> counts : storageContents.values()) {
            for (ItemCount ic : counts) {
                if (ItemStack.isSameItemSameTags(ic.item(), target)) {
                    total += ic.count();
                }
            }
        }
        return total;
    }

    /**
     * Update cached count after extracting items.
     */
    public void recordExtraction(WorkBlockTarget target, ItemStack item, int count) {
        List<ItemCount> counts = storageContents.get(target);
        if (counts == null) return;
        
        for (int i = 0; i < counts.size(); i++) {
            if (ItemStack.isSameItemSameTags(counts.get(i).item(), item)) {
                int newCount = counts.get(i).count() - count;
                if (newCount <= 0) {
                    counts.remove(i);
                } else {
                    counts.set(i, new ItemCount(counts.get(i).item(), newCount));
                }
                break;
            }
        }
    }

    /**
     * Update cached count after inserting items.
     */
    public void recordInsertion(WorkBlockTarget target, ItemStack item, int count) {
        List<ItemCount> counts = storageContents.computeIfAbsent(target, k -> new ArrayList<>());
        
        for (int i = 0; i < counts.size(); i++) {
            if (ItemStack.isSameItemSameTags(counts.get(i).item(), item)) {
                counts.set(i, new ItemCount(counts.get(i).item(), counts.get(i).count() + count));
                return;
            }
        }
        counts.add(new ItemCount(item.copyWithCount(1), count));
    }

    /**
     * Check if we've visited a storage location.
     */
    public boolean hasVisited(WorkBlockTarget target) {
        return visitedPositions.contains(target);
    }

    /**
     * Mark a position as visited.
     */
    public void markVisited(WorkBlockTarget target) {
        visitedPositions.add(target);
    }

    /**
     * Clear visited positions for a new exploration session.
     */
    public void resetVisited() {
        visitedPositions.clear();
    }

    /**
     * Remove a storage from memory (e.g., if block was destroyed).
     */
    public void removeStorage(WorkBlockTarget target) {
        storageContents.remove(target);
        visitedPositions.remove(target);
    }

    /**
     * Clear all memory.
     */
    public void clear() {
        storageContents.clear();
        visitedPositions.clear();
        indexedStorages.clear();
    }

    /**
     * Clear indexed storages for fresh scan.
     */
    public void clearStorages() {
        indexedStorages.clear();
        storageContents.clear();
    }

    /**
     * Add a storage to the indexed list.
     */
    public void addStorage(WorkBlockTarget target, List<net.minecraft.world.item.ItemStack> contents) {
        indexedStorages.add(target);
        setContents(target, contents);
    }

    /**
     * Get storage by index (from get_nearby_storage result).
     */
    public Optional<WorkBlockTarget> getStorageByIndex(int index) {
        if (index >= 0 && index < indexedStorages.size()) {
            return Optional.of(indexedStorages.get(index));
        }
        return Optional.empty();
    }

    /**
     * Get the number of indexed storages.
     */
    public int getStorageCount() {
        return indexedStorages.size();
    }
}

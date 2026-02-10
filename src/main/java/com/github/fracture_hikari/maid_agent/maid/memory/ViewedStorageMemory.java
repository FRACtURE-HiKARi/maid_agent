package com.github.fracture_hikari.maid_agent.maid.memory;

import com.github.fracture_hikari.maid_agent.util.ItemIdUtils;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Memory of discovered storage locations and their cached contents.
 * Updated when maid explores/interacts with storage blocks.
 * Uses List<ItemStack> where each ItemStack holds aggregated count via getCount().
 */
public class ViewedStorageMemory {

    // Map from storage target to list of aggregated items (each ItemStack has merged count)
    private final Map<WorkBlockTarget, List<ItemStack>> storageContents = new HashMap<>();
    
    // Positions we've already visited during this exploration session
    private final Set<WorkBlockTarget> visitedPositions = new HashSet<>();
    
    // Ordered list of storages for LLM indexed access
    private final List<ViewedStorage> indexedStorages = new ArrayList<>();
    
    // Timestamp of last update (game time in ticks)
    private long lastUpdated = 0;

    public ViewedStorageMemory() {
    }

    private record ViewedStorage(WorkBlockTarget target, String blockName) {

    }

    /**
     * Merge an ItemStack into a list, aggregating counts for same item types.
     */
    private static void mergeStack(List<ItemStack> list, ItemStack stack) {
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        list.add(stack.copy());
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
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                mergeStack(merged, stack);
            }
        }
        storageContents.put(target, merged);
        visitedPositions.add(target);
    }

    /**
     * Get cached contents of a storage location.
     * Returns List<ItemStack> where each stack has aggregated count.
     */
    public List<ItemStack> getContents(WorkBlockTarget target) {
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
        for (Map.Entry<WorkBlockTarget, List<ItemStack>> entry : storageContents.entrySet()) {
            for (ItemStack stack : entry.getValue()) {
                if (ItemStack.isSameItemSameTags(stack, target) && stack.getCount() > 0) {
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
        for (List<ItemStack> stacks : storageContents.values()) {
            for (ItemStack stack : stacks) {
                if (ItemStack.isSameItemSameTags(stack, target)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Update cached count after extracting items.
     */
    public void recordExtraction(WorkBlockTarget target, ItemStack item, int count) {
        List<ItemStack> stacks = storageContents.get(target);
        if (stacks == null) return;
        
        for (int i = 0; i < stacks.size(); i++) {
            if (ItemStack.isSameItemSameTags(stacks.get(i), item)) {
                int newCount = stacks.get(i).getCount() - count;
                if (newCount <= 0) {
                    stacks.remove(i);
                } else {
                    stacks.get(i).setCount(newCount);
                }
                break;
            }
        }
    }

    /**
     * Update cached count after inserting items.
     */
    public void recordInsertion(WorkBlockTarget target, ItemStack item, int count) {
        List<ItemStack> stacks = storageContents.computeIfAbsent(target, k -> new ArrayList<>());
        
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameTags(stack, item)) {
                stack.grow(count);
                return;
            }
        }
        ItemStack newStack = item.copy();
        newStack.setCount(count);
        stacks.add(newStack);
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
    public void addStorage(WorkBlockTarget target, List<ItemStack> contents, String blockName) {
        indexedStorages.add(new ViewedStorage(target, blockName));
        setContents(target, contents);
    }

    /**
     * Get storage by index (from get_nearby_storage result).
     */
    public Optional<WorkBlockTarget> getStorageByIndex(int index) {
        if (index >= 0 && index < indexedStorages.size()) {
            return Optional.of(indexedStorages.get(index).target);
        }
        return Optional.empty();
    }

    /**
     * Get the number of indexed storages.
     */
    public int getStorageCount() {
        return indexedStorages.size();
    }

    /**
     * Generate a summary of all storage contents for AI.
     * Format:
     * [0] chest at (10,64,20):
     *   - minecraft:diamond x12
     *   - minecraft:iron_ingot x64
     */
    public String getStorageContentsSummary() {
        if (indexedStorages.isEmpty()) {
            return "No storages found.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d storage(s):\n", indexedStorages.size()));
        
        for (int i = 0; i < indexedStorages.size(); i++) {
            ViewedStorage viewed = indexedStorages.get(i);
            WorkBlockTarget target = viewed.target;
            String blockType = viewed.blockName;
            String pos = target.getPos().toShortString();
            
            sb.append(String.format("[%d] %s at %s", i, blockType, pos));
            
            List<ItemStack> contents = storageContents.get(target);
            if (contents == null || contents.isEmpty()) {
                sb.append(" (empty)\n");
            } else {
                sb.append(":\n");
                for (ItemStack stack : contents) {
                    sb.append(String.format("  - %s x%d\n", 
                        ItemIdUtils.getId(stack),
                        stack.getCount()));
                }
            }
        }
        
        return sb.toString().trim();
    }
}

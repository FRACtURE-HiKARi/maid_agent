package io.github.fracture_hikari.maid_agent.storage;

import io.github.fracture_hikari.maid_agent.MaidAgent;
import io.github.fracture_hikari.maid_agent.storage.handler.ItemHandlerStorageHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton registry for storage handlers.
 * Manages all registered IStorageHandler implementations.
 */
public class StorageManager {
    private static StorageManager instance;
    private final List<IStorageHandler> handlers = new ArrayList<>();

    private StorageManager() {
        // Register built-in handlers
        registerHandler(new ItemHandlerStorageHandler());
    }

    public static StorageManager getInstance() {
        if (instance == null) {
            instance = new StorageManager();
        }
        return instance;
    }

    /**
     * Register a new storage handler.
     * Handlers are checked in registration order, so more specific handlers
     * should be registered before more general ones.
     */
    public void registerHandler(IStorageHandler handler) {
        handlers.add(handler);
        MaidAgent.LOGGER.info("Registered storage handler: {}", handler.getType());
    }

    /**
     * Find a handler for the given block position.
     * 
     * @param level The server level
     * @param pos The block position
     * @param side Optional side to access from
     * @return The first handler that can handle this position, or empty if none
     */
    public Optional<IStorageHandler> findHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        for (IStorageHandler handler : handlers) {
            if (handler.isValidTarget(level, pos, side)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    /**
     * Get a handler by its type.
     */
    public Optional<IStorageHandler> getHandler(ResourceLocation type) {
        for (IStorageHandler handler : handlers) {
            if (handler.getType().equals(type)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if the given position is a valid storage target.
     * Returns a StorageTarget if valid, or empty if not.
     */
    public Optional<StorageTarget> isValidTarget(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return findHandler(level, pos, side)
                .map(handler -> new StorageTarget(handler.getType(), pos, side));
    }

    /**
     * Get all registered handlers.
     */
    public List<IStorageHandler> getHandlers() {
        return new ArrayList<>(handlers);
    }
}

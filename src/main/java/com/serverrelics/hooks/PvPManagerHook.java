package com.serverrelics.hooks;

import com.serverrelics.ServerRelics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hook for PvPManager plugin integration.
 * Forces PvP enabled for relic holders.
 *
 * Uses reflection to avoid hard dependency on PvPManager.
 */
public class PvPManagerHook {

    private final ServerRelics plugin;
    private boolean available = false;

    // Store original PvP state for players
    private final Map<UUID, Boolean> originalPvPState;

    // Reflection references - cached for performance
    private Object playerHandler;
    private Method getPlayerMethod;
    private Method setPvPMethod;
    private Method hasPvPEnabledMethod;

    public PvPManagerHook(ServerRelics plugin) {
        this.plugin = plugin;
        this.originalPvPState = new HashMap<>();
    }

    /**
     * Initialize the hook by checking if PvPManager is available
     */
    public void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PvPManager")) {
            plugin.getLogger().info("PvPManager not found, PvP forcing disabled.");
            return;
        }

        try {
            // Get PvPManager instance via reflection
            // Try new package path first (v4.x+), then fall back to old path
            Class<?> pvpManagerClass;
            try {
                pvpManagerClass = Class.forName("me.chancesd.pvpmanager.PvPManager");
                plugin.debug("Found PvPManager v4.x+ (new package path)");
            } catch (ClassNotFoundException e) {
                pvpManagerClass = Class.forName("me.NoChance.PvPManager.PvPManager");
                plugin.debug("Found PvPManager legacy version (old package path)");
            }
            Method getInstanceMethod = pvpManagerClass.getMethod("getInstance");
            Object pvpManagerInstance = getInstanceMethod.invoke(null);

            // Get PlayerHandler/PlayerManager (method name changed in v4.x)
            Method getPlayerHandlerMethod;
            try {
                getPlayerHandlerMethod = pvpManagerClass.getMethod("getPlayerManager");
                plugin.debug("Using getPlayerManager() (v4.x+ API)");
            } catch (NoSuchMethodException e) {
                getPlayerHandlerMethod = pvpManagerClass.getMethod("getPlayerHandler");
                plugin.debug("Using getPlayerHandler() (legacy API)");
            }
            playerHandler = getPlayerHandlerMethod.invoke(pvpManagerInstance);

            // Find the get(Player) method
            Class<?> playerHandlerClass = playerHandler.getClass();
            for (Method m : playerHandlerClass.getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == Player.class) {
                    getPlayerMethod = m;
                    break;
                }
            }

            if (getPlayerMethod == null) {
                throw new NoSuchMethodException("Could not find get(Player) method in PlayerHandler");
            }

            // Get a test PvPlayer to find methods - we need an online player for this
            // Cache methods on first actual use instead
            available = true;
            plugin.getLogger().info("PvPManager integration enabled.");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into PvPManager: " + e.getMessage());
            if (plugin.isDebug()) {
                e.printStackTrace();
            }
            available = false;
        }
    }

    /**
     * Find and cache the PvPlayer methods (called lazily on first use)
     */
    private void cachePvPlayerMethods(Object pvPlayer) {
        if (setPvPMethod != null && hasPvPEnabledMethod != null) return;

        for (Method m : pvPlayer.getClass().getMethods()) {
            if (m.getName().equals("setPvP") && m.getParameterCount() == 1) {
                setPvPMethod = m;
            } else if (m.getName().equals("hasPvPEnabled") && m.getParameterCount() == 0) {
                hasPvPEnabledMethod = m;
            }
        }

        if (setPvPMethod != null) {
            plugin.debug("Found PvPManager setPvP method: " + setPvPMethod);
        } else {
            plugin.getLogger().warning("Could not find setPvP method in PvPManager - PvP forcing will not work!");
        }
    }

    /**
     * Force PvP enabled for a player
     */
    public void forcePvPOn(Player player) {
        if (!available) return;

        try {
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer == null) {
                plugin.debug("PvPlayer is null for " + player.getName());
                return;
            }

            // Cache methods on first use
            cachePvPlayerMethods(pvPlayer);

            // Store original state if not already stored
            if (!originalPvPState.containsKey(player.getUniqueId())) {
                boolean currentState = getPvPStateInternal(pvPlayer);
                originalPvPState.put(player.getUniqueId(), currentState);
                plugin.debug("Stored original PvP state for " + player.getName() + ": " + currentState);
            }

            // Force PvP on
            if (setPvPMethod != null) {
                setPvPMethod.invoke(pvPlayer, true);
                plugin.debug("Forced PvP on for " + player.getName());
            }
        } catch (Exception e) {
            plugin.debug("Failed to force PvP on for " + player.getName() + ": " + e.getMessage());
            if (plugin.isDebug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Ensure PvP stays on for a player (called periodically or after events)
     */
    public void enforcePvP(Player player) {
        if (!available || !isPvPForced(player)) return;

        try {
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer == null) return;

            // Check current state
            boolean currentState = getPvPStateInternal(pvPlayer);
            if (!currentState && setPvPMethod != null) {
                // PvP was turned off somehow, force it back on
                setPvPMethod.invoke(pvPlayer, true);
                plugin.debug("Re-enforced PvP on for " + player.getName());
            }
        } catch (Exception e) {
            plugin.debug("Failed to enforce PvP for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Schedule a PvP re-enforcement after a short delay
     * (useful after commands or events that might change PvP state)
     */
    public void scheduleEnforcePvP(Player player) {
        if (!available || !isPvPForced(player)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && isPvPForced(player)) {
                    enforcePvP(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Restore player's original PvP state
     */
    public void restorePvP(Player player) {
        if (!available) return;

        Boolean originalState = originalPvPState.remove(player.getUniqueId());
        if (originalState == null) return;

        try {
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer != null && setPvPMethod != null) {
                setPvPMethod.invoke(pvPlayer, originalState);
                plugin.debug("Restored PvP state for " + player.getName() + " to " + originalState);
            }
        } catch (Exception e) {
            plugin.debug("Failed to restore PvP for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get current PvP state for a player
     */
    public boolean getPvPState(Player player) {
        if (!available) return true;

        try {
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer != null) {
                cachePvPlayerMethods(pvPlayer);
                return getPvPStateInternal(pvPlayer);
            }
        } catch (Exception e) {
            plugin.debug("Failed to get PvP state for " + player.getName() + ": " + e.getMessage());
        }

        return true; // Default to PvP enabled
    }

    /**
     * Internal method to get PvP state from a PvPlayer object
     */
    private boolean getPvPStateInternal(Object pvPlayer) {
        if (hasPvPEnabledMethod == null) return true;

        try {
            return (Boolean) hasPvPEnabledMethod.invoke(pvPlayer);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Check if player's PvP is currently forced by us
     */
    public boolean isPvPForced(Player player) {
        return originalPvPState.containsKey(player.getUniqueId());
    }

    /**
     * Check if PvPManager integration is available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Clean up when player leaves
     */
    public void cleanup(Player player) {
        originalPvPState.remove(player.getUniqueId());
    }
}

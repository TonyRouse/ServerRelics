package com.serverrelics.hooks;

import com.serverrelics.ServerRelics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

    // Reflection references
    private Object pvpManagerInstance;
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
            Class<?> pvpManagerClass = Class.forName("me.NoChance.PvPManager.PvPManager");
            Method getInstanceMethod = pvpManagerClass.getMethod("getInstance");
            pvpManagerInstance = getInstanceMethod.invoke(null);

            // Get PlayerHandler
            Method getPlayerHandlerMethod = pvpManagerClass.getMethod("getPlayerHandler");
            playerHandler = getPlayerHandlerMethod.invoke(pvpManagerInstance);

            // Get methods we need
            Class<?> playerHandlerClass = playerHandler.getClass();
            getPlayerMethod = playerHandlerClass.getMethod("get", Player.class);

            // Get PvPlayer class methods
            Class<?> pvPlayerClass = Class.forName("me.NoChance.PvPManager.Player.nametag.BukkitNametag");
            // Actually need the interface/base class
            pvPlayerClass = Class.forName("me.NoChance.PvPManager.Player.CancelResult");

            // Try different approach - get any returned PvPlayer
            for (Method m : playerHandlerClass.getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1) {
                    getPlayerMethod = m;
                    break;
                }
            }

            available = true;
            plugin.getLogger().info("PvPManager integration enabled.");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into PvPManager: " + e.getMessage());
            plugin.debug("PvPManager hook error: " + e.toString());
            available = false;
        }
    }

    /**
     * Force PvP enabled for a player
     */
    public void forcePvPOn(Player player) {
        if (!available) return;

        try {
            // Store original state
            if (!originalPvPState.containsKey(player.getUniqueId())) {
                boolean currentState = getPvPState(player);
                originalPvPState.put(player.getUniqueId(), currentState);
            }

            // Force PvP on via reflection
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer != null) {
                // Try to find and call setPvP method
                for (Method m : pvPlayer.getClass().getMethods()) {
                    if (m.getName().equals("setPvP") && m.getParameterCount() == 1) {
                        m.invoke(pvPlayer, true);
                        plugin.debug("Forced PvP on for " + player.getName());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("Failed to force PvP on for " + player.getName() + ": " + e.getMessage());
        }
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
            if (pvPlayer != null) {
                for (Method m : pvPlayer.getClass().getMethods()) {
                    if (m.getName().equals("setPvP") && m.getParameterCount() == 1) {
                        m.invoke(pvPlayer, originalState);
                        plugin.debug("Restored PvP state for " + player.getName() + " to " + originalState);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("Failed to restore PvP for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get current PvP state for a player
     */
    private boolean getPvPState(Player player) {
        if (!available) return true;

        try {
            Object pvPlayer = getPlayerMethod.invoke(playerHandler, player);
            if (pvPlayer != null) {
                for (Method m : pvPlayer.getClass().getMethods()) {
                    if (m.getName().equals("hasPvPEnabled") && m.getParameterCount() == 0) {
                        return (Boolean) m.invoke(pvPlayer);
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("Failed to get PvP state for " + player.getName() + ": " + e.getMessage());
        }

        return true; // Default to PvP enabled
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

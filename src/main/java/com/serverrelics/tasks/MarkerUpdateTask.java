package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task that periodically updates BlueMap markers for all relics.
 * Updates positions for holders that have moved.
 */
public class MarkerUpdateTask extends BukkitRunnable {

    private final ServerRelics plugin;

    public MarkerUpdateTask(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Update all markers via the BlueMap hook
        if (plugin.getBlueMapHook().isEnabled()) {
            plugin.getBlueMapHook().updateAllMarkers();
        }
    }
}

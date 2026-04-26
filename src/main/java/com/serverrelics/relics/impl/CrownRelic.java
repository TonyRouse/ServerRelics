package com.serverrelics.relics.impl;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.entity.Player;

/**
 * The Crown of the Server - the flagship relic.
 *
 * Special behaviors:
 * - Must be worn in helmet slot to be active
 * - Grants combat buffs and glowing effect
 * - Forces PvP enabled
 * - Always drops on death (bypasses graves)
 * - Never despawns when dropped
 * - Shows on BlueMap
 *
 * Most behavior is configured in config.yml and handled by the base Relic class.
 * This class exists for any Crown-specific customizations.
 */
public class CrownRelic extends Relic {

    public CrownRelic(ServerRelics plugin, String id) {
        super(plugin, id);
    }

    @Override
    protected void onActivate(Player player) {
        super.onActivate(player);

        // Crown-specific activation logic
        plugin.debug("Crown activated for " + player.getName());

        // Update BlueMap marker to show holder
        if (blueMapEnabled) {
            plugin.getBlueMapHook().updateMarker(this, player);
        }
    }

    @Override
    protected void onDeactivate(Player player) {
        super.onDeactivate(player);

        // Crown-specific deactivation logic
        plugin.debug("Crown deactivated for " + player.getName());
    }

    @Override
    public void onPickup(Player player) {
        super.onPickup(player);

        // Record acquisition in stats
        if (trackTimesAcquired) {
            plugin.getStatsManager().incrementAcquisitions(player.getUniqueId(), id);
        }
    }

    /**
     * The Crown can have special behavior for certain events.
     * For example, we could add particle effects, sounds, etc.
     */
    public void playCoronationEffect(Player player) {
        // Could add fireworks, sounds, particles here
        // For now, the base class handles everything needed
    }
}

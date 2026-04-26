package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Periodic task that ensures relic integrity:
 * - Saves state frequently for crash protection
 * - Detects missing/corrupted relics
 * - Checks for invalid holder states (creative mode, etc.)
 * - Handles world deletion scenarios
 */
public class RelicIntegrityTask extends BukkitRunnable {

    private final ServerRelics plugin;
    private int tickCount = 0;

    // Notify admins every 5 minutes (60 ticks per run * 5 = 300 ticks = 5 min if run every second)
    // We run every minute, so notify every 5 runs
    private static final int NOTIFY_INTERVAL = 5;

    public RelicIntegrityTask(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        // Save state every run (every minute)
        plugin.getRelicManager().saveState();

        // Check relic integrity
        for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
            checkRelicIntegrity(relicId);
        }

        // Check for creative mode holders
        checkCreativeHolders();
    }

    private void checkRelicIntegrity(String relicId) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) return;

        // Only check unique relics that should exist
        if (!relic.isUnique()) return;

        boolean existsInWorld = plugin.getRelicManager().relicExistsInWorld(relicId);

        if (!existsInWorld) {
            // Relic doesn't exist - this is expected if it hasn't been spawned yet
            // But if we have a holder or dropped location, something is wrong
            return;
        }

        // Check if holder exists and is valid
        UUID holderUuid = plugin.getRelicManager().getHolder(relicId);
        if (holderUuid != null) {
            Player holder = Bukkit.getPlayer(holderUuid);
            if (holder != null && holder.isOnline()) {
                // Holder is online - verify they actually have the item
                if (!playerHasRelic(holder, relic)) {
                    // They don't have it! Relic is lost
                    notifyAdminsRelicLost(relicId, "Holder " + holder.getName() +
                        " is online but doesn't have the relic in inventory");
                    plugin.getRelicManager().clearHolder(relicId);
                }
            }
            // If holder is offline, that's fine - they'll be checked on login
            return;
        }

        // Check if dropped location is valid
        Location droppedLoc = plugin.getRelicManager().getDroppedLocation(relicId);
        if (droppedLoc != null) {
            // Check if world still exists
            if (droppedLoc.getWorld() == null) {
                // World was deleted! Drop at main world spawn
                handleWorldDeleted(relicId, relic);
                return;
            }

            // Check if the item entity still exists
            Item itemEntity = plugin.getRelicManager().getDroppedItemEntity(relicId);
            if (itemEntity == null || !itemEntity.isValid() || itemEntity.isDead()) {
                // Item entity is gone! Relic is lost
                notifyAdminsRelicLost(relicId, "Dropped item entity no longer exists at " +
                    formatLocation(droppedLoc));
                plugin.getRelicManager().clearDroppedLocation(relicId);
            }
        }
    }

    private void handleWorldDeleted(String relicId, Relic relic) {
        // Get the main world spawn
        World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            notifyAdminsRelicLost(relicId, "World was deleted and no fallback world available");
            return;
        }

        Location spawnLoc = mainWorld.getSpawnLocation();
        plugin.getLogger().warning("World containing " + relicId + " was deleted! Respawning at main world spawn.");

        // Clear old tracking
        plugin.getRelicManager().clearDroppedLocation(relicId);

        // Spawn new relic at spawn
        Item newItem = plugin.getRelicManager().spawnRelic(relicId, spawnLoc);
        if (newItem != null) {
            // Notify admins
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("serverrelics.admin")) {
                    p.sendMessage(TextUtil.colorize("&6[Relics] &eWorld containing " + relicId +
                        " was deleted. Relic respawned at spawn: " + formatLocation(spawnLoc)));
                }
            }
        }
    }

    private void notifyAdminsRelicLost(String relicId, String reason) {
        // Only notify every NOTIFY_INTERVAL runs (every 5 minutes)
        if (tickCount % NOTIFY_INTERVAL != 0) return;

        String message = "&6[Relics] &c WARNING: Relic '" + relicId + "' may be lost! " + reason +
            " &7Use &e/relic spawn " + relicId + " &7to recreate it.";

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("serverrelics.admin")) {
                p.sendMessage(TextUtil.colorize(message));
            }
        }

        plugin.getLogger().warning("Relic " + relicId + " may be lost: " + reason);
    }

    private void checkCreativeHolders() {
        for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
            Player holder = plugin.getRelicManager().getHolderPlayer(relicId);
            if (holder != null && holder.getGameMode() == GameMode.CREATIVE) {
                Relic relic = plugin.getRelicRegistry().getRelic(relicId);
                if (relic != null) {
                    // Force drop - this is handled by PlayerStateListener but double-check here
                    plugin.getLogger().warning("Player " + holder.getName() +
                        " is in creative mode with " + relicId + " - should have been dropped");
                }
            }
        }
    }

    private boolean playerHasRelic(Player player, Relic relic) {
        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (relic.isThisRelic(item)) return true;
        }
        // Check armor
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (relic.isThisRelic(item)) return true;
        }
        // Check offhand
        if (relic.isThisRelic(player.getInventory().getItemInOffHand())) return true;

        return false;
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

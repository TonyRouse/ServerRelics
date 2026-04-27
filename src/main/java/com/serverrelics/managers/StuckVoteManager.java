package com.serverrelics.managers;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages voting for stuck relics.
 *
 * Players can vote that a dropped relic is stuck (inaccessible).
 * When enough votes are cast within the time window, the relic
 * is relocated to spawn or a configured location.
 */
public class StuckVoteManager {

    private final ServerRelics plugin;

    // Votes per relic: relicId -> list of (playerUuid, timestamp)
    private final Map<String, List<Vote>> votes;

    // Per-player cooldowns: playerUuid -> last vote timestamp
    private final Map<UUID, Long> cooldowns;

    public StuckVoteManager(ServerRelics plugin) {
        this.plugin = plugin;
        this.votes = new HashMap<>();
        this.cooldowns = new HashMap<>();
    }

    /**
     * Check if stuck voting is enabled
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("stuck.enabled", true);
    }

    /**
     * Get configured votes required (from config)
     */
    public int getConfiguredVotesRequired() {
        return plugin.getConfig().getInt("stuck.votes-required", 2);
    }

    /**
     * Get effective votes required, adjusted for online player count.
     * If only 1 player is online and config requires 2, we allow 1 vote.
     * Formula: min(configured, max(1, online_players))
     */
    public int getVotesRequired() {
        int configured = getConfiguredVotesRequired();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        // At least 1 vote required, but no more than online players or configured amount
        return Math.max(1, Math.min(configured, onlinePlayers));
    }

    /**
     * Get vote window in milliseconds
     */
    public long getVoteWindowMillis() {
        return plugin.getConfig().getInt("stuck.vote-window-minutes", 10) * 60 * 1000L;
    }

    /**
     * Get cooldown in milliseconds
     */
    public long getCooldownMillis() {
        return plugin.getConfig().getInt("stuck.cooldown-minutes", 60) * 60 * 1000L;
    }

    /**
     * Attempt to cast a vote for a stuck relic
     *
     * @param player The player voting
     * @param relicId The relic being voted as stuck
     * @return VoteResult indicating success/failure and reason
     */
    public VoteResult castVote(Player player, String relicId) {
        relicId = relicId.toLowerCase();

        // Check if enabled
        if (!isEnabled()) {
            return new VoteResult(false, VoteResultType.DISABLED, 0, 0);
        }

        // Check if relic exists and is dropped
        if (!plugin.getRelicManager().relicExistsInWorld(relicId)) {
            return new VoteResult(false, VoteResultType.NOT_IN_WORLD, 0, 0);
        }

        if (!plugin.getRelicManager().isDropped(relicId)) {
            return new VoteResult(false, VoteResultType.NOT_DROPPED, 0, 0);
        }

        // Check player cooldown
        Long lastVote = cooldowns.get(player.getUniqueId());
        if (lastVote != null) {
            long elapsed = System.currentTimeMillis() - lastVote;
            if (elapsed < getCooldownMillis()) {
                long remaining = getCooldownMillis() - elapsed;
                return new VoteResult(false, VoteResultType.ON_COOLDOWN, 0, 0, remaining);
            }
        }

        // Clean up old votes outside the window
        cleanupOldVotes(relicId);

        // Check if player already voted in this window
        List<Vote> relicVotes = votes.computeIfAbsent(relicId, k -> new ArrayList<>());
        for (Vote vote : relicVotes) {
            if (vote.playerUuid.equals(player.getUniqueId())) {
                return new VoteResult(false, VoteResultType.ALREADY_VOTED,
                    relicVotes.size(), getVotesRequired());
            }
        }

        // Cast the vote
        relicVotes.add(new Vote(player.getUniqueId(), System.currentTimeMillis()));
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        int currentVotes = relicVotes.size();
        int required = getVotesRequired();

        // Check if we've reached the threshold
        if (currentVotes >= required) {
            // Relocate the relic!
            Location newLocation = relocateRelic(relicId);
            if (newLocation != null) {
                // Clear votes for this relic
                votes.remove(relicId);
                return new VoteResult(true, VoteResultType.RELOCATED,
                    currentVotes, required, newLocation);
            }
        }

        return new VoteResult(true, VoteResultType.VOTE_CAST, currentVotes, required);
    }

    /**
     * Clean up votes outside the time window
     */
    private void cleanupOldVotes(String relicId) {
        List<Vote> relicVotes = votes.get(relicId);
        if (relicVotes == null) return;

        long windowStart = System.currentTimeMillis() - getVoteWindowMillis();
        relicVotes.removeIf(vote -> vote.timestamp < windowStart);
    }

    /**
     * Relocate a relic to spawn or configured location
     *
     * @return The new location, or null if failed
     */
    private Location relocateRelic(String relicId) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return null;

        // Get the dropped item
        Item droppedItem = plugin.getRelicManager().getDroppedItemEntity(relicId);
        Location oldLocation = plugin.getRelicManager().getDroppedLocation(relicId);

        // Determine new location
        Location newLocation = getRelocateLocation(oldLocation);
        if (newLocation == null) {
            plugin.getLogger().warning("Could not determine relocation target for " + relicId);
            return null;
        }

        // Remove old item if it exists
        if (droppedItem != null && droppedItem.isValid()) {
            droppedItem.remove();
        }

        // Clear old tracking so spawnRelic doesn't think it already exists
        plugin.getRelicManager().clearDroppedLocation(relicId);

        // Spawn at new location
        Item newItem = plugin.getRelicManager().spawnRelic(relicId, newLocation);
        if (newItem == null) {
            plugin.getLogger().warning("Failed to spawn relic at new location");
            return null;
        }

        plugin.debug("Relocated " + relicId + " to " + formatLocation(newLocation));

        // Update BlueMap
        if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
            plugin.getBlueMapHook().updateMarkerDropped(relic, newLocation);
        }

        return newLocation;
    }

    /**
     * Get the location to relocate to based on config
     */
    private Location getRelocateLocation(Location fallback) {
        String relocateTo = plugin.getConfig().getString("stuck.relocate-to", "spawn");

        if ("spawn".equalsIgnoreCase(relocateTo)) {
            // Use the world spawn from the relic's current world
            World world = fallback != null ? fallback.getWorld() : Bukkit.getWorlds().get(0);
            if (world != null) {
                return world.getSpawnLocation().add(0, 1, 0); // Slightly above spawn
            }
        } else if (relocateTo.contains(",")) {
            // Parse coordinates: "world,x,y,z"
            String[] parts = relocateTo.split(",");
            if (parts.length >= 4) {
                try {
                    World world = Bukkit.getWorld(parts[0].trim());
                    double x = Double.parseDouble(parts[1].trim());
                    double y = Double.parseDouble(parts[2].trim());
                    double z = Double.parseDouble(parts[3].trim());
                    if (world != null) {
                        return new Location(world, x, y, z);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid relocate-to coordinates: " + relocateTo);
                }
            }
        }

        // Fallback to main world spawn
        World mainWorld = Bukkit.getWorlds().get(0);
        return mainWorld != null ? mainWorld.getSpawnLocation().add(0, 1, 0) : null;
    }

    /**
     * Get current vote count for a relic
     */
    public int getCurrentVotes(String relicId) {
        cleanupOldVotes(relicId.toLowerCase());
        List<Vote> relicVotes = votes.get(relicId.toLowerCase());
        return relicVotes != null ? relicVotes.size() : 0;
    }

    /**
     * Clear all votes (for reload)
     */
    public void clearAll() {
        votes.clear();
        // Don't clear cooldowns - they should persist through reloads
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // Inner classes

    private static class Vote {
        final UUID playerUuid;
        final long timestamp;

        Vote(UUID playerUuid, long timestamp) {
            this.playerUuid = playerUuid;
            this.timestamp = timestamp;
        }
    }

    public enum VoteResultType {
        DISABLED,
        NOT_IN_WORLD,
        NOT_DROPPED,
        ON_COOLDOWN,
        ALREADY_VOTED,
        VOTE_CAST,
        RELOCATED
    }

    public static class VoteResult {
        private final boolean success;
        private final VoteResultType type;
        private final int currentVotes;
        private final int requiredVotes;
        private final long cooldownRemaining;
        private final Location newLocation;

        public VoteResult(boolean success, VoteResultType type, int currentVotes, int requiredVotes) {
            this(success, type, currentVotes, requiredVotes, 0, null);
        }

        public VoteResult(boolean success, VoteResultType type, int currentVotes, int requiredVotes, long cooldownRemaining) {
            this(success, type, currentVotes, requiredVotes, cooldownRemaining, null);
        }

        public VoteResult(boolean success, VoteResultType type, int currentVotes, int requiredVotes, Location newLocation) {
            this(success, type, currentVotes, requiredVotes, 0, newLocation);
        }

        public VoteResult(boolean success, VoteResultType type, int currentVotes, int requiredVotes,
                         long cooldownRemaining, Location newLocation) {
            this.success = success;
            this.type = type;
            this.currentVotes = currentVotes;
            this.requiredVotes = requiredVotes;
            this.cooldownRemaining = cooldownRemaining;
            this.newLocation = newLocation;
        }

        public boolean isSuccess() { return success; }
        public VoteResultType getType() { return type; }
        public int getCurrentVotes() { return currentVotes; }
        public int getRequiredVotes() { return requiredVotes; }
        public long getCooldownRemaining() { return cooldownRemaining; }
        public Location getNewLocation() { return newLocation; }
    }
}

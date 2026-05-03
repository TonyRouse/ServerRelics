package com.serverrelics.managers;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages relic state - who holds what, where dropped relics are, etc.
 *
 * State is persisted to a YAML file for server restarts.
 */
public class RelicManager {

    private final ServerRelics plugin;
    private final File stateFile;

    // Current holder UUID for each relic ID
    private final Map<String, UUID> holders;

    // When each holder acquired the relic (for reign time calculation)
    private final Map<String, Long> holdStartTimes;

    // Dropped relic locations (for relics not held by anyone)
    private final Map<String, Location> droppedLocations;

    // Dropped item entity UUIDs (for tracking the actual item entities)
    private final Map<String, UUID> droppedItemEntities;

    // When holder went offline (for expiration tracking)
    private final Map<String, Long> holderOfflineSince;

    // Last known location of offline holder
    private final Map<String, Location> holderLastLocation;

    // Previous holder tracking (for pickup cooldown)
    private final Map<String, UUID> previousHolders;
    private final Map<String, Long> previousHolderDropTimes;

    public RelicManager(ServerRelics plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
        this.holders = new HashMap<>();
        this.holdStartTimes = new HashMap<>();
        this.droppedLocations = new HashMap<>();
        this.droppedItemEntities = new HashMap<>();
        this.holderOfflineSince = new HashMap<>();
        this.holderLastLocation = new HashMap<>();
        this.previousHolders = new HashMap<>();
        this.previousHolderDropTimes = new HashMap<>();
    }

    /**
     * Load relic state from file
     */
    public void loadState() {
        if (!stateFile.exists()) {
            plugin.getLogger().info("No state file found, starting fresh.");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);

            ConfigurationSection relicsSection = config.getConfigurationSection("relics");
            if (relicsSection == null) return;

            for (String relicId : relicsSection.getKeys(false)) {
                ConfigurationSection relicState = relicsSection.getConfigurationSection(relicId);
                if (relicState == null) continue;

                // Load holder
                String holderStr = relicState.getString("holder");
                if (holderStr != null && !holderStr.isEmpty()) {
                    try {
                        UUID holderUuid = UUID.fromString(holderStr);
                        holders.put(relicId, holderUuid);

                        long startTime = relicState.getLong("hold-start-time", System.currentTimeMillis());
                        holdStartTimes.put(relicId, startTime);

                        plugin.debug("Loaded holder for " + relicId + ": " + holderUuid);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid holder UUID for " + relicId);
                    }
                }

                // Load dropped location
                String worldName = relicState.getString("dropped-world");
                if (worldName != null) {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        double x = relicState.getDouble("dropped-x");
                        double y = relicState.getDouble("dropped-y");
                        double z = relicState.getDouble("dropped-z");
                        Location loc = new Location(world, x, y, z);
                        droppedLocations.put(relicId, loc);
                        plugin.debug("Loaded dropped location for " + relicId + ": " + loc);
                    }
                }

                // Load offline timestamp and last location
                long offlineSince = relicState.getLong("holder-offline-since", 0);
                if (offlineSince > 0) {
                    holderOfflineSince.put(relicId, offlineSince);
                    plugin.debug("Loaded offline-since for " + relicId + ": " + offlineSince);
                }

                String lastLocWorld = relicState.getString("holder-last-world");
                if (lastLocWorld != null) {
                    World world = Bukkit.getWorld(lastLocWorld);
                    if (world != null) {
                        double x = relicState.getDouble("holder-last-x");
                        double y = relicState.getDouble("holder-last-y");
                        double z = relicState.getDouble("holder-last-z");
                        holderLastLocation.put(relicId, new Location(world, x, y, z));
                    }
                }

                // Load previous holder (for pickup cooldown)
                String prevHolderStr = relicState.getString("previous-holder");
                if (prevHolderStr != null && !prevHolderStr.isEmpty()) {
                    try {
                        UUID prevHolderUuid = UUID.fromString(prevHolderStr);
                        previousHolders.put(relicId, prevHolderUuid);

                        long dropTime = relicState.getLong("previous-holder-drop-time", 0);
                        if (dropTime > 0) {
                            previousHolderDropTimes.put(relicId, dropTime);
                        }
                        plugin.debug("Loaded previous holder for " + relicId + ": " + prevHolderUuid);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid previous holder UUID for " + relicId);
                    }
                }
            }

            plugin.getLogger().info("Loaded relic state from file.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load state file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save relic state to file
     */
    public void saveState() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
                String path = "relics." + relicId + ".";

                // Save holder
                UUID holder = holders.get(relicId);
                if (holder != null) {
                    config.set(path + "holder", holder.toString());
                    Long startTime = holdStartTimes.get(relicId);
                    if (startTime != null) {
                        config.set(path + "hold-start-time", startTime);
                    }
                }

                // Save dropped location
                Location droppedLoc = droppedLocations.get(relicId);
                if (droppedLoc != null && droppedLoc.getWorld() != null) {
                    config.set(path + "dropped-world", droppedLoc.getWorld().getName());
                    config.set(path + "dropped-x", droppedLoc.getX());
                    config.set(path + "dropped-y", droppedLoc.getY());
                    config.set(path + "dropped-z", droppedLoc.getZ());
                }

                // Save offline timestamp
                Long offlineSince = holderOfflineSince.get(relicId);
                if (offlineSince != null) {
                    config.set(path + "holder-offline-since", offlineSince);
                }

                // Save last known location
                Location lastLoc = holderLastLocation.get(relicId);
                if (lastLoc != null && lastLoc.getWorld() != null) {
                    config.set(path + "holder-last-world", lastLoc.getWorld().getName());
                    config.set(path + "holder-last-x", lastLoc.getX());
                    config.set(path + "holder-last-y", lastLoc.getY());
                    config.set(path + "holder-last-z", lastLoc.getZ());
                }

                // Save previous holder (for pickup cooldown)
                UUID prevHolder = previousHolders.get(relicId);
                if (prevHolder != null) {
                    config.set(path + "previous-holder", prevHolder.toString());
                    Long dropTime = previousHolderDropTimes.get(relicId);
                    if (dropTime != null) {
                        config.set(path + "previous-holder-drop-time", dropTime);
                    }
                }
            }

            config.save(stateFile);
            plugin.debug("Saved relic state to file.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save state file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== Holder Management ====================

    /**
     * Get the current holder of a relic
     */
    public UUID getHolder(String relicId) {
        return holders.get(relicId.toLowerCase());
    }

    /**
     * Get the player object for the current holder (if online)
     */
    public Player getHolderPlayer(String relicId) {
        UUID uuid = getHolder(relicId);
        return uuid != null ? Bukkit.getPlayer(uuid) : null;
    }

    /**
     * Set the holder of a relic
     */
    public void setHolder(String relicId, UUID playerUuid) {
        relicId = relicId.toLowerCase();
        UUID previousHolder = holders.get(relicId);

        // Clear dropped location since it's now held
        droppedLocations.remove(relicId);
        droppedItemEntities.remove(relicId);

        // Clear previous holder tracking since someone picked it up
        previousHolders.remove(relicId);
        previousHolderDropTimes.remove(relicId);

        // Handle previous holder
        if (previousHolder != null && !previousHolder.equals(playerUuid)) {
            // Save previous holder's time - wrapped in try-catch to not break pickup handling
            try {
                plugin.getStatsManager().finalizeHoldTime(previousHolder, relicId);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to finalize hold time for " + previousHolder + ": " + e.getMessage());
            }

            // Remove effects from previous holder if online
            Player prevPlayer = Bukkit.getPlayer(previousHolder);
            if (prevPlayer != null) {
                Relic relic = plugin.getRelicRegistry().getRelic(relicId);
                if (relic != null) {
                    relic.removeEffects(prevPlayer);
                }
            }
        }

        // Set new holder
        holders.put(relicId, playerUuid);
        holdStartTimes.put(relicId, System.currentTimeMillis());

        // Start tracking time for new holder - wrapped in try-catch
        try {
            plugin.getStatsManager().startHoldTime(playerUuid, relicId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start hold time for " + playerUuid + ": " + e.getMessage());
        }

        // Apply effects to new holder if online
        Player newPlayer = Bukkit.getPlayer(playerUuid);
        if (newPlayer != null) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (relic != null) {
                relic.applyEffects(newPlayer);
                relic.onPickup(newPlayer);
            }
        }

        plugin.debug("Set holder of " + relicId + " to " + playerUuid);
    }

    /**
     * Clear the holder of a relic (when dropped)
     */
    public void clearHolder(String relicId) {
        relicId = relicId.toLowerCase();
        UUID previousHolder = holders.remove(relicId);

        if (previousHolder != null) {
            // Track previous holder for pickup cooldown
            previousHolders.put(relicId, previousHolder);
            previousHolderDropTimes.put(relicId, System.currentTimeMillis());
            // Save their time - wrapped in try-catch to not break drop handling
            try {
                plugin.getStatsManager().finalizeHoldTime(previousHolder, relicId);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to finalize hold time for " + previousHolder + ": " + e.getMessage());
            }

            // Remove effects if online
            Player player = Bukkit.getPlayer(previousHolder);
            if (player != null) {
                Relic relic = plugin.getRelicRegistry().getRelic(relicId);
                if (relic != null) {
                    relic.removeEffects(player);
                }
            }
        }

        holdStartTimes.remove(relicId);
        plugin.debug("Cleared holder of " + relicId);
    }

    /**
     * Get the time the current holder started holding the relic
     */
    public long getHoldStartTime(String relicId) {
        Long time = holdStartTimes.get(relicId.toLowerCase());
        return time != null ? time : System.currentTimeMillis();
    }

    /**
     * Get the current reign time in seconds
     */
    public long getCurrentReignTime(String relicId) {
        Long startTime = holdStartTimes.get(relicId.toLowerCase());
        if (startTime == null) return 0;
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    // ==================== Dropped Relic Management ====================

    /**
     * Set the dropped location of a relic
     */
    public void setDroppedLocation(String relicId, Location location) {
        droppedLocations.put(relicId.toLowerCase(), location.clone());
        plugin.debug("Set dropped location of " + relicId + " to " + location);
    }

    /**
     * Get the dropped location of a relic
     */
    public Location getDroppedLocation(String relicId) {
        return droppedLocations.get(relicId.toLowerCase());
    }

    /**
     * Track the dropped item entity
     */
    public void setDroppedItemEntity(String relicId, Item item) {
        droppedItemEntities.put(relicId.toLowerCase(), item.getUniqueId());
    }

    /**
     * Get the dropped item entity
     */
    public Item getDroppedItemEntity(String relicId) {
        UUID entityUuid = droppedItemEntities.get(relicId.toLowerCase());
        if (entityUuid == null) return null;

        Location loc = droppedLocations.get(relicId.toLowerCase());
        if (loc == null || loc.getWorld() == null) return null;

        for (Item item : loc.getWorld().getEntitiesByClass(Item.class)) {
            if (item.getUniqueId().equals(entityUuid)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Clear dropped location (when picked up)
     */
    public void clearDroppedLocation(String relicId) {
        droppedLocations.remove(relicId.toLowerCase());
        droppedItemEntities.remove(relicId.toLowerCase());
    }

    /**
     * Check if a relic is currently dropped on the ground
     */
    public boolean isDropped(String relicId) {
        return droppedLocations.containsKey(relicId.toLowerCase()) && !holders.containsKey(relicId.toLowerCase());
    }

    /**
     * Get the current location of a relic (holder location or dropped location)
     */
    public Location getRelicLocation(String relicId) {
        relicId = relicId.toLowerCase();

        // Check if held by someone online
        UUID holder = holders.get(relicId);
        if (holder != null) {
            Player player = Bukkit.getPlayer(holder);
            if (player != null) {
                return player.getLocation();
            }
        }

        // Check dropped location
        return droppedLocations.get(relicId);
    }

    // ==================== Relic Existence Management ====================

    /**
     * Check if a relic exists in the world (held or dropped)
     */
    public boolean relicExistsInWorld(String relicId) {
        relicId = relicId.toLowerCase();
        return holders.containsKey(relicId) || droppedLocations.containsKey(relicId);
    }

    /**
     * Spawn a relic at a location (admin command)
     */
    public Item spawnRelic(String relicId, Location location) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return null;

        // Check if unique relic already exists
        if (relic.isUnique() && relicExistsInWorld(relicId)) {
            return null;
        }

        ItemStack item = relic.createItem();
        Item droppedItem = location.getWorld().dropItem(location, item);

        // Set unlimited lifetime
        droppedItem.setUnlimitedLifetime(true);

        // Track it
        setDroppedLocation(relicId, location);
        setDroppedItemEntity(relicId, droppedItem);

        return droppedItem;
    }

    /**
     * Remove a relic from existence (admin command)
     */
    public boolean despawnRelic(String relicId) {
        relicId = relicId.toLowerCase();

        // Remove from holder if held
        UUID holder = holders.get(relicId);
        if (holder != null) {
            Player player = Bukkit.getPlayer(holder);
            if (player != null) {
                // Find and remove the item from their inventory
                Relic relic = plugin.getRelicRegistry().getRelic(relicId);
                if (relic != null) {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (relic.isThisRelic(item)) {
                            player.getInventory().remove(item);
                            break;
                        }
                    }
                    // Check armor slots
                    for (ItemStack item : player.getInventory().getArmorContents()) {
                        if (relic.isThisRelic(item)) {
                            if (item.equals(player.getInventory().getHelmet())) {
                                player.getInventory().setHelmet(null);
                            } else if (item.equals(player.getInventory().getChestplate())) {
                                player.getInventory().setChestplate(null);
                            } else if (item.equals(player.getInventory().getLeggings())) {
                                player.getInventory().setLeggings(null);
                            } else if (item.equals(player.getInventory().getBoots())) {
                                player.getInventory().setBoots(null);
                            }
                            break;
                        }
                    }
                    relic.removeEffects(player);
                }
            }
            clearHolder(relicId);
        }

        // Remove dropped item entity
        Item droppedItem = getDroppedItemEntity(relicId);
        if (droppedItem != null && droppedItem.isValid()) {
            droppedItem.remove();
        }

        // Clear tracking
        droppedLocations.remove(relicId);
        droppedItemEntities.remove(relicId);

        return true;
    }

    /**
     * Give a relic to a player (admin command)
     */
    public boolean giveRelic(String relicId, Player player) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return false;

        // Check if unique relic already exists
        if (relic.isUnique() && relicExistsInWorld(relicId)) {
            return false;
        }

        // Despawn if it exists elsewhere
        if (relicExistsInWorld(relicId)) {
            despawnRelic(relicId);
        }

        // Create and give item
        ItemStack item = relic.createItem();
        player.getInventory().addItem(item);

        // Set holder
        setHolder(relicId, player.getUniqueId());

        return true;
    }

    /**
     * Take a relic from a player (admin command)
     */
    public boolean takeRelic(String relicId, Player player) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return false;

        // Check if this player has the relic
        UUID holder = getHolder(relicId);
        if (holder == null || !holder.equals(player.getUniqueId())) {
            return false;
        }

        // Remove from inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (relic.isThisRelic(item)) {
                player.getInventory().remove(item);
                break;
            }
        }
        // Check armor slots
        if (relic.isThisRelic(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(null);
        }

        // Clear holder and effects
        clearHolder(relicId);

        return true;
    }

    /**
     * Check if a player currently holds any relic
     */
    public boolean playerHoldsAnyRelic(Player player) {
        return holders.containsValue(player.getUniqueId());
    }

    /**
     * Get all relics held by a player
     */
    public List<String> getRelicsHeldBy(Player player) {
        List<String> held = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : holders.entrySet()) {
            if (entry.getValue().equals(player.getUniqueId())) {
                held.add(entry.getKey());
            }
        }
        return held;
    }

    // ==================== Offline Holder Management ====================

    /**
     * Mark holder as offline (they logged out with relic)
     */
    public void setHolderOffline(String relicId, Location lastLocation) {
        relicId = relicId.toLowerCase();
        holderOfflineSince.put(relicId, System.currentTimeMillis());
        if (lastLocation != null) {
            holderLastLocation.put(relicId, lastLocation.clone());
        }
        plugin.debug("Marked holder of " + relicId + " as offline");
    }

    /**
     * Clear offline status (holder came back online)
     */
    public void clearHolderOffline(String relicId) {
        relicId = relicId.toLowerCase();
        holderOfflineSince.remove(relicId);
        holderLastLocation.remove(relicId);
        plugin.debug("Cleared offline status for " + relicId);
    }

    /**
     * Check if holder is offline
     */
    public boolean isHolderOffline(String relicId) {
        return holderOfflineSince.containsKey(relicId.toLowerCase());
    }

    /**
     * Get when holder went offline
     */
    public Long getHolderOfflineSince(String relicId) {
        return holderOfflineSince.get(relicId.toLowerCase());
    }

    /**
     * Get last known location of offline holder
     */
    public Location getHolderLastLocation(String relicId) {
        return holderLastLocation.get(relicId.toLowerCase());
    }

    /**
     * Check if holder has been offline longer than expiration time
     */
    public boolean isHolderExpired(String relicId, int expirationDays) {
        Long offlineSince = holderOfflineSince.get(relicId.toLowerCase());
        if (offlineSince == null) return false;

        long expirationMillis = expirationDays * 24L * 60L * 60L * 1000L;
        return System.currentTimeMillis() - offlineSince > expirationMillis;
    }

    /**
     * Drop relic at random location within radius of last known position
     */
    public Item dropRelicFromOfflineHolder(String relicId, int radius) {
        relicId = relicId.toLowerCase();
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return null;

        Location lastLoc = holderLastLocation.get(relicId);
        if (lastLoc == null || lastLoc.getWorld() == null) {
            plugin.getLogger().warning("Cannot drop " + relicId + " - no last known location");
            return null;
        }

        // Find a safe random location within radius
        Location dropLoc = findSafeDropLocation(lastLoc, radius);
        if (dropLoc == null) {
            dropLoc = lastLoc; // Fallback to exact location
        }

        // Create and drop the item
        ItemStack item = relic.createItem();
        Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, item);

        // Configure the dropped item
        if (relic.getRestrictions().isNeverDespawn()) {
            droppedItem.setUnlimitedLifetime(true);
        }
        if (relic.getRestrictions().isIndestructible()) {
            droppedItem.setInvulnerable(true);
        }

        // Clear holder and offline tracking
        UUID previousHolder = holders.remove(relicId);
        holdStartTimes.remove(relicId);
        holderOfflineSince.remove(relicId);
        holderLastLocation.remove(relicId);

        // Track as dropped
        droppedLocations.put(relicId, dropLoc);
        droppedItemEntities.put(relicId, droppedItem.getUniqueId());

        plugin.debug("Dropped " + relicId + " from expired offline holder at " + dropLoc);

        return droppedItem;
    }

    /**
     * Find a safe location to drop item within radius
     */
    private Location findSafeDropLocation(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        java.util.Random random = new java.util.Random();

        // Try up to 10 times to find a safe location
        for (int attempt = 0; attempt < 10; attempt++) {
            int offsetX = random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = random.nextInt(radius * 2 + 1) - radius;

            int x = center.getBlockX() + offsetX;
            int z = center.getBlockZ() + offsetZ;

            // Get highest block at this location
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);

            // Check if it's a safe spot (not in lava, water, etc.)
            if (isSafeDropSpot(loc)) {
                return loc;
            }
        }

        // Fallback: just use highest block at center
        int y = world.getHighestBlockYAt(center.getBlockX(), center.getBlockZ());
        return new Location(world, center.getBlockX() + 0.5, y + 1, center.getBlockZ() + 0.5);
    }

    /**
     * Check if location is safe for dropping an item
     */
    private boolean isSafeDropSpot(Location loc) {
        if (loc.getWorld() == null) return false;

        org.bukkit.block.Block block = loc.getBlock();
        org.bukkit.block.Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);

        // Don't drop in liquid
        if (block.isLiquid() || below.isLiquid()) return false;

        // Don't drop in void
        if (loc.getY() < loc.getWorld().getMinHeight()) return false;

        // Make sure there's solid ground below
        return below.getType().isSolid();
    }

    /**
     * Get offline holder's name for display
     */
    public String getOfflineHolderName(String relicId) {
        UUID holder = getHolder(relicId);
        if (holder == null) return null;
        return Bukkit.getOfflinePlayer(holder).getName();
    }

    // ==================== Previous Holder Pickup Cooldown ====================

    /**
     * Get the previous holder of a relic
     */
    public UUID getPreviousHolder(String relicId) {
        return previousHolders.get(relicId.toLowerCase());
    }

    /**
     * Get when the previous holder dropped the relic
     */
    public Long getPreviousHolderDropTime(String relicId) {
        return previousHolderDropTimes.get(relicId.toLowerCase());
    }

    /**
     * Clear previous holder tracking (when someone else picks it up)
     */
    public void clearPreviousHolder(String relicId) {
        relicId = relicId.toLowerCase();
        previousHolders.remove(relicId);
        previousHolderDropTimes.remove(relicId);
    }

    /**
     * Check if a player can pick up a relic (respects previous holder cooldown)
     * @return remaining cooldown in seconds, or 0 if can pickup
     */
    public int getRemainingPickupCooldown(String relicId, UUID playerUuid) {
        relicId = relicId.toLowerCase();

        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return 0;

        int cooldownSeconds = relic.getRestrictions().getPreviousHolderPickupCooldown();
        if (cooldownSeconds <= 0) return 0;

        UUID previousHolder = previousHolders.get(relicId);
        if (previousHolder == null || !previousHolder.equals(playerUuid)) {
            return 0; // Not the previous holder, can pickup
        }

        Long dropTime = previousHolderDropTimes.get(relicId);
        if (dropTime == null) return 0;

        long elapsedSeconds = (System.currentTimeMillis() - dropTime) / 1000;
        long remaining = cooldownSeconds - elapsedSeconds;

        return remaining > 0 ? (int) remaining : 0;
    }

    /**
     * Check if a player can pick up a relic
     */
    public boolean canPickup(String relicId, UUID playerUuid) {
        return getRemainingPickupCooldown(relicId, playerUuid) == 0;
    }
}

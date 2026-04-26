package com.serverrelics.hooks;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hook for BlueMap integration.
 * Shows relic locations on the web map.
 */
public class BlueMapHook {

    private final ServerRelics plugin;
    private BlueMapAPI api;
    private boolean enabled = false;

    // Cache of marker sets per map
    private final Map<String, MarkerSet> markerSets;

    public BlueMapHook(ServerRelics plugin) {
        this.plugin = plugin;
        this.markerSets = new HashMap<>();
    }

    /**
     * Initialize BlueMap integration
     */
    public void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            plugin.getLogger().info("BlueMap not found, map markers disabled.");
            return;
        }

        // BlueMap may not be ready immediately, register for when it enables
        BlueMapAPI.onEnable(api -> {
            this.api = api;
            this.enabled = true;
            plugin.getLogger().info("BlueMap integration enabled.");

            // Initialize marker sets for all relics
            initializeMarkerSets();
        });

        BlueMapAPI.onDisable(api -> {
            this.api = null;
            this.enabled = false;
            this.markerSets.clear();
        });
    }

    /**
     * Initialize marker sets for all enabled relics
     */
    private void initializeMarkerSets() {
        if (api == null) return;

        for (Relic relic : plugin.getRelicRegistry().getEnabledRelics()) {
            if (!relic.isBlueMapEnabled()) continue;

            // Create marker set for each world/map
            for (BlueMapWorld bmWorld : api.getWorlds()) {
                for (BlueMapMap map : bmWorld.getMaps()) {
                    String markerSetId = relic.getMarkerSetId();

                    MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                    if (markerSet == null) {
                        markerSet = MarkerSet.builder()
                            .label(relic.getMarkerSetLabel())
                            .toggleable(true)
                            .defaultHidden(false)
                            .build();
                        map.getMarkerSets().put(markerSetId, markerSet);
                    }

                    markerSets.put(map.getId() + ":" + markerSetId, markerSet);
                }
            }

            plugin.debug("Initialized BlueMap marker set for " + relic.getId());
        }
    }

    /**
     * Update marker for a relic held by a player
     */
    public void updateMarker(Relic relic, Player holder) {
        if (!enabled || api == null || !relic.isBlueMapEnabled()) return;

        Location loc = holder.getLocation();
        String label = buildMarkerLabel(relic, holder.getName(), false);

        updateMarkerInternal(relic, loc, label);
    }

    /**
     * Update marker for a dropped relic
     */
    public void updateMarkerDropped(Relic relic, Location location) {
        if (!enabled || api == null || !relic.isBlueMapEnabled()) return;
        if (!relic.isShowWhenDropped()) return;

        String label = buildMarkerLabel(relic, null, true);
        updateMarkerInternal(relic, location, label);
    }

    /**
     * Update marker for offline holder (show last known location)
     */
    public void updateMarkerOffline(Relic relic, Location lastLocation) {
        if (!enabled || api == null || !relic.isBlueMapEnabled()) return;

        String holderName = null;
        var holder = plugin.getRelicManager().getHolder(relic.getId());
        if (holder != null) {
            holderName = Bukkit.getOfflinePlayer(holder).getName();
        }

        String label = buildMarkerLabel(relic, holderName, false) + " (Offline)";
        updateMarkerInternal(relic, lastLocation, label);
    }

    /**
     * Internal method to update marker position
     */
    private void updateMarkerInternal(Relic relic, Location location, String label) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Optional<BlueMapWorld> bmWorldOpt = api.getWorld(world);
        if (bmWorldOpt.isEmpty()) return;

        BlueMapWorld bmWorld = bmWorldOpt.get();

        for (BlueMapMap map : bmWorld.getMaps()) {
            String markerSetKey = map.getId() + ":" + relic.getMarkerSetId();
            MarkerSet markerSet = markerSets.get(markerSetKey);

            if (markerSet == null) {
                // Create marker set if it doesn't exist
                markerSet = MarkerSet.builder()
                    .label(relic.getMarkerSetLabel())
                    .toggleable(true)
                    .defaultHidden(false)
                    .build();
                map.getMarkerSets().put(relic.getMarkerSetId(), markerSet);
                markerSets.put(markerSetKey, markerSet);
            }

            // Create or update POI marker
            String markerId = relic.getMarkerId();
            POIMarker marker = POIMarker.builder()
                .label(label)
                .position(location.getX(), location.getY(), location.getZ())
                .build();

            markerSet.put(markerId, marker);

            plugin.debug("Updated BlueMap marker for " + relic.getId() + " at " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
    }

    /**
     * Remove marker for a relic
     */
    public void removeMarker(Relic relic) {
        if (!enabled || api == null) return;

        for (MarkerSet markerSet : markerSets.values()) {
            markerSet.remove(relic.getMarkerId());
        }

        plugin.debug("Removed BlueMap marker for " + relic.getId());
    }

    /**
     * Build the label text for a marker
     */
    private String buildMarkerLabel(Relic relic, String holderName, boolean isDropped) {
        StringBuilder label = new StringBuilder();
        label.append(TextUtil.stripColor(relic.getDisplayName()));

        if (isDropped) {
            label.append(" (Dropped)");
        } else if (holderName != null && relic.isShowHolderName()) {
            label.append(" - ").append(holderName);
        }

        return label.toString();
    }

    /**
     * Update all relic markers (called periodically)
     */
    public void updateAllMarkers() {
        if (!enabled || api == null) return;

        for (Relic relic : plugin.getRelicRegistry().getEnabledRelics()) {
            if (!relic.isBlueMapEnabled()) continue;

            String relicId = relic.getId();
            Location location = plugin.getRelicManager().getRelicLocation(relicId);

            if (location != null) {
                boolean isDropped = plugin.getRelicManager().isDropped(relicId);
                String holderName = null;

                if (!isDropped) {
                    Player holder = plugin.getRelicManager().getHolderPlayer(relicId);
                    if (holder != null) {
                        holderName = holder.getName();
                        location = holder.getLocation(); // Get current location
                    }
                }

                String label = buildMarkerLabel(relic, holderName, isDropped);
                updateMarkerInternal(relic, location, label);
            } else {
                // No known location, remove marker
                removeMarker(relic);
            }
        }
    }

    /**
     * Clean up markers on disable
     */
    public void cleanup() {
        if (!enabled || api == null) return;

        // Remove all relic markers
        for (Relic relic : plugin.getRelicRegistry().getAllRelics()) {
            removeMarker(relic);
        }

        markerSets.clear();
    }

    /**
     * Check if BlueMap integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

package com.serverrelics.relics;

import com.serverrelics.ServerRelics;
import com.serverrelics.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for all relics.
 * Provides common functionality and defines the contract for relic implementations.
 *
 * To create a new relic type:
 * 1. Extend this class
 * 2. Register it in RelicRegistry
 * 3. Add configuration in config.yml under relics.<id>
 */
public abstract class Relic {

    // PDC keys for identifying relics
    public static final NamespacedKey RELIC_TYPE_KEY = new NamespacedKey("serverrelics", "relic_type");
    public static final NamespacedKey RELIC_UUID_KEY = new NamespacedKey("serverrelics", "relic_uuid");

    protected final ServerRelics plugin;
    protected final String id;

    // Configuration
    protected boolean enabled;
    protected boolean unique;
    protected String displayName;
    protected Material material;
    protected String skullTexture;
    protected List<String> loreTemplate;
    protected boolean glowing;
    protected ActiveSlot activeSlot;
    protected List<PotionEffect> effects;
    protected RelicRestrictions restrictions;

    // BlueMap settings
    protected boolean blueMapEnabled;
    protected String markerSetId;
    protected String markerSetLabel;
    protected String markerId;
    protected String markerLabel;
    protected String markerIcon;
    protected int markerIconAnchorX;
    protected int markerIconAnchorY;
    protected int markerUpdateInterval;
    protected boolean showWhenDropped;
    protected boolean showHolderName;

    // Broadcast messages
    protected String broadcastOnPickup;
    protected String broadcastOnDeathDrop;
    protected String broadcastOnLogoutDrop;
    protected String broadcastOnOfflineExpired;
    protected String broadcastOnLocate;
    protected String broadcastOnLocateDropped;
    protected String broadcastOnLocateOffline;

    // Stats settings
    protected boolean trackTimeHeld;
    protected boolean trackKills;
    protected boolean trackDeaths;
    protected boolean trackTimesAcquired;

    /**
     * Create a new relic with the given id
     */
    public Relic(ServerRelics plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    /**
     * Load this relic's configuration from the config section
     */
    public void loadConfig(ConfigurationSection section) {
        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", true);
        unique = section.getBoolean("unique", true);

        // Display settings
        ConfigurationSection display = section.getConfigurationSection("display");
        if (display != null) {
            displayName = display.getString("name", "&f" + id);
            String materialStr = display.getString("material", "DIAMOND");
            material = Material.matchMaterial(materialStr);
            if (material == null) {
                material = Material.DIAMOND;
            }
            skullTexture = display.getString("skull-texture", null);
            loreTemplate = display.getStringList("lore");
            glowing = display.getBoolean("glowing", false);
        } else {
            displayName = "&f" + id;
            material = Material.DIAMOND;
            loreTemplate = new ArrayList<>();
        }

        // Active slot
        activeSlot = ActiveSlot.fromString(section.getString("active-slot", "ANY"));

        // Effects
        effects = new ArrayList<>();
        List<String> effectStrings = section.getStringList("effects");
        for (String effectStr : effectStrings) {
            PotionEffect effect = parseEffect(effectStr);
            if (effect != null) {
                effects.add(effect);
            }
        }

        // Restrictions
        restrictions = RelicRestrictions.fromConfig(section.getConfigurationSection("restrictions"));

        // BlueMap settings
        ConfigurationSection bluemap = section.getConfigurationSection("bluemap");
        if (bluemap != null) {
            blueMapEnabled = bluemap.getBoolean("enabled", false);
            markerSetId = bluemap.getString("marker-set-id", "relics");
            markerSetLabel = bluemap.getString("marker-set-label", "Server Relics");
            markerId = bluemap.getString("marker-id", id);
            markerLabel = bluemap.getString("marker-label", displayName);
            markerIcon = bluemap.getString("marker-icon", "");
            markerIconAnchorX = bluemap.getInt("icon-anchor-x", 16);
            markerIconAnchorY = bluemap.getInt("icon-anchor-y", 16);
            markerUpdateInterval = bluemap.getInt("update-interval-seconds", 30);
            showWhenDropped = bluemap.getBoolean("show-when-dropped", true);
            showHolderName = bluemap.getBoolean("show-holder-name", true);
        }

        // Broadcasts
        ConfigurationSection broadcasts = section.getConfigurationSection("broadcasts");
        if (broadcasts != null) {
            broadcastOnPickup = broadcasts.getString("on-pickup", "");
            broadcastOnDeathDrop = broadcasts.getString("on-death-drop", "");
            broadcastOnLogoutDrop = broadcasts.getString("on-logout-drop", "");
            broadcastOnOfflineExpired = broadcasts.getString("on-offline-expired", "");
            broadcastOnLocate = broadcasts.getString("on-locate", "");
            broadcastOnLocateDropped = broadcasts.getString("on-locate-dropped", "");
            broadcastOnLocateOffline = broadcasts.getString("on-locate-offline", "");
        }

        // Stats
        ConfigurationSection stats = section.getConfigurationSection("stats");
        if (stats != null) {
            trackTimeHeld = stats.getBoolean("track-time-held", true);
            trackKills = stats.getBoolean("track-kills-while-holding", false);
            trackDeaths = stats.getBoolean("track-deaths-while-holding", false);
            trackTimesAcquired = stats.getBoolean("track-times-acquired", true);
        }

        // Let subclasses load additional config
        loadAdditionalConfig(section);
    }

    /**
     * Override this to load additional configuration specific to your relic type
     */
    protected void loadAdditionalConfig(ConfigurationSection section) {
        // Default: no additional config
    }

    /**
     * Create the ItemStack for this relic
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        // Set display name
        meta.displayName(TextUtil.colorize(displayName));

        // Set skull texture if applicable
        if (material == Material.PLAYER_HEAD && skullTexture != null && !skullTexture.isEmpty()) {
            applySkullTexture((SkullMeta) meta, skullTexture);
        }

        // Set lore (will be updated dynamically)
        updateLore(meta, null, 0);

        // Add glow effect flag (hide enchants so glow looks clean)
        if (glowing) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide attributes
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Add PDC tags to identify this as a relic
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(RELIC_TYPE_KEY, PersistentDataType.STRING, id);
        pdc.set(RELIC_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());

        item.setItemMeta(meta);

        // Add glow effect via dummy enchantment (after meta is set)
        if (glowing) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LUCK, 1);
        }

        return item;
    }

    /**
     * Update the lore of a relic item with current holder info
     */
    public void updateLore(ItemMeta meta, UUID holderUuid, long reignTimeSeconds) {
        if (meta == null || loreTemplate == null) return;

        List<Component> lore = new ArrayList<>();
        String holderName = holderUuid != null ?
            Bukkit.getOfflinePlayer(holderUuid).getName() : "None";
        String reignTime = formatTime(reignTimeSeconds);

        for (String line : loreTemplate) {
            String processed = line
                .replace("%holder%", holderName != null ? holderName : "Unknown")
                .replace("%reign_time%", reignTime);
            lore.add(TextUtil.colorize(processed));
        }

        meta.lore(lore);
    }

    /**
     * Update the lore of a relic ItemStack
     */
    public void updateItemLore(ItemStack item, UUID holderUuid, long reignTimeSeconds) {
        if (item == null || !isThisRelic(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            updateLore(meta, holderUuid, reignTimeSeconds);
            item.setItemMeta(meta);
        }
    }

    /**
     * Apply skull texture to a skull meta
     */
    protected void applySkullTexture(SkullMeta meta, String base64Texture) {
        try {
            // Decode the texture URL from base64
            String decoded = new String(Base64.getDecoder().decode(base64Texture));
            Pattern pattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(decoded);

            if (matcher.find()) {
                String textureUrl = matcher.group(1);
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(URI.create(textureUrl).toURL());
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply skull texture for relic " + id + ": " + e.getMessage());
        }
    }

    /**
     * Parse a potion effect from config string format "EFFECT_TYPE:AMPLIFIER"
     */
    protected PotionEffect parseEffect(String effectStr) {
        try {
            String[] parts = effectStr.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect type: " + parts[0]);
                return null;
            }
            int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            // Infinite duration, no particles, show icon
            return new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse effect: " + effectStr);
            return null;
        }
    }

    /**
     * Check if the given item is THIS specific relic
     */
    public boolean isThisRelic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(RELIC_TYPE_KEY, PersistentDataType.STRING);
        return id.equals(type);
    }

    /**
     * Check if any item is a relic (any type)
     */
    public static boolean isAnyRelic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(RELIC_TYPE_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the relic type ID from an item
     */
    public static String getRelicId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(RELIC_TYPE_KEY, PersistentDataType.STRING);
    }

    /**
     * Apply effects to a player when they acquire/equip the relic
     */
    public void applyEffects(Player player) {
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        // Force PvP if configured
        if (restrictions.isForcePvp()) {
            plugin.getPvPManagerHook().forcePvPOn(player);
        }

        // Call hook for subclass-specific behavior
        onActivate(player);
    }

    /**
     * Remove effects from a player when they lose/unequip the relic
     */
    public void removeEffects(Player player) {
        for (PotionEffect effect : effects) {
            player.removePotionEffect(effect.getType());
        }

        // Restore PvP setting
        if (restrictions.isForcePvp()) {
            plugin.getPvPManagerHook().restorePvP(player);
        }

        // Call hook for subclass-specific behavior
        onDeactivate(player);
    }

    /**
     * Broadcast a message to all players with the notify permission
     */
    public void broadcast(String message, Player player, Location location) {
        if (message == null || message.isEmpty()) return;

        String formatted = message
            .replace("{player}", player != null ? player.getName() : "Unknown")
            .replace("{holder}", player != null ? player.getName() : "Unknown")
            .replace("{relic}", TextUtil.stripColor(displayName))
            .replace("{location}", formatLocation(location));

        Component component = TextUtil.colorize(formatted);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("serverrelics.notify")) {
                p.sendMessage(component);
            }
        }
    }

    /**
     * Format a location as a readable string
     */
    protected String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Format seconds as a human-readable time string
     */
    protected String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }

    // Event hooks for subclasses to override

    /**
     * Called when the relic is activated (equipped to active slot or picked up)
     */
    protected void onActivate(Player player) {
        // Override in subclass
    }

    /**
     * Called when the relic is deactivated (removed from active slot or dropped)
     */
    protected void onDeactivate(Player player) {
        // Override in subclass
    }

    /**
     * Called when a player picks up the relic
     */
    public void onPickup(Player player) {
        broadcast(broadcastOnPickup, player, player.getLocation());
    }

    /**
     * Called when the relic is dropped on death
     */
    public void onDeathDrop(Player player, Location dropLocation) {
        broadcast(broadcastOnDeathDrop, player, dropLocation);
    }

    /**
     * Called when the relic is dropped on logout
     */
    public void onLogoutDrop(Player player, Location dropLocation) {
        broadcast(broadcastOnLogoutDrop, player, dropLocation);
    }

    /**
     * Called when a player uses /relic locate
     */
    public void onLocate(Player requester, Player holder, Location location, boolean isDropped) {
        if (isDropped) {
            broadcast(broadcastOnLocateDropped, requester, location);
        } else {
            String message = broadcastOnLocate
                .replace("{holder}", holder != null ? holder.getName() : "Unknown");
            broadcast(message, requester, location);
        }
    }

    // Getters

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isUnique() {
        return unique;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Component getDisplayNameComponent() {
        return TextUtil.colorize(displayName);
    }

    public Material getMaterial() {
        return material;
    }

    public ActiveSlot getActiveSlot() {
        return activeSlot;
    }

    public List<PotionEffect> getEffects() {
        return effects;
    }

    public RelicRestrictions getRestrictions() {
        return restrictions;
    }

    public boolean isBlueMapEnabled() {
        return blueMapEnabled;
    }

    public String getMarkerSetId() {
        return markerSetId;
    }

    public String getMarkerSetLabel() {
        return markerSetLabel;
    }

    public String getMarkerId() {
        return markerId;
    }

    public String getMarkerLabel() {
        return markerLabel;
    }

    public String getMarkerIcon() {
        return markerIcon;
    }

    public int getMarkerIconAnchorX() {
        return markerIconAnchorX;
    }

    public int getMarkerIconAnchorY() {
        return markerIconAnchorY;
    }

    public int getMarkerUpdateInterval() {
        return markerUpdateInterval;
    }

    public boolean isShowWhenDropped() {
        return showWhenDropped;
    }

    public boolean isShowHolderName() {
        return showHolderName;
    }

    public boolean isTrackTimeHeld() {
        return trackTimeHeld;
    }

    public boolean isTrackKills() {
        return trackKills;
    }

    public boolean isTrackDeaths() {
        return trackDeaths;
    }

    public boolean isTrackTimesAcquired() {
        return trackTimesAcquired;
    }

    /**
     * Get a broadcast message by key name
     */
    public String getBroadcastMessage(String key) {
        return switch (key) {
            case "on-pickup" -> broadcastOnPickup;
            case "on-death-drop" -> broadcastOnDeathDrop;
            case "on-logout-drop" -> broadcastOnLogoutDrop;
            case "on-offline-expired" -> broadcastOnOfflineExpired;
            case "on-locate" -> broadcastOnLocate;
            case "on-locate-dropped" -> broadcastOnLocateDropped;
            case "on-locate-offline" -> broadcastOnLocateOffline;
            default -> null;
        };
    }
}

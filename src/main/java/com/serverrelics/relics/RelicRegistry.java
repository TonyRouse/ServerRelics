package com.serverrelics.relics;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.impl.CrownRelic;
import com.serverrelics.relics.impl.GenericRelic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Registry for all relic types.
 * Handles registration, lookup, and lifecycle of relics.
 *
 * To add a new built-in relic type:
 * 1. Create a class extending Relic
 * 2. Register it in registerBuiltInTypes()
 *
 * Third-party plugins can also register custom relic types via the API.
 */
public class RelicRegistry {

    private final ServerRelics plugin;

    // Map of relic type name -> factory function
    private final Map<String, BiFunction<ServerRelics, String, Relic>> relicFactories;

    // Map of relic ID -> Relic instance
    private final Map<String, Relic> relics;

    public RelicRegistry(ServerRelics plugin) {
        this.plugin = plugin;
        this.relicFactories = new HashMap<>();
        this.relics = new LinkedHashMap<>(); // Maintain insertion order

        registerBuiltInTypes();
    }

    /**
     * Register all built-in relic types
     */
    private void registerBuiltInTypes() {
        // Crown - the flagship relic
        registerType("crown", CrownRelic::new);

        // Generic types for simple relics that don't need special code
        registerType("generic", GenericRelic::new);
        registerType("orb", GenericRelic::new);
        registerType("scepter", GenericRelic::new);
        registerType("cloak", GenericRelic::new);
        registerType("amulet", GenericRelic::new);
        registerType("ring", GenericRelic::new);
    }

    /**
     * Register a relic type with a factory function.
     * The factory takes (plugin, id) and returns a Relic instance.
     *
     * @param typeName The type name used in config (e.g., "crown", "orb")
     * @param factory Factory function to create the relic
     */
    public void registerType(String typeName, BiFunction<ServerRelics, String, Relic> factory) {
        relicFactories.put(typeName.toLowerCase(), factory);
        plugin.getLogger().fine("Registered relic type: " + typeName);
    }

    /**
     * Load and register all relics from the configuration
     */
    public void registerRelicsFromConfig() {
        ConfigurationSection relicsSection = plugin.getConfig().getConfigurationSection("relics");
        if (relicsSection == null) {
            plugin.getLogger().warning("No relics section found in config!");
            return;
        }

        for (String relicId : relicsSection.getKeys(false)) {
            ConfigurationSection relicConfig = relicsSection.getConfigurationSection(relicId);
            if (relicConfig == null) continue;

            if (!relicConfig.getBoolean("enabled", true)) {
                plugin.getLogger().info("Relic '" + relicId + "' is disabled in config.");
                continue;
            }

            String typeName = relicConfig.getString("type", "generic");
            Relic relic = createRelic(typeName, relicId);

            if (relic != null) {
                relic.loadConfig(relicConfig);
                relics.put(relicId.toLowerCase(), relic);
                plugin.getLogger().info("Loaded relic: " + relicId + " (type: " + typeName + ")");
            } else {
                plugin.getLogger().warning("Failed to create relic: " + relicId + " (unknown type: " + typeName + ")");
            }
        }
    }

    /**
     * Create a relic instance using the registered factory
     */
    private Relic createRelic(String typeName, String relicId) {
        BiFunction<ServerRelics, String, Relic> factory = relicFactories.get(typeName.toLowerCase());
        if (factory == null) {
            // Try loading as a custom class
            try {
                Class<?> clazz = Class.forName(typeName);
                if (Relic.class.isAssignableFrom(clazz)) {
                    return (Relic) clazz.getConstructor(ServerRelics.class, String.class)
                        .newInstance(plugin, relicId);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load custom relic class: " + typeName);
            }
            return null;
        }
        return factory.apply(plugin, relicId);
    }

    /**
     * Get a relic by its ID
     */
    public Relic getRelic(String id) {
        return relics.get(id.toLowerCase());
    }

    /**
     * Get the relic that matches the given item
     */
    public Relic getRelicFromItem(ItemStack item) {
        String id = Relic.getRelicId(item);
        return id != null ? getRelic(id) : null;
    }

    /**
     * Check if an item is any registered relic
     */
    public boolean isRelic(ItemStack item) {
        return getRelicFromItem(item) != null;
    }

    /**
     * Get all registered relics
     */
    public Collection<Relic> getAllRelics() {
        return Collections.unmodifiableCollection(relics.values());
    }

    /**
     * Get all enabled relics
     */
    public Collection<Relic> getEnabledRelics() {
        return relics.values().stream()
            .filter(Relic::isEnabled)
            .toList();
    }

    /**
     * Get count of enabled relics
     */
    public int getEnabledRelicCount() {
        return (int) relics.values().stream().filter(Relic::isEnabled).count();
    }

    /**
     * Get all relic IDs
     */
    public Set<String> getRelicIds() {
        return Collections.unmodifiableSet(relics.keySet());
    }

    /**
     * Unregister all relics (for reload)
     */
    public void unregisterAll() {
        relics.clear();
    }

    /**
     * Check if a relic with the given ID exists and is enabled
     */
    public boolean isRelicEnabled(String id) {
        Relic relic = getRelic(id);
        return relic != null && relic.isEnabled();
    }
}

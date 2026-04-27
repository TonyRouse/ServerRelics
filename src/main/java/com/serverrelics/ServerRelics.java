package com.serverrelics;

import com.serverrelics.commands.RelicCommand;
import com.serverrelics.commands.RelicTabCompleter;
import com.serverrelics.hooks.BlueMapHook;
import com.serverrelics.hooks.PlaceholderHook;
import com.serverrelics.hooks.PvPManagerHook;
import com.serverrelics.listeners.DeathListener;
import com.serverrelics.listeners.InventoryListener;
import com.serverrelics.listeners.ItemListener;
import com.serverrelics.listeners.JoinQuitListener;
import com.serverrelics.listeners.PlayerStateListener;
import com.serverrelics.listeners.PvPCommandListener;
import com.serverrelics.managers.ConfigManager;
import com.serverrelics.managers.RelicManager;
import com.serverrelics.managers.StatsManager;
import com.serverrelics.managers.StuckVoteManager;
import com.serverrelics.relics.RelicRegistry;
import com.serverrelics.tasks.MarkerUpdateTask;
import com.serverrelics.tasks.OfflineExpirationTask;
import com.serverrelics.tasks.RelicIntegrityTask;
import com.serverrelics.tasks.TimeTrackingTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ServerRelics - A framework for unique, trackable server-wide relics
 *
 * This plugin provides:
 * - Unique items that can only exist once in the world
 * - Configurable restrictions (storage, PvP, death behavior)
 * - BlueMap integration for live tracking
 * - PlaceholderAPI integration for stats display
 * - Full statistics tracking with leaderboards
 */
public class ServerRelics extends JavaPlugin {

    private static ServerRelics instance;

    private ConfigManager configManager;
    private RelicRegistry relicRegistry;
    private RelicManager relicManager;
    private StatsManager statsManager;
    private StuckVoteManager stuckVoteManager;

    private PvPManagerHook pvpManagerHook;
    private BlueMapHook blueMapHook;
    private PlaceholderHook placeholderHook;

    private TimeTrackingTask timeTrackingTask;
    private MarkerUpdateTask markerUpdateTask;
    private OfflineExpirationTask offlineExpirationTask;
    private RelicIntegrityTask relicIntegrityTask;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        if (!configManager.load()) {
            getLogger().severe("Failed to load configuration! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize registry and managers
        relicRegistry = new RelicRegistry(this);
        statsManager = new StatsManager(this);
        relicManager = new RelicManager(this);
        stuckVoteManager = new StuckVoteManager(this);

        // Connect to database
        if (!statsManager.initialize()) {
            getLogger().severe("Failed to connect to database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load relics from config
        relicRegistry.registerRelicsFromConfig();

        // Load relic state (who has what, dropped locations)
        relicManager.loadState();

        // Initialize hooks
        initializeHooks();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start tasks
        startTasks();

        getLogger().info("ServerRelics enabled! " + relicRegistry.getEnabledRelicCount() + " relic(s) loaded.");
    }

    @Override
    public void onDisable() {
        // Stop tasks
        if (timeTrackingTask != null) {
            timeTrackingTask.cancel();
        }
        if (markerUpdateTask != null) {
            markerUpdateTask.cancel();
        }

        // Save state
        if (relicManager != null) {
            relicManager.saveState();
        }
        if (statsManager != null) {
            statsManager.saveAll();
            statsManager.shutdown();
        }

        // Clean up hooks
        if (blueMapHook != null) {
            blueMapHook.cleanup();
        }

        instance = null;
        getLogger().info("ServerRelics disabled.");
    }

    /**
     * Reload the plugin configuration and relics
     */
    public void reload() {
        // Stop tasks
        if (timeTrackingTask != null) {
            timeTrackingTask.cancel();
        }
        if (markerUpdateTask != null) {
            markerUpdateTask.cancel();
        }

        // Save current state
        relicManager.saveState();
        statsManager.saveAll();

        // Reload config
        reloadConfig();
        configManager.load();

        // Re-register relics
        relicRegistry.unregisterAll();
        relicRegistry.registerRelicsFromConfig();

        // Reload relic state
        relicManager.loadState();

        // Clear stuck votes (config may have changed)
        stuckVoteManager.clearAll();

        // Restart tasks
        startTasks();

        getLogger().info("ServerRelics reloaded!");
    }

    private void initializeHooks() {
        // PvPManager hook
        pvpManagerHook = new PvPManagerHook(this);
        pvpManagerHook.initialize();

        // BlueMap hook
        blueMapHook = new BlueMapHook(this);
        blueMapHook.initialize();

        // PlaceholderAPI hook
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderHook = new PlaceholderHook(this);
            placeholderHook.register();
            getLogger().info("PlaceholderAPI integration enabled.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(this), this);
        getServer().getPluginManager().registerEvents(new PvPCommandListener(this), this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("relic");
        if (command != null) {
            RelicCommand relicCommand = new RelicCommand(this);
            command.setExecutor(relicCommand);
            command.setTabCompleter(new RelicTabCompleter(this));
        }
    }

    private void startTasks() {
        // Time tracking task - runs every second
        timeTrackingTask = new TimeTrackingTask(this);
        timeTrackingTask.runTaskTimer(this, 20L, 20L);

        // Marker update task - runs based on config
        int updateInterval = configManager.getMarkerUpdateInterval();
        if (updateInterval > 0 && blueMapHook.isEnabled()) {
            markerUpdateTask = new MarkerUpdateTask(this);
            markerUpdateTask.runTaskTimer(this, 20L * updateInterval, 20L * updateInterval);
        }

        // Offline expiration task - runs every hour (20 ticks * 60 seconds * 60 minutes)
        offlineExpirationTask = new OfflineExpirationTask(this);
        offlineExpirationTask.runTaskTimer(this, 20L * 60, 20L * 60 * 60);

        // Integrity check task - runs every minute for state saves and integrity checks
        relicIntegrityTask = new RelicIntegrityTask(this);
        relicIntegrityTask.runTaskTimer(this, 20L * 60, 20L * 60);
    }

    /**
     * Log a message at the specified level
     */
    public void log(Level level, String message) {
        getLogger().log(level, message);
    }

    /**
     * Log a debug message (only if debug is enabled)
     */
    public void debug(String message) {
        if (configManager.isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    /**
     * Check if debug mode is enabled
     */
    public boolean isDebug() {
        return configManager != null && configManager.isDebugEnabled();
    }

    // Getters

    public static ServerRelics getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RelicRegistry getRelicRegistry() {
        return relicRegistry;
    }

    public RelicManager getRelicManager() {
        return relicManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public StuckVoteManager getStuckVoteManager() {
        return stuckVoteManager;
    }

    public PvPManagerHook getPvPManagerHook() {
        return pvpManagerHook;
    }

    public BlueMapHook getBlueMapHook() {
        return blueMapHook;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }
}

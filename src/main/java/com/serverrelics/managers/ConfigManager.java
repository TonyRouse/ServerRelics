package com.serverrelics.managers;

import com.serverrelics.ServerRelics;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages plugin configuration loading and validation.
 */
public class ConfigManager {

    private final ServerRelics plugin;

    // Database settings
    private String databaseType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String sqliteFile;

    // Stats settings
    private int statsSaveInterval;
    private int leaderboardSize;

    // Messages
    private String prefix;

    // Debug
    private boolean debugEnabled;

    public ConfigManager(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * Load configuration from file
     * @return true if successful
     */
    public boolean load() {
        try {
            FileConfiguration config = plugin.getConfig();

            // Database settings
            databaseType = config.getString("database.type", "mysql");
            mysqlHost = config.getString("database.mysql.host", "localhost");
            mysqlPort = config.getInt("database.mysql.port", 3306);
            mysqlDatabase = config.getString("database.mysql.database", "serverrelics");
            mysqlUsername = config.getString("database.mysql.username", "minecraft");
            mysqlPassword = config.getString("database.mysql.password", "");
            sqliteFile = config.getString("database.sqlite.file", "database.db");

            // Stats settings
            statsSaveInterval = config.getInt("stats.save-interval", 300);
            leaderboardSize = config.getInt("stats.leaderboard-size", 10);

            // Messages
            prefix = config.getString("messages.prefix", "&6&l[Relics] &r");

            // Debug (hidden config option)
            debugEnabled = config.getBoolean("debug", false);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get a message from config with prefix
     */
    public String getMessage(String key) {
        String message = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        return prefix + message;
    }

    /**
     * Get a raw message without prefix
     */
    public String getRawMessage(String key) {
        return plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    /**
     * Get the marker update interval (uses first relic's setting or default)
     */
    public int getMarkerUpdateInterval() {
        // Find first enabled relic with bluemap and use its interval
        var relicsSection = plugin.getConfig().getConfigurationSection("relics");
        if (relicsSection != null) {
            for (String key : relicsSection.getKeys(false)) {
                var relicSection = relicsSection.getConfigurationSection(key);
                if (relicSection != null && relicSection.getBoolean("enabled", true)) {
                    var bluemap = relicSection.getConfigurationSection("bluemap");
                    if (bluemap != null && bluemap.getBoolean("enabled", false)) {
                        return bluemap.getInt("update-interval-seconds", 30);
                    }
                }
            }
        }
        return 30; // Default
    }

    // Getters

    public String getDatabaseType() {
        return databaseType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public int getStatsSaveInterval() {
        return statsSaveInterval;
    }

    public int getLeaderboardSize() {
        return leaderboardSize;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isMySql() {
        return "mysql".equalsIgnoreCase(databaseType);
    }
}

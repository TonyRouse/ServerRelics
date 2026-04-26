package com.serverrelics.managers;

import com.serverrelics.ServerRelics;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player statistics for relics.
 * Tracks time held, kills, deaths, acquisitions.
 * Persists to MySQL or SQLite database.
 */
public class StatsManager {

    private final ServerRelics plugin;
    private HikariDataSource dataSource;

    // In-memory cache of stats
    private final Map<UUID, PlayerStats> statsCache;

    // Track when players started holding relics (for time calculation)
    private final Map<UUID, Map<String, Long>> holdStartTimes;

    // Dirty flags for saving
    private final Set<UUID> dirtyStats;

    public StatsManager(ServerRelics plugin) {
        this.plugin = plugin;
        this.statsCache = new ConcurrentHashMap<>();
        this.holdStartTimes = new ConcurrentHashMap<>();
        this.dirtyStats = ConcurrentHashMap.newKeySet();
    }

    /**
     * Initialize database connection and create tables
     */
    public boolean initialize() {
        try {
            ConfigManager config = plugin.getConfigManager();

            HikariConfig hikariConfig = new HikariConfig();

            if (config.isMySql()) {
                hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" +
                    config.getMysqlPort() + "/" + config.getMysqlDatabase() +
                    "?useSSL=false&allowPublicKeyRetrieval=true");
                hikariConfig.setUsername(config.getMysqlUsername());
                hikariConfig.setPassword(config.getMysqlPassword());
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else {
                File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
            }

            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setPoolName("ServerRelics-Pool");

            dataSource = new HikariDataSource(hikariConfig);

            createTables();

            // Start periodic save task
            int saveInterval = config.getStatsSaveInterval();
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveAll();
                }
            }.runTaskTimerAsynchronously(plugin, 20L * saveInterval, 20L * saveInterval);

            plugin.getLogger().info("Database connection established (" +
                (config.isMySql() ? "MySQL" : "SQLite") + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create database tables
     */
    private void createTables() throws SQLException {
        boolean isSqlite = !plugin.getConfigManager().isMySql();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Main stats table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS relic_stats (
                    uuid VARCHAR(36) NOT NULL,
                    relic_id VARCHAR(64) NOT NULL,
                    total_time_held BIGINT DEFAULT 0,
                    kills INT DEFAULT 0,
                    deaths INT DEFAULT 0,
                    times_acquired INT DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, relic_id)
                )
            """);

            // History/audit table - different syntax for MySQL vs SQLite
            if (isSqlite) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS relic_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        relic_id VARCHAR(64) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        holder_name VARCHAR(16),
                        start_time BIGINT NOT NULL,
                        end_time BIGINT,
                        end_reason VARCHAR(32)
                    )
                """);
                // Create indexes separately for SQLite
                try {
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_relic_id ON relic_history (relic_id)");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_holder ON relic_history (holder_uuid)");
                } catch (SQLException e) {
                    // Indexes may already exist, ignore
                }
            } else {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS relic_history (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        relic_id VARCHAR(64) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        holder_name VARCHAR(16),
                        start_time BIGINT NOT NULL,
                        end_time BIGINT,
                        end_reason VARCHAR(32),
                        INDEX idx_relic_id (relic_id),
                        INDEX idx_holder (holder_uuid)
                    )
                """);
            }

            // Player name cache
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_names (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    /**
     * Shutdown database connection
     */
    public void shutdown() {
        saveAll();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ==================== Time Tracking ====================

    /**
     * Start tracking hold time for a player
     */
    public void startHoldTime(UUID playerUuid, String relicId) {
        holdStartTimes.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
            .put(relicId.toLowerCase(), System.currentTimeMillis());
    }

    /**
     * Finalize hold time when player loses the relic
     */
    public void finalizeHoldTime(UUID playerUuid, String relicId) {
        relicId = relicId.toLowerCase();
        Map<String, Long> playerHolds = holdStartTimes.get(playerUuid);
        if (playerHolds == null) return;

        Long startTime = playerHolds.remove(relicId);
        if (startTime == null) return;

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        if (duration > 0) {
            addTimeHeld(playerUuid, relicId, duration);
        }
    }

    /**
     * Increment time for all current holders (called every second)
     */
    public void incrementHoldTimes() {
        for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
            UUID holder = plugin.getRelicManager().getHolder(relicId);
            if (holder != null && Bukkit.getPlayer(holder) != null) {
                // Increment by 1 second
                PlayerStats stats = getOrCreateStats(holder);
                stats.addTimeHeld(relicId, 1);
                dirtyStats.add(holder);
            }
        }
    }

    // ==================== Stats Modification ====================

    /**
     * Add time held for a player
     */
    public void addTimeHeld(UUID playerUuid, String relicId, long seconds) {
        PlayerStats stats = getOrCreateStats(playerUuid);
        stats.addTimeHeld(relicId.toLowerCase(), seconds);
        dirtyStats.add(playerUuid);
    }

    /**
     * Increment kill count
     */
    public void incrementKills(UUID playerUuid, String relicId) {
        PlayerStats stats = getOrCreateStats(playerUuid);
        stats.incrementKills(relicId.toLowerCase());
        dirtyStats.add(playerUuid);
    }

    /**
     * Increment death count
     */
    public void incrementDeaths(UUID playerUuid, String relicId) {
        PlayerStats stats = getOrCreateStats(playerUuid);
        stats.incrementDeaths(relicId.toLowerCase());
        dirtyStats.add(playerUuid);
    }

    /**
     * Increment acquisition count
     */
    public void incrementAcquisitions(UUID playerUuid, String relicId) {
        PlayerStats stats = getOrCreateStats(playerUuid);
        stats.incrementAcquisitions(relicId.toLowerCase());
        dirtyStats.add(playerUuid);
    }

    // ==================== Stats Retrieval ====================

    /**
     * Get total time a player has held a relic
     */
    public long getTotalTimeHeld(UUID playerUuid, String relicId) {
        PlayerStats stats = statsCache.get(playerUuid);
        if (stats == null) {
            stats = loadStats(playerUuid);
        }
        return stats != null ? stats.getTimeHeld(relicId.toLowerCase()) : 0;
    }

    /**
     * Get kills while holding a relic
     */
    public int getKills(UUID playerUuid, String relicId) {
        PlayerStats stats = statsCache.get(playerUuid);
        if (stats == null) {
            stats = loadStats(playerUuid);
        }
        return stats != null ? stats.getKills(relicId.toLowerCase()) : 0;
    }

    /**
     * Get deaths while holding a relic
     */
    public int getDeaths(UUID playerUuid, String relicId) {
        PlayerStats stats = statsCache.get(playerUuid);
        if (stats == null) {
            stats = loadStats(playerUuid);
        }
        return stats != null ? stats.getDeaths(relicId.toLowerCase()) : 0;
    }

    /**
     * Get times acquired a relic
     */
    public int getTimesAcquired(UUID playerUuid, String relicId) {
        PlayerStats stats = statsCache.get(playerUuid);
        if (stats == null) {
            stats = loadStats(playerUuid);
        }
        return stats != null ? stats.getTimesAcquired(relicId.toLowerCase()) : 0;
    }

    /**
     * Get leaderboard for time held
     */
    public List<LeaderboardEntry> getTimeLeaderboard(String relicId, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        relicId = relicId.toLowerCase();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                SELECT s.uuid, COALESCE(p.name, 'Unknown') as name, s.total_time_held
                FROM relic_stats s
                LEFT JOIN player_names p ON s.uuid = p.uuid
                WHERE s.relic_id = ? AND s.total_time_held > 0
                ORDER BY s.total_time_held DESC
                LIMIT ?
             """)) {

            stmt.setString(1, relicId);
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                    rank++,
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"),
                    rs.getLong("total_time_held")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get leaderboard: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Get a player's rank on the leaderboard
     */
    public int getPlayerRank(UUID playerUuid, String relicId) {
        relicId = relicId.toLowerCase();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                SELECT COUNT(*) + 1 as rank
                FROM relic_stats
                WHERE relic_id = ? AND total_time_held > (
                    SELECT COALESCE(total_time_held, 0)
                    FROM relic_stats
                    WHERE uuid = ? AND relic_id = ?
                )
             """)) {

            stmt.setString(1, relicId);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, relicId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("rank");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get player rank: " + e.getMessage());
        }

        return -1;
    }

    // ==================== Persistence ====================

    /**
     * Get or create stats for a player (loads from DB if not cached)
     */
    private PlayerStats getOrCreateStats(UUID playerUuid) {
        try {
            return statsCache.computeIfAbsent(playerUuid, this::loadStats);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get/create stats for " + playerUuid + ": " + e.getMessage());
            // Return empty stats to avoid breaking other functionality
            return new PlayerStats(playerUuid);
        }
    }

    /**
     * Load stats from database
     */
    private PlayerStats loadStats(UUID playerUuid) {
        PlayerStats stats = new PlayerStats(playerUuid);

        // Check if dataSource is available
        if (dataSource == null || dataSource.isClosed()) {
            plugin.debug("Database not available, returning empty stats for " + playerUuid);
            return stats;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT relic_id, total_time_held, kills, deaths, times_acquired FROM relic_stats WHERE uuid = ?")) {

            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String relicId = rs.getString("relic_id");
                stats.setTimeHeld(relicId, rs.getLong("total_time_held"));
                stats.setKills(relicId, rs.getInt("kills"));
                stats.setDeaths(relicId, rs.getInt("deaths"));
                stats.setTimesAcquired(relicId, rs.getInt("times_acquired"));
            }
            // Note: Don't put in statsCache here - computeIfAbsent handles that
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load stats for " + playerUuid + ": " + e.getMessage());
        }

        return stats;
    }

    /**
     * Save all dirty stats to database
     */
    public void saveAll() {
        if (dirtyStats.isEmpty()) return;

        Set<UUID> toSave = new HashSet<>(dirtyStats);
        dirtyStats.clear();

        for (UUID uuid : toSave) {
            PlayerStats stats = statsCache.get(uuid);
            if (stats != null) {
                saveStats(stats);
            }
        }

        plugin.debug("Saved stats for " + toSave.size() + " players.");
    }

    /**
     * Save a player's stats to database
     */
    private void saveStats(PlayerStats stats) {
        try (Connection conn = dataSource.getConnection()) {
            // Update player name
            String playerName = Bukkit.getOfflinePlayer(stats.getPlayerUuid()).getName();
            if (playerName != null) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "REPLACE INTO player_names (uuid, name) VALUES (?, ?)")) {
                    stmt.setString(1, stats.getPlayerUuid().toString());
                    stmt.setString(2, playerName);
                    stmt.executeUpdate();
                }
            }

            // Save relic stats
            try (PreparedStatement stmt = conn.prepareStatement("""
                REPLACE INTO relic_stats (uuid, relic_id, total_time_held, kills, deaths, times_acquired)
                VALUES (?, ?, ?, ?, ?, ?)
            """)) {
                for (String relicId : stats.getRelicIds()) {
                    stmt.setString(1, stats.getPlayerUuid().toString());
                    stmt.setString(2, relicId);
                    stmt.setLong(3, stats.getTimeHeld(relicId));
                    stmt.setInt(4, stats.getKills(relicId));
                    stmt.setInt(5, stats.getDeaths(relicId));
                    stmt.setInt(6, stats.getTimesAcquired(relicId));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save stats for " + stats.getPlayerUuid() + ": " + e.getMessage());
        }
    }

    /**
     * Update player name in cache/db
     */
    public void updatePlayerName(UUID uuid, String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "REPLACE INTO player_names (uuid, name) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update player name: " + e.getMessage());
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Holds stats for a single player
     */
    public static class PlayerStats {
        private final UUID playerUuid;
        private final Map<String, Long> timeHeld = new ConcurrentHashMap<>();
        private final Map<String, Integer> kills = new ConcurrentHashMap<>();
        private final Map<String, Integer> deaths = new ConcurrentHashMap<>();
        private final Map<String, Integer> timesAcquired = new ConcurrentHashMap<>();

        public PlayerStats(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public Set<String> getRelicIds() {
            Set<String> ids = new HashSet<>();
            ids.addAll(timeHeld.keySet());
            ids.addAll(kills.keySet());
            ids.addAll(deaths.keySet());
            ids.addAll(timesAcquired.keySet());
            return ids;
        }

        public long getTimeHeld(String relicId) {
            return timeHeld.getOrDefault(relicId, 0L);
        }

        public void setTimeHeld(String relicId, long time) {
            timeHeld.put(relicId, time);
        }

        public void addTimeHeld(String relicId, long seconds) {
            timeHeld.merge(relicId, seconds, Long::sum);
        }

        public int getKills(String relicId) {
            return kills.getOrDefault(relicId, 0);
        }

        public void setKills(String relicId, int count) {
            kills.put(relicId, count);
        }

        public void incrementKills(String relicId) {
            kills.merge(relicId, 1, Integer::sum);
        }

        public int getDeaths(String relicId) {
            return deaths.getOrDefault(relicId, 0);
        }

        public void setDeaths(String relicId, int count) {
            deaths.put(relicId, count);
        }

        public void incrementDeaths(String relicId) {
            deaths.merge(relicId, 1, Integer::sum);
        }

        public int getTimesAcquired(String relicId) {
            return timesAcquired.getOrDefault(relicId, 0);
        }

        public void setTimesAcquired(String relicId, int count) {
            timesAcquired.put(relicId, count);
        }

        public void incrementAcquisitions(String relicId) {
            timesAcquired.merge(relicId, 1, Integer::sum);
        }
    }

    /**
     * Represents a leaderboard entry
     */
    public static class LeaderboardEntry {
        private final int rank;
        private final UUID playerUuid;
        private final String playerName;
        private final long value;

        public LeaderboardEntry(int rank, UUID playerUuid, String playerName, long value) {
            this.rank = rank;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.value = value;
        }

        public int getRank() {
            return rank;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getValue() {
            return value;
        }
    }
}

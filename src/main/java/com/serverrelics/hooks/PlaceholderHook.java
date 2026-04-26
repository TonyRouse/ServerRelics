package com.serverrelics.hooks;

import com.serverrelics.ServerRelics;
import com.serverrelics.managers.StatsManager;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlaceholderAPI expansion for ServerRelics.
 *
 * Placeholders:
 * - %relics_<relic>_holder%           - Current holder name
 * - %relics_<relic>_holder_time%      - Current reign duration
 * - %relics_<relic>_total_time%       - Player's total time holding
 * - %relics_<relic>_total_time_<name>% - Specific player's total time
 * - %relics_<relic>_top_<1-10>_name%  - Leaderboard position name
 * - %relics_<relic>_top_<1-10>_time%  - Leaderboard position time
 * - %relics_<relic>_rank%             - Player's leaderboard rank
 * - %relics_<relic>_kills%            - Player's kills while holding
 * - %relics_<relic>_deaths%           - Player's deaths while holding
 * - %relics_<relic>_acquired%         - Times player acquired the relic
 * - %relics_<relic>_exists%           - true/false if relic exists in world
 * - %relics_<relic>_location%         - Current relic location
 *
 * Example: %relics_crown_holder% returns the current crown holder's name
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final ServerRelics plugin;

    // Pattern for top placeholders: top_<rank>_<type>
    private static final Pattern TOP_PATTERN = Pattern.compile("top_(\\d+)_(name|time)");

    // Pattern for player-specific total time: total_time_<playername>
    private static final Pattern PLAYER_TIME_PATTERN = Pattern.compile("total_time_(.+)");

    public PlaceholderHook(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "relics";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Parse the placeholder: <relic>_<type>
        String[] parts = params.split("_", 2);
        if (parts.length < 2) {
            return null;
        }

        String relicId = parts[0].toLowerCase();
        String type = parts[1].toLowerCase();

        // Check if relic exists
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) {
            return null;
        }

        // Route to appropriate handler
        return switch (type) {
            case "holder" -> getHolder(relicId);
            case "holder_time" -> getHolderTime(relicId);
            case "total_time" -> getTotalTime(player, relicId);
            case "rank" -> getRank(player, relicId);
            case "kills" -> getKills(player, relicId);
            case "deaths" -> getDeaths(player, relicId);
            case "acquired" -> getAcquired(player, relicId);
            case "exists" -> getExists(relicId);
            case "location" -> getLocation(relicId);
            default -> handleComplexPlaceholder(player, relicId, type);
        };
    }

    /**
     * Handle complex placeholders like top_X_name and total_time_<player>
     */
    private String handleComplexPlaceholder(OfflinePlayer player, String relicId, String type) {
        // Check for top_X_name or top_X_time
        Matcher topMatcher = TOP_PATTERN.matcher(type);
        if (topMatcher.matches()) {
            int rank = Integer.parseInt(topMatcher.group(1));
            String valueType = topMatcher.group(2);
            return getLeaderboardEntry(relicId, rank, valueType);
        }

        // Check for total_time_<playername>
        Matcher playerMatcher = PLAYER_TIME_PATTERN.matcher(type);
        if (playerMatcher.matches()) {
            String playerName = playerMatcher.group(1);
            return getTotalTimeForPlayer(playerName, relicId);
        }

        return null;
    }

    /**
     * Get current holder name
     */
    private String getHolder(String relicId) {
        UUID holderUuid = plugin.getRelicManager().getHolder(relicId);
        if (holderUuid == null) {
            return "None";
        }
        OfflinePlayer holder = Bukkit.getOfflinePlayer(holderUuid);
        return holder.getName() != null ? holder.getName() : "Unknown";
    }

    /**
     * Get current reign time formatted
     */
    private String getHolderTime(String relicId) {
        long seconds = plugin.getRelicManager().getCurrentReignTime(relicId);
        return TextUtil.formatTime(seconds);
    }

    /**
     * Get player's total time holding this relic
     */
    private String getTotalTime(OfflinePlayer player, String relicId) {
        if (player == null) return "0s";
        long seconds = plugin.getStatsManager().getTotalTimeHeld(player.getUniqueId(), relicId);
        return TextUtil.formatTime(seconds);
    }

    /**
     * Get total time for a specific player by name
     */
    private String getTotalTimeForPlayer(String playerName, String relicId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (player.getUniqueId() == null) return "0s";
        long seconds = plugin.getStatsManager().getTotalTimeHeld(player.getUniqueId(), relicId);
        return TextUtil.formatTime(seconds);
    }

    /**
     * Get player's leaderboard rank
     */
    private String getRank(OfflinePlayer player, String relicId) {
        if (player == null) return "-";
        int rank = plugin.getStatsManager().getPlayerRank(player.getUniqueId(), relicId);
        return rank > 0 ? String.valueOf(rank) : "-";
    }

    /**
     * Get player's kills while holding this relic
     */
    private String getKills(OfflinePlayer player, String relicId) {
        if (player == null) return "0";
        return String.valueOf(plugin.getStatsManager().getKills(player.getUniqueId(), relicId));
    }

    /**
     * Get player's deaths while holding this relic
     */
    private String getDeaths(OfflinePlayer player, String relicId) {
        if (player == null) return "0";
        return String.valueOf(plugin.getStatsManager().getDeaths(player.getUniqueId(), relicId));
    }

    /**
     * Get times player acquired this relic
     */
    private String getAcquired(OfflinePlayer player, String relicId) {
        if (player == null) return "0";
        return String.valueOf(plugin.getStatsManager().getTimesAcquired(player.getUniqueId(), relicId));
    }

    /**
     * Check if relic exists in the world
     */
    private String getExists(String relicId) {
        return String.valueOf(plugin.getRelicManager().relicExistsInWorld(relicId));
    }

    /**
     * Get relic's current location
     */
    private String getLocation(String relicId) {
        var loc = plugin.getRelicManager().getRelicLocation(relicId);
        if (loc == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Get leaderboard entry
     */
    private String getLeaderboardEntry(String relicId, int rank, String type) {
        if (rank < 1 || rank > 10) return "-";

        int leaderboardSize = plugin.getConfigManager().getLeaderboardSize();
        List<StatsManager.LeaderboardEntry> leaderboard =
            plugin.getStatsManager().getTimeLeaderboard(relicId, leaderboardSize);

        if (rank > leaderboard.size()) {
            return type.equals("name") ? "-" : "0s";
        }

        StatsManager.LeaderboardEntry entry = leaderboard.get(rank - 1);
        if (type.equals("name")) {
            return entry.getPlayerName();
        } else {
            return TextUtil.formatTime(entry.getValue());
        }
    }
}

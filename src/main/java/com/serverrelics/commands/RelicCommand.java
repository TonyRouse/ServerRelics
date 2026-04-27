package com.serverrelics.commands;

import com.serverrelics.ServerRelics;
import com.serverrelics.managers.StatsManager;
import com.serverrelics.managers.StuckVoteManager;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Main command handler for /relic
 *
 * Subcommands:
 * - help                    - Show help
 * - give <player> <relic>   - Give relic to player (admin)
 * - take <player>           - Take relic from player (admin)
 * - locate [relic]          - Broadcast current relic location (all players)
 * - spawn <relic>           - Spawn relic at your location (admin)
 * - despawn <relic>         - Remove relic from existence (admin)
 * - stats [player]          - Show player stats
 * - leaderboard [relic]     - Show top holders
 * - reload                  - Reload config (admin)
 */
public class RelicCommand implements CommandExecutor {

    private final ServerRelics plugin;

    public RelicCommand(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "help" -> {
                showHelp(sender);
                yield true;
            }
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "drop" -> handleDrop(sender, args);
            case "locate" -> handleLocate(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "despawn" -> handleDespawn(sender, args);
            case "stats" -> handleStats(sender, args);
            case "leaderboard", "lb", "top" -> handleLeaderboard(sender, args);
            case "stuck" -> handleStuck(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sendMessage(sender, "&cUnknown subcommand. Use /relic help");
                yield true;
            }
        };
    }

    /**
     * Show help message
     */
    private void showHelp(CommandSender sender) {
        sendMessage(sender, plugin.getConfigManager().getRawMessage("help-header"));
        sendMessage(sender, "&e/relic locate [relic] &7- Broadcast relic location");
        sendMessage(sender, "&e/relic stuck [relic] &7- Vote that a dropped relic is stuck");
        sendMessage(sender, "&e/relic stats [player] &7- View player stats");
        sendMessage(sender, "&e/relic leaderboard [relic] &7- View top holders");

        if (sender.hasPermission("serverrelics.admin")) {
            sendMessage(sender, plugin.getConfigManager().getRawMessage("help-admin-header"));
            sendMessage(sender, "&e/relic give <player> <relic> &7- Give relic to player");
            sendMessage(sender, "&e/relic take <player> &7- Remove relic from player");
            sendMessage(sender, "&e/relic drop <relic> &7- Force drop from offline holder");
            sendMessage(sender, "&e/relic spawn <relic> &7- Spawn relic at your location");
            sendMessage(sender, "&e/relic despawn <relic> &7- Remove relic from world");
            sendMessage(sender, "&e/relic reload &7- Reload configuration");
        }
    }

    /**
     * Handle /relic give <player> <relic>
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.give")) return true;

        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /relic give <player> <relic>");
            return true;
        }

        String playerName = args[1];
        String relicId = args[2].toLowerCase();

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sendMessage(sender, plugin.getConfigManager().getMessage("player-not-found")
                .replace("{player}", playerName));
            return true;
        }

        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Check if unique relic already exists
        if (relic.isUnique() && plugin.getRelicManager().relicExistsInWorld(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-already-exists"));
            return true;
        }

        // Give the relic
        if (plugin.getRelicManager().giveRelic(relicId, target)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-given")
                .replace("{relic}", TextUtil.stripColor(relic.getDisplayName()))
                .replace("{player}", target.getName()));
        } else {
            sendMessage(sender, "&cFailed to give relic.");
        }

        return true;
    }

    /**
     * Handle /relic take <player>
     */
    private boolean handleTake(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.take")) return true;

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /relic take <player>");
            return true;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sendMessage(sender, plugin.getConfigManager().getMessage("player-not-found")
                .replace("{player}", playerName));
            return true;
        }

        // Find what relics they have
        List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(target);
        if (heldRelics.isEmpty()) {
            sendMessage(sender, "&c" + target.getName() + " doesn't hold any relics.");
            return true;
        }

        // Take all relics
        for (String relicId : heldRelics) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (plugin.getRelicManager().takeRelic(relicId, target)) {
                sendMessage(sender, plugin.getConfigManager().getMessage("relic-taken")
                    .replace("{relic}", relic != null ? TextUtil.stripColor(relic.getDisplayName()) : relicId)
                    .replace("{player}", target.getName()));
            }
        }

        return true;
    }

    /**
     * Handle /relic drop <relic>
     * Force drop a relic from an offline holder
     */
    private boolean handleDrop(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.drop")) return true;

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /relic drop <relic>");
            return true;
        }

        String relicId = args[1].toLowerCase();
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Check if relic exists and has a holder
        if (!plugin.getRelicManager().relicExistsInWorld(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-in-world"));
            return true;
        }

        UUID holderUuid = plugin.getRelicManager().getHolder(relicId);
        if (holderUuid == null) {
            sendMessage(sender, "&cThis relic is not held by anyone (it may be on the ground).");
            return true;
        }

        Player holder = Bukkit.getPlayer(holderUuid);
        if (holder != null && holder.isOnline()) {
            sendMessage(sender, "&cThe holder is online! Use /relic take " + holder.getName() + " instead.");
            return true;
        }

        // Get holder name for message
        String holderName = Bukkit.getOfflinePlayer(holderUuid).getName();
        if (holderName == null) holderName = "Unknown";

        // Force drop from offline holder
        int radius = relic.getRestrictions().getOfflineDropRadius();
        org.bukkit.entity.Item droppedItem = plugin.getRelicManager().dropRelicFromOfflineHolder(relicId, radius);

        if (droppedItem != null) {
            Location dropLoc = droppedItem.getLocation();
            sendMessage(sender, "&aForced drop of " + TextUtil.stripColor(relic.getDisplayName()) +
                " from offline player " + holderName + " at " + formatLocation(dropLoc));

            // Update BlueMap
            if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
                plugin.getBlueMapHook().updateMarkerDropped(relic, dropLoc);
            }
        } else {
            sendMessage(sender, "&cFailed to drop relic. Check console for errors.");
        }

        return true;
    }

    /**
     * Handle /relic locate [relic]
     * Broadcasts to ALL players - this is a server-wide event!
     */
    private boolean handleLocate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.locate")) return true;

        // Default to crown if no relic specified
        String relicId = args.length > 1 ? args[1].toLowerCase() : "crown";

        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Check if relic exists in world
        if (!plugin.getRelicManager().relicExistsInWorld(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-in-world"));
            return true;
        }

        boolean isDropped = plugin.getRelicManager().isDropped(relicId);
        boolean isHolderOffline = plugin.getRelicManager().isHolderOffline(relicId);
        Player holder = plugin.getRelicManager().getHolderPlayer(relicId);

        // Get appropriate location
        Location location;
        if (isDropped) {
            location = plugin.getRelicManager().getDroppedLocation(relicId);
        } else if (isHolderOffline) {
            location = plugin.getRelicManager().getHolderLastLocation(relicId);
        } else {
            location = plugin.getRelicManager().getRelicLocation(relicId);
        }

        // Broadcast to everyone
        String message;
        if (isDropped) {
            message = relic.getBroadcastMessage("on-locate-dropped");
        } else if (isHolderOffline) {
            message = relic.getBroadcastMessage("on-locate-offline");
            String holderName = plugin.getRelicManager().getOfflineHolderName(relicId);
            if (message != null) {
                message = message.replace("{holder}", holderName != null ? holderName : "Unknown");
            }
        } else {
            message = relic.getBroadcastMessage("on-locate");
            if (message != null && holder != null) {
                message = message.replace("{holder}", holder.getName());
            }
        }

        if (message != null && !message.isEmpty()) {
            message = message.replace("{location}", formatLocation(location));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("serverrelics.notify")) {
                    p.sendMessage(TextUtil.colorize(message));
                }
            }
        }

        return true;
    }

    /**
     * Handle /relic spawn <relic>
     */
    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.spawn")) return true;

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /relic spawn <relic>");
            return true;
        }

        String relicId = args[1].toLowerCase();
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);

        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Check if unique relic already exists
        if (relic.isUnique() && plugin.getRelicManager().relicExistsInWorld(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-already-exists"));
            return true;
        }

        // Spawn the relic
        Item droppedItem = plugin.getRelicManager().spawnRelic(relicId, player.getLocation());
        if (droppedItem != null) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-spawned")
                .replace("{relic}", TextUtil.stripColor(relic.getDisplayName())));
        } else {
            sendMessage(sender, "&cFailed to spawn relic.");
        }

        return true;
    }

    /**
     * Handle /relic despawn <relic>
     */
    private boolean handleDespawn(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.despawn")) return true;

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /relic despawn <relic>");
            return true;
        }

        String relicId = args[1].toLowerCase();
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);

        if (relic == null) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Check if relic exists
        if (!plugin.getRelicManager().relicExistsInWorld(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-in-world"));
            return true;
        }

        // Despawn the relic
        if (plugin.getRelicManager().despawnRelic(relicId)) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-despawned")
                .replace("{relic}", TextUtil.stripColor(relic.getDisplayName())));
        } else {
            sendMessage(sender, "&cFailed to despawn relic.");
        }

        return true;
    }

    /**
     * Handle /relic stats [player]
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.stats")) return true;

        // Determine target player
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendMessage(sender, plugin.getConfigManager().getMessage("player-not-found")
                    .replace("{player}", args[1]));
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sendMessage(sender, "&cUsage: /relic stats <player>");
            return true;
        }

        // Show stats header
        sendMessage(sender, plugin.getConfigManager().getRawMessage("stats-header")
            .replace("{player}", target.getName()));

        boolean hasStats = false;

        // Show stats for each relic
        for (Relic relic : plugin.getRelicRegistry().getEnabledRelics()) {
            String relicId = relic.getId();
            UUID uuid = target.getUniqueId();

            long timeHeld = plugin.getStatsManager().getTotalTimeHeld(uuid, relicId);
            int kills = plugin.getStatsManager().getKills(uuid, relicId);
            int deaths = plugin.getStatsManager().getDeaths(uuid, relicId);
            int acquired = plugin.getStatsManager().getTimesAcquired(uuid, relicId);

            if (timeHeld > 0 || kills > 0 || deaths > 0 || acquired > 0) {
                hasStats = true;
                String relicName = TextUtil.stripColor(relic.getDisplayName());

                if (relic.isTrackTimeHeld() && timeHeld > 0) {
                    sendMessage(sender, plugin.getConfigManager().getRawMessage("stats-time-held")
                        .replace("{relic}", relicName)
                        .replace("{time}", TextUtil.formatTime(timeHeld)));
                }
                if (relic.isTrackKills() && kills > 0) {
                    sendMessage(sender, plugin.getConfigManager().getRawMessage("stats-crown-kills")
                        .replace("{relic}", relicName)
                        .replace("{kills}", String.valueOf(kills)));
                }
            }
        }

        if (!hasStats) {
            sendMessage(sender, plugin.getConfigManager().getRawMessage("stats-no-data"));
        }

        return true;
    }

    /**
     * Handle /relic leaderboard [relic]
     */
    private boolean handleLeaderboard(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.leaderboard")) return true;

        // Default to crown if no relic specified
        String relicId = args.length > 1 ? args[1].toLowerCase() : "crown";

        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Get leaderboard
        int limit = plugin.getConfigManager().getLeaderboardSize();
        List<StatsManager.LeaderboardEntry> entries =
            plugin.getStatsManager().getTimeLeaderboard(relicId, limit);

        // Show header
        sendMessage(sender, plugin.getConfigManager().getRawMessage("leaderboard-header")
            .replace("{relic}", TextUtil.stripColor(relic.getDisplayName())));

        if (entries.isEmpty()) {
            sendMessage(sender, plugin.getConfigManager().getRawMessage("leaderboard-empty"));
        } else {
            for (StatsManager.LeaderboardEntry entry : entries) {
                sendMessage(sender, plugin.getConfigManager().getRawMessage("leaderboard-entry")
                    .replace("{rank}", String.valueOf(entry.getRank()))
                    .replace("{player}", entry.getPlayerName())
                    .replace("{value}", TextUtil.formatTime(entry.getValue()))
                    .replace("{time}", TextUtil.formatTime(entry.getValue())));
            }
        }

        sendMessage(sender, plugin.getConfigManager().getRawMessage("leaderboard-footer"));

        return true;
    }

    /**
     * Handle /relic reload
     */
    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "serverrelics.command.reload")) return true;

        plugin.reload();
        sendMessage(sender, plugin.getConfigManager().getMessage("config-reloaded"));

        return true;
    }

    /**
     * Handle /relic stuck [relic]
     * Allows players to vote that a dropped relic is stuck/inaccessible
     */
    private boolean handleStuck(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "serverrelics.command.stuck")) return true;

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        // Default to crown if no relic specified
        String relicId = args.length > 1 ? args[1].toLowerCase() : "crown";

        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null || !relic.isEnabled()) {
            sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-found")
                .replace("{relic}", relicId));
            return true;
        }

        // Cast vote
        StuckVoteManager.VoteResult result = plugin.getStuckVoteManager().castVote(player, relicId);
        String relicName = TextUtil.stripColor(relic.getDisplayName());

        switch (result.getType()) {
            case DISABLED:
                sendMessage(sender, plugin.getConfigManager().getMessage("stuck-disabled"));
                break;

            case NOT_IN_WORLD:
                sendMessage(sender, plugin.getConfigManager().getMessage("relic-not-in-world"));
                break;

            case NOT_DROPPED:
                sendMessage(sender, plugin.getConfigManager().getMessage("stuck-not-dropped")
                    .replace("{relic}", relicName));
                break;

            case ON_COOLDOWN:
                String cooldownTime = TextUtil.formatTime(result.getCooldownRemaining() / 1000);
                sendMessage(sender, plugin.getConfigManager().getMessage("stuck-cooldown")
                    .replace("{time}", cooldownTime));
                break;

            case ALREADY_VOTED:
                sendMessage(sender, plugin.getConfigManager().getMessage("stuck-already-voted"));
                break;

            case VOTE_CAST:
                // Notify the voter
                sendMessage(sender, plugin.getConfigManager().getMessage("stuck-vote-cast")
                    .replace("{current}", String.valueOf(result.getCurrentVotes()))
                    .replace("{required}", String.valueOf(result.getRequiredVotes())));

                // Broadcast to all players
                String broadcastMsg = plugin.getConfigManager().getMessage("stuck-vote-broadcast")
                    .replace("{player}", player.getName())
                    .replace("{relic}", relicName)
                    .replace("{current}", String.valueOf(result.getCurrentVotes()))
                    .replace("{required}", String.valueOf(result.getRequiredVotes()));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player) && p.hasPermission("serverrelics.notify")) {
                        p.sendMessage(TextUtil.colorize(broadcastMsg));
                    }
                }
                break;

            case RELOCATED:
                // Broadcast relocation to everyone
                String relocateMsg = plugin.getConfigManager().getMessage("stuck-relocated")
                    .replace("{relic}", relicName)
                    .replace("{location}", formatLocation(result.getNewLocation()));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("serverrelics.notify")) {
                        p.sendMessage(TextUtil.colorize(relocateMsg));
                    }
                }
                break;
        }

        return true;
    }

    /**
     * Check permission and send message if denied
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("serverrelics.admin")) {
            return true;
        }
        sendMessage(sender, plugin.getConfigManager().getMessage("no-permission"));
        return false;
    }

    /**
     * Send a colored message to the sender
     */
    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(TextUtil.colorize(message));
    }

    /**
     * Format a location as a string
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

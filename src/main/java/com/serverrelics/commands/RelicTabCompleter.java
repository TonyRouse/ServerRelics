package com.serverrelics.commands;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completion for /relic command
 */
public class RelicTabCompleter implements TabCompleter {

    private final ServerRelics plugin;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "help", "give", "take", "locate", "spawn", "despawn", "drop", "stats", "leaderboard", "reload"
    );

    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
        "give", "take", "spawn", "despawn", "drop", "reload"
    );

    public RelicTabCompleter(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: subcommand
            for (String subCmd : SUBCOMMANDS) {
                // Filter admin commands for non-admins
                if (ADMIN_SUBCOMMANDS.contains(subCmd) &&
                    !sender.hasPermission("serverrelics.admin")) {
                    continue;
                }
                if (subCmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "give":
                    // Complete with online player names
                    completions.addAll(getOnlinePlayerNames(args[1]));
                    break;

                case "take":
                    // Complete with online player names who hold relics
                    completions.addAll(getRelicHolderNames(args[1]));
                    break;

                case "locate":
                case "spawn":
                case "despawn":
                case "drop":
                case "leaderboard":
                case "lb":
                case "top":
                    // Complete with relic IDs
                    completions.addAll(getRelicIds(args[1]));
                    break;

                case "stats":
                    // Complete with online player names
                    completions.addAll(getOnlinePlayerNames(args[1]));
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("give")) {
                // Complete with relic IDs
                completions.addAll(getRelicIds(args[2]));
            }
        }

        return completions;
    }

    /**
     * Get online player names matching prefix
     */
    private List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * Get names of players who currently hold relics
     */
    private List<String> getRelicHolderNames(String prefix) {
        List<String> holders = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getRelicManager().playerHoldsAnyRelic(player)) {
                if (player.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                    holders.add(player.getName());
                }
            }
        }
        return holders;
    }

    /**
     * Get relic IDs matching prefix
     */
    private List<String> getRelicIds(String prefix) {
        return plugin.getRelicRegistry().getEnabledRelics().stream()
            .map(Relic::getId)
            .filter(id -> id.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }
}

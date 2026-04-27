package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

/**
 * Blocks PvP toggle commands for players holding relics with force-pvp enabled.
 * Also re-enforces PvP status after any command that might change it.
 */
public class PvPCommandListener implements Listener {

    private final ServerRelics plugin;

    // Commands that toggle PvP status
    private static final Set<String> PVP_COMMANDS = Set.of(
        "pvp", "pvptoggle", "togglepvp", "pvpmanager"
    );

    // Commands that could affect PvP (newbie protection)
    private static final Set<String> NEWBIE_COMMANDS = Set.of(
        "newbie"
    );

    public PvPCommandListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        // Parse the command (remove leading / and get first word)
        String[] parts = message.substring(1).split(" ");
        if (parts.length == 0) return;

        String command = parts[0];

        // Check if player has a force-pvp relic
        Relic forcePvpRelic = getPlayerForcePvpRelic(player);
        if (forcePvpRelic == null) return;

        // Block PvP toggle commands
        if (PVP_COMMANDS.contains(command)) {
            // Check if they're trying to turn it off
            // Block all pvp toggle commands - let them know why
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot toggle PvP while holding the ", NamedTextColor.RED)
                .append(TextUtil.colorize(forcePvpRelic.getDisplayName()))
                .append(Component.text("!", NamedTextColor.RED)));

            // Schedule re-enforcement just in case
            plugin.getPvPManagerHook().scheduleEnforcePvP(player);
            plugin.debug("Blocked PvP command from " + player.getName() + " (holding " + forcePvpRelic.getId() + ")");
            return;
        }

        // Block newbie disable if it would turn off PvP protection
        if (NEWBIE_COMMANDS.contains(command) && parts.length > 1 && parts[1].equals("disable")) {
            // Let them disable newbie protection but ensure PvP stays on
            plugin.getPvPManagerHook().scheduleEnforcePvP(player);
            return;
        }
    }

    /**
     * Get a force-pvp relic that the player is currently holding.
     * Returns null if player has no such relic.
     *
     * Note: We don't check active slot here - if a player holds a force-pvp relic
     * anywhere in their inventory, they cannot toggle PvP off. This matches
     * the behavior where PvP is forced on when picking up the relic.
     */
    private Relic getPlayerForcePvpRelic(Player player) {
        java.util.List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(player);
        plugin.debug("PvPCommandListener: Player " + player.getName() + " holds relics: " + heldRelics);

        for (String relicId : heldRelics) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (relic == null) {
                plugin.debug("PvPCommandListener: Relic " + relicId + " not found in registry");
                continue;
            }

            // Check if this relic forces PvP - if so, block command regardless of slot
            if (relic.getRestrictions().isForcePvp()) {
                plugin.debug("PvPCommandListener: Relic " + relicId + " forces PvP, blocking command");
                return relic;
            }
        }
        return null;
    }

    /**
     * Find a relic item in a player's inventory
     */
    private org.bukkit.inventory.ItemStack findRelicItem(Player player, Relic relic) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        // Check cursor first
        if (relic.isThisRelic(player.getItemOnCursor())) {
            plugin.debug("PvPCommandListener: Found relic on CURSOR");
            return player.getItemOnCursor();
        }

        // Check armor slots
        if (relic.isThisRelic(inv.getHelmet())) {
            plugin.debug("PvPCommandListener: Found relic in HELMET slot");
            return inv.getHelmet();
        }
        if (relic.isThisRelic(inv.getChestplate())) {
            plugin.debug("PvPCommandListener: Found relic in CHESTPLATE slot");
            return inv.getChestplate();
        }
        if (relic.isThisRelic(inv.getLeggings())) {
            plugin.debug("PvPCommandListener: Found relic in LEGGINGS slot");
            return inv.getLeggings();
        }
        if (relic.isThisRelic(inv.getBoots())) {
            plugin.debug("PvPCommandListener: Found relic in BOOTS slot");
            return inv.getBoots();
        }

        // Check offhand
        if (relic.isThisRelic(inv.getItemInOffHand())) {
            plugin.debug("PvPCommandListener: Found relic in OFFHAND");
            return inv.getItemInOffHand();
        }

        // Check main inventory
        for (int i = 0; i < inv.getContents().length; i++) {
            org.bukkit.inventory.ItemStack item = inv.getContents()[i];
            if (relic.isThisRelic(item)) {
                plugin.debug("PvPCommandListener: Found relic in inventory slot " + i);
                return item;
            }
        }

        return null;
    }
}

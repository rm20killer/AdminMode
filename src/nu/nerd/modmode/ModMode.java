package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * The main plugin class.
 */
public class ModMode extends JavaPlugin {

    /**
     * This plugin.
     */
    public static ModMode PLUGIN;

    /**
     * This plugin's configuration.
     */
    public static Configuration CONFIG;

    /**
     * Cache of players currently in ModMode.
     */
    static final HashSet<UUID> MODMODE = new HashSet<>();

    /**
     * Cache of players currently vanished.
     */
    static final HashSet<UUID> VANISHED = new HashSet<>();

    /**
     * @see JavaPlugin#onEnable().
     */
    @Override
    public void onEnable() {
        PLUGIN = this;
        CONFIG = new Configuration();
        new ModModeListener();
        new Commands();

        Plugin logblock = getServer().getPluginManager().getPlugin("logblock");
        if (logblock != null && logblock.isEnabled()) {
            new LogBlockListener();
        }

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String actionBar = null;
                if (isModMode(player)) {
                    actionBar = String.format("%sYou are currently in ModMode", ChatColor.GREEN);
                    if (!isVanished(player)) {
                        actionBar += String.format(" %s(%sunvanished%s)", ChatColor.GRAY, ChatColor.RED, ChatColor.GRAY);
                    }
                } else if (isVanished(player)) {
                    actionBar = String.format("%sYou are currently vanished", ChatColor.BLUE);
                }
                if (actionBar != null) {
                    player.sendActionBar(actionBar);
                }
            }
        }, 1, 20);

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, () -> {
            for (Player a : Bukkit.getOnlinePlayers()) {
                for (Player b : Bukkit.getOnlinePlayers()) {
                    if (canPlayerASeePlayerB(a, b)) {
                        a.showPlayer(this, b);
                    } else {
                        a.hidePlayer(this, b);
                    }
                }
            }
        }, 1, 10);
    }

    private static boolean canPlayerASeePlayerB(Player a, Player b) {
        if (Permissions.isAdmin(a)) return true;
        if (PLUGIN.isModMode(a)) return true;
        if (PLUGIN.isVanished(b)) return false;
        return true;
    }

    /**
     * @see JavaPlugin#onDisable().
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        CONFIG.save();
    }

    /**
     * Toggles the player's ModMode state.
     *
     * @param player the player.
     */
    void toggleModMode(Player player) {
        final long timeStart = System.currentTimeMillis();
        boolean isAdmin = player.hasPermission(Permissions.ADMIN);
        boolean enteringModMode = !this.isModMode(player);
        boolean vanished = enteringModMode || (isAdmin && CONFIG.loggedOutVanished(player));
        runCommands(player, enteringModMode ? CONFIG.BEFORE_ACTIVATION_COMMANDS : CONFIG.BEFORE_DEACTIVATION_COMMANDS);
        Permissions
            .updatePermissions(player, enteringModMode) // off main thread
            .thenRun(() -> { // back on main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    if (enteringModMode) {
                        MODMODE.add(player.getUniqueId());
                    } else {
                        if (CONFIG.loggedOutVanished(player)) {
                            if (!isAdmin) {
                                getServer().broadcastMessage(CONFIG.getJoinMessage(player));
                            }
                            // we don't need to know this anymore
                            CONFIG.setLoggedOutVanished(player, false);
                        }
                        MODMODE.remove(player.getUniqueId());
                    }
                    // Save player data for the old ModMode state and load for the new.
                    PlayerState.savePlayerData(player, !enteringModMode);
                    PlayerState.loadPlayerData(player, enteringModMode);

                    this.setVanished(player, vanished);
                    this.runCommands(player, enteringModMode ? CONFIG.AFTER_ACTIVATION_COMMANDS : CONFIG.AFTER_DEACTIVATION_COMMANDS);
                    this.restoreFlight(player, enteringModMode);

                    long duration = System.currentTimeMillis() - timeStart;
                    player.sendMessage(String.format("%sYou are %s in ModMode %s(took %d ms, %.2f ticks)",
                        ChatColor.RED,
                        enteringModMode ? "now" : "no longer",
                        ChatColor.GRAY,
                        duration,
                        (double) duration/50));
                    log("Task took " + duration + " ms.");
                });
            });
    }

    /**
     * Sets the player's current vanish state.
     *
     * @param player the player.
     * @param isVanishing true to vanish; false to unvanish.
     */
    void setVanished(Player player, boolean isVanishing) {
        Bukkit.getOnlinePlayers()
              .stream()
              .filter((otherPlayer) -> otherPlayer != player)
              .forEach((otherPlayer) -> {
                  if (!isVanishing) {
                      otherPlayer.showPlayer(this, player);
                  } else if (!this.isModMode(otherPlayer) && !Permissions.isAdmin(otherPlayer)) {
                      otherPlayer.hidePlayer(this, player);
                  }
              });
        if (isVanishing) {
            VANISHED.add(player.getUniqueId());
        } else {
            VANISHED.remove(player.getUniqueId());
        }
        ScoreboardManager.reconcilePlayerWithVanishState(player);
    }

    /**
     * Return true if the player is currently in ModMode.
     *
     * @param player the Player.
     * @return true if the player is currently in ModMode.
     */
    public boolean isModMode(Player player) {
        return MODMODE.contains(player.getUniqueId());
    }

    /**
     * Returns true if the player is in ModMode *or* is vanished, i.e. in a
     * transcendental state.
     *
     * @param player the player.
     * @return true if the player is in ModMode or is vanished.
     */
    public boolean isTranscendental(Player player) {
        return player != null && (this.isModMode(player) || this.isVanished(player));
    }

    /**
     * Returns true if the given player is vanished.
     *
     * @param player the player.
     * @return true if the player is vanished.
     */
    public boolean isVanished(Player player) {
        return player != null && VANISHED.contains(player.getUniqueId());
    }

    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player the player.
     * @param isInModMode true if the player is in ModMode.
     */
    void restoreFlight(Player player, boolean isInModMode) {
        player.setAllowFlight((isInModMode && CONFIG.ALLOW_FLIGHT) || player.getGameMode() == GameMode.CREATIVE);
    }

    /**
     * Run all of the commands in the List of Strings.
     *
     * @param player the moderator causing the commands to run.
     * @param commands the commands to run.
     */
    private void runCommands(Player player, LinkedHashSet<String> commands) {
        for (String command : commands) {
            // dispatchCommand() doesn't cope with a leading '/' in commands
            if (command.length() > 0 && command.charAt(0) == '/') {
                command = command.substring(1);
            }
            try {
                if (!Bukkit.getServer().dispatchCommand(player, command)) {
                    log("Command \"" + command + "\" could not be executed.");
                }
            } catch (Exception ex) {
                log("Command \"" + command + "\" raised " + ex.getClass().getName());
            }
        }
    }

    /**
     * A logging method used instead of {@link java.util.logging.Logger} to
     * faciliate prefix coloring.
     *
     * @param msg the message to log.
     */
    static void log(String msg) {
        System.out.println(PREFIX + msg);
    }

    /**
     * This plugin's prefix as a string; for logging.
     */
    private static final String PREFIX = String.format("%s[%sModMode%s]%s",
        ChatColor.DARK_GRAY,
        ChatColor.GREEN,
        ChatColor.DARK_GRAY,
        ChatColor.RESET);

}

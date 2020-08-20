package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The main plugin class.
 */
public class ModMode extends JavaPlugin {

    enum Permissions {
        ADMIN("modmode.admin"),
        MODERATOR("modmode.mod"),
        VANISH("modmode.vanish");

        public String node;

        Permissions(String node) {
            this.node = node;
        }

        public static boolean isAdmin(Player player) {
            return player.hasPermission(Permissions.ADMIN.node);
        }

        public static boolean canModMode(Player player) {
            return player.hasPermission(Permissions.MODERATOR.node);
        }
    }

    /**
     * This plugin.
     */
    public static ModMode PLUGIN;

    /**
     * This plugin's configuration.
     */
    public static Configuration CONFIG;

    /**
     * Cache of players currently in <b>ModMode</b>.
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
            LogBlockListener.INSTANCE.startListening();
        }

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, () ->
            Bukkit.getOnlinePlayers()
                  .stream()
                  .filter(ModMode.PLUGIN::isTranscendental)
                  .forEach(player -> {
                      String actionBar = null;
                      if (isModMode(player)) {
                          actionBar = String.format("%sYou are currently in %s", ChatColor.GREEN, ModMode.Permissions.isAdmin(player) ? "AdminMode" : "ModMode");
                          if (!isVanished(player)) {
                              actionBar += String.format(" %s(%sunvanished%s)", ChatColor.GRAY, ChatColor.RED, ChatColor.GRAY);
                          }
                      } else if (isVanished(player)) {
                          actionBar = String.format("%sYou are currently vanished", ChatColor.BLUE);
                      }
                      if (actionBar != null) {
                          player.sendActionBar(actionBar);
                      }
                  })
        , 1, 20);

        Bukkit.getScheduler().runTaskTimer(ModMode.PLUGIN, ModMode::updatePlayerVisibility, 0, 5);
    }

    private static void updatePlayerVisibility() {
        for (Player a : Bukkit.getOnlinePlayers()) {
            for (Player b : Bukkit.getOnlinePlayers()) {
                if (canPlayerASeePlayerB(a, b)) {
                    a.showPlayer(ModMode.PLUGIN, b);
                } else {
                    a.hidePlayer(ModMode.PLUGIN, b);
                }
            }
        }
    }

    private static boolean canPlayerASeePlayerB(Player a, Player b) {
        if (ModMode.Permissions.isAdmin(a)) return true;
        if (ModMode.isModMode(a)) return true;
        return !PLUGIN.isVanished(b);
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
        boolean isAdmin = ModMode.Permissions.isAdmin(player);
        boolean enteringModMode = !ModMode.isModMode(player);
        runCommands(player, enteringModMode ? CONFIG.BEFORE_ACTIVATION_COMMANDS : CONFIG.BEFORE_DEACTIVATION_COMMANDS);

        LuckPermsHook.INSTANCE
            .updatePermissions(player, enteringModMode)
            .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                Consumer<UUID> addOrRemoveFunc = enteringModMode ? MODMODE::add : MODMODE::remove;
                addOrRemoveFunc.accept(player.getUniqueId());

                // Save player data for the old ModMode state and load for the new.
                PlayerState.savePlayerData(player, !enteringModMode);
                PlayerState.loadPlayerData(player, enteringModMode);

                this.setVanished(player, enteringModMode);
                this.runCommands(player, enteringModMode ? CONFIG.AFTER_ACTIVATION_COMMANDS : CONFIG.AFTER_DEACTIVATION_COMMANDS);
                this.restoreFlight(player, enteringModMode);

                if (!enteringModMode && player.getGameMode() == GameMode.CREATIVE) {
                    player.setGameMode(GameMode.SURVIVAL);
                }

                long duration = System.currentTimeMillis() - timeStart;
                player.sendMessage(String.format("%sYou are %s in %s %s(took %d ms, %.2f ticks)",
                                                 ChatColor.RED,
                                                 enteringModMode ? "now" : "no longer",
                                                 isAdmin ? "AdminMode" : "ModMode",
                                                 ChatColor.GRAY,
                                                 duration,
                                                 (double) duration / 50));
                log("Task took " + duration + " ms.");
            }));
    }

    /**
     * Sets the player's current vanish state.
     *
     * @param player the player.
     * @param isVanishing true to vanish; false to unvanish.
     */
    void setVanished(@NotNull Player player, boolean isVanishing) {
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
    public static boolean isModMode(@NotNull Player player) {
        return MODMODE.contains(player.getUniqueId());
    }

    /**
     * Returns true if the player is in ModMode *or* is vanished, i.e. in a
     * transcendental state.
     *
     * @param player the player.
     * @return true if the player is in ModMode or is vanished.
     */
    public boolean isTranscendental(@NotNull Player player) {
        return ModMode.isModMode(player) || this.isVanished(player);
    }

    /**
     * Returns true if the given player is vanished.
     *
     * @param player the player.
     * @return true if the player is vanished.
     */
    public boolean isVanished(@NotNull Player player) {
        return VANISHED.contains(player.getUniqueId());
    }

    /**
     * Restore flight ability if in ModMode or creative game mode.
     *
     * @param player the player.
     * @param isInModMode true if the player is in ModMode.
     */
    void restoreFlight(@NotNull Player player, boolean isInModMode) {
        player.setAllowFlight((isInModMode && CONFIG.ALLOW_FLIGHT) || player.getGameMode() == GameMode.CREATIVE);
    }

    /**
     * Run all of the commands in the List of Strings.
     *
     * @param player the moderator causing the commands to run.
     * @param commands the commands to run.
     */
    private void runCommands(@NotNull Player player, @NotNull LinkedHashSet<String> commands) {
        for (String command : commands) {
            player.chat(command);
        }
    }

    /**
     * A logging method used instead of {@link java.util.logging.Logger} to
     * faciliate prefix coloring.
     *
     * @param msg the message to log.
     */
    @SuppressWarnings("java:S106")
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

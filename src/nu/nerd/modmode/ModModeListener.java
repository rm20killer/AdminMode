package nu.nerd.modmode;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import static nu.nerd.modmode.ModMode.CONFIG;

/**
 * The plugin's main event-handling class.
 */
public class ModModeListener implements Listener {

    /**
     * Constructor.
     */
    ModModeListener() {
        Bukkit.getPluginManager().registerEvents(this, ModMode.PLUGIN);
    }

    /**
     * Facilitates the persistence of ModMode state across logins.
     */
    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (ModMode.isModMode(player)) {
            ModMode.PLUGIN.setVanished(player, true);
            ModMode.PLUGIN.restoreFlight(player, true);
            ScoreboardManager.reconcilePlayerWithVanishState(player);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player player && ModMode.PLUGIN.isVanished(player)) {
            e.setCancelled(true);
        }
    }

    /**
     * Disallows vanished players and players in ModMode from dropping items.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (ModMode.PLUGIN.isVanished(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /**
     * Disallows entities from targeting vanished players and players in
     * ModMode, e.g. hostile mobs, parrots.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player player && ModMode.PLUGIN.isTranscendental(player)) {
            e.setCancelled(true);
        }
    }

    /**
     * Disallows vanished players and players in ModMode from damaging other
     * players.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (ModMode.PLUGIN.isTranscendental(victim)) {
                // Extinguish view-obscuring fires.
                victim.setFireTicks(0);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Updates the player's WorldeditCache and allow-flight status upon
     * changing worlds.
     */
    @EventHandler(ignoreCancelled = true)
    public static void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (CONFIG.ALLOW_FLIGHT) {
                boolean flightState = ModMode.isModMode(player);
                player.setAllowFlight(flightState);
            }
        }
    }

    /**
     * Restores a player's flight ability upon changing game modes.
     */
    @EventHandler(ignoreCancelled = true)
    public static void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(ModMode.PLUGIN, () -> {
            boolean flightState = ModMode.isModMode(player);
            ModMode.PLUGIN.restoreFlight(player, flightState);
        });
    }

    /**
     * Prevents the depletion of hunger level for players in ModMode.
     */
    @EventHandler(ignoreCancelled = true)
    public static void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (ModMode.isModMode(player)) {
                if (player.getFoodLevel() != 20) {
                    player.setFoodLevel(20);
                }
                event.setCancelled(true);
            }
        }
    }

}

package nu.nerd.modmode;

import de.diddiz.LogBlock.Actor;
import de.diddiz.LogBlock.events.BlockChangePreLogEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * The LogBlock event-handling class.
 */
public class LogBlockListener implements Listener {

    public static LogBlockListener INSTANCE = new LogBlockListener();

    private LogBlockListener() { }

    public void startListening() {
        ModMode.PLUGIN.getServer().getPluginManager().registerEvents(this, ModMode.PLUGIN);
    }

    /**
     * Ensures edits made by players in ModMode are logged with that player's
     * ModMode name.
     */
    @EventHandler
    public static void onLogBlockPreLogEvent(BlockChangePreLogEvent event) {
        Player player;
        try {
            UUID actorUUID = UUID.fromString(event.getOwnerActor().getUUID());
            player = ModMode.PLUGIN.getServer().getPlayer(actorUUID);
        } catch (Exception e) {
            // probably liquid flow or something
            return;
        }
        if (player != null && ModMode.isModMode(player)) {
            Actor actor = new Actor("modmode_" + player.getName());
            event.setOwner(actor);
        }
    }

}

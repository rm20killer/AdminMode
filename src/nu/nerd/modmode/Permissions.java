package nu.nerd.modmode;


import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Permissions {

    private static final LuckPerms API = LuckPermsProvider.get();

    private static final Track MODMODE_TRACK = API.getTrackManager().getTrack("modmode-track");

    private Permissions() { }

    public static boolean canModMode(Player player) {
        return player.hasPermission(TOGGLE);
    }

    /**
     * Return true if the player has Admin permissions.
     *
     * That is, the player has permissions in excess of those of the ModMode
     * permission group. This is a different concept from Permissions.OP,
     * which merely signifies that the player can administer this plugin.
     *
     * @return true for Admins, false for Moderators and default players.
     */
    public static boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN);
    }

    private static CompletableFuture<User> getUser(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        if (API.getUserManager().isLoaded(uuid)) {
            return CompletableFuture.completedFuture(API.getUserManager().getUser(uuid));
        } else {
            return API.getUserManager().loadUser(uuid);
        }
    }

    static CompletableFuture<Void> updatePermissions(Player player, boolean promote) {
        CompletableFuture<User> futureUser = getUser(player);
        if (isAdmin(player)) {
            return CompletableFuture.completedFuture(null);
        }
        return futureUser.thenComposeAsync(user -> CompletableFuture.supplyAsync(() -> {
            if (user != null && !isAdmin(player)) {
                if (promote) {
                    MODMODE_TRACK.promote(user, ImmutableContextSet.empty());
                } else {
                    MODMODE_TRACK.demote(user, ImmutableContextSet.empty());
                }
                return user;
            }
            return null;
         })).thenAcceptAsync(u -> {
             if (u != null) {
                 API.getUserManager().saveUser(u);
             }
        });
    }

    private static final String MODMODE = "modmode.";
    public static final String VANISH = MODMODE + "vanish";
    public static final String TOGGLE = MODMODE + "toggle";
    public static final String ADMIN = MODMODE + "admin";
    public static final String OP = MODMODE + "op";

}
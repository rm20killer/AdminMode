package nu.nerd.modmode;


import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {

    public static final LuckPermsHook INSTANCE = new LuckPermsHook();

    private LuckPermsHook() { }

    @Nullable
    private Track getTrack(@NotNull Player player) {
        return LuckPermsProvider.get()
                                .getTrackManager()
                                .getTrack(ModMode.Permissions.isAdmin(player) ? "adminmode": "modmode");
    }

    @NotNull
    private CompletableFuture<User> getUser(@NotNull Player player) {
        var userManager = LuckPermsProvider.get().getUserManager();
        UUID uuid = player.getUniqueId();
        if (userManager.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(userManager.getUser(uuid));
        } else {
            return userManager.loadUser(uuid);
        }
    }

    @NotNull
    public CompletableFuture<Void> updatePermissions(@NotNull Player player, @NotNull boolean promote) {
        var track = this.getTrack(player);
        if (track == null) {
            ModMode.log("Failed to find track for " + player.getDisplayName() + " for action " + (promote ? "promote" : "demote"));
            return CompletableFuture.completedFuture(null);
        }
        return getUser(player)
            .thenAcceptAsync(user -> {
                if (user != null) {
                    if (promote) {
                        track.promote(user, ImmutableContextSet.empty());
                    } else {
                        track.demote(user, ImmutableContextSet.empty());
                    }
                    LuckPermsProvider.get().getUserManager().saveUser(user);
                }
            });
    }

}

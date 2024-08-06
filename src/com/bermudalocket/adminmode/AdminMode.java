package com.bermudalocket.adminmode;

import net.citizensnpcs.api.event.NPCLookCloseChangeTargetEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AdminMode extends JavaPlugin implements Listener {

    private String serverName;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        this.serverName = getConfig().getString("server-name");
        if (serverName == null) {
            log("You must give this server a name in config.yml.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }


        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player a : Bukkit.getOnlinePlayers()) {
                for (Player b : Bukkit.getOnlinePlayers()) {
                    if (canPlayerASeePlayerB(a, b)) {
                        a.showPlayer(this, b);
                    } else {
                        a.hidePlayer(this, b);
                    }
                }
            }
        }, 0, 5);
    }

    private static boolean canPlayerASeePlayerB(Player a, Player b) {
        return true;
    }

    void toggle(Player player) {
        final long timeStart = System.currentTimeMillis();
        boolean isAdmin = player.hasPermission("group.admin");
        boolean enteringModMode = !(player.hasPermission("group.modmode") || player.hasPermission("group.adminmode"));

        var lp = LuckPermsProvider.get();

        var track = lp.getTrackManager().getTrack(isAdmin ? "adminmode" : "modmode");
        if (track == null) {
            log("Failed to find track for " + player.getName());
            return;
        }

        lp.getUserManager()
                .loadUser(player.getUniqueId())
                .thenApply(user -> {
                    var context = lp.getContextManager().getContext(user);
                    if (context.isPresent()) {
                        var server = context.get().getValues("server").stream().findFirst();
                        var contextSet = server.map(s -> ImmutableContextSet.of("server", s)).orElseGet(ImmutableContextSet::empty);
                        Result result = enteringModMode ? track.promote(user, contextSet) : track.demote(user, contextSet);
                        LuckPermsProvider.get().getUserManager().saveUser(user);
                        return result;
                    }
                    return null;
                }).thenAccept(result -> {
                    if (result == null) {
                        player.sendMessage(ChatColor.RED + "There was an error obtaining your context from LuckPerms.");
                        return;
                    }
                    if (!result.wasSuccessful()) {
                        player.sendMessage(ChatColor.RED + "Your permissions change was not successful.");

                        String msg, msg2;
                        if (result instanceof PromotionResult promotionResult) {
                            msg = "- direction: " + promotionResult.getGroupFrom().orElse("EMPTY") + " -> " + promotionResult.getGroupTo().orElse("EMPTY");
                            msg2 = "- status: " + promotionResult.getStatus().name();
                        } else if (result instanceof DemotionResult demotionResult) {
                            msg = "- direction: " + demotionResult.getGroupFrom().orElse("EMPTY") + " -> " + demotionResult.getGroupTo().orElse("EMPTY");
                            msg2 = "- status: " + demotionResult.getStatus().name();
                        } else {
                            msg = "Error:";
                            msg2 = "Could not determine result type.";
                        }
                        player.sendMessage(ChatColor.RED + msg);
                        player.sendMessage(ChatColor.RED + msg2);
                    }
                    Bukkit.getScheduler().runTask(this, () -> { // main thread
                        // Save player data for the old ModMode state and load for the new.
                        this.savePlayerData(player, !enteringModMode);
                        this.loadPlayerData(player, enteringModMode);

                        if (!enteringModMode && player.getGameMode() == GameMode.CREATIVE) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                        if (!enteringModMode && player.getGameMode() == GameMode.SPECTATOR) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                        this.restoreFlight(player);

                        long duration = System.currentTimeMillis() - timeStart;
                        player.sendMessage(String.format("%sYou are %s in StaffMode %s(took %d ms, %.2f ticks)",
                                ChatColor.RED,
                                enteringModMode ? "now" : "no longer",
                                ChatColor.GRAY,
                                duration,
                                (double) duration / 50));
                        log("Task took " + duration + " ms.");
                    });
                });
    }

    private void restoreFlight(@NotNull Player player) {
        player.setAllowFlight(isPlayerInModMode(player) || player.getGameMode() == GameMode.CREATIVE);
    }

    private boolean isPlayerInModMode(@NotNull Player player) {
        return player.hasPermission("group.modmode") || player.hasPermission("group.adminmode");
    }

    // - Commands

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String name, String[] args) {
        if (command.getName().equalsIgnoreCase("modmode") && sender instanceof Player player) {
            this.toggle(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return null;
    }

    // - Event handlers

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (isPlayerInModMode(event.getPlayer())) {
            event.message(null); // hush your mouth
        }
    }

    @EventHandler
    public void onNPCLookEvent(NPCLookCloseChangeTargetEvent event) {
        if (event.getNewTarget() != null && isPlayerInModMode(event.getNewTarget())) {
            event.setNewTarget(null);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (isPlayerInModMode(player)) {
            this.restoreFlight(player);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player player && isPlayerInModMode(player)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && isPlayerInModMode(victim)) {
            victim.setFireTicks(0);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        if (isPlayerInModMode(event.getPlayer())) {
            this.restoreFlight(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        this.restoreFlight(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isPlayerInModMode(player)) {
            player.setFoodLevel(20);
            event.setCancelled(true);
        }
    }

    // - Log

    static void log(String msg) {
        System.out.println(PREFIX + msg);
    }

    private static final String PREFIX = String.format("%s[%sModMode%s]%s",
            ChatColor.DARK_GRAY,
            ChatColor.GREEN,
            ChatColor.DARK_GRAY,
            ChatColor.RESET);

    // - Player state

    /**
     * Return the File used to store the player's normal or ModMode state.
     *
     * @param player the player.
     * @param isModMode true if the data is for the ModMode state.
     * @return the File used to store the player's normal or ModMode state.
     */
    private File getStateFile(Player player, boolean isModMode) {
        var playersDir = new File(this.getDataFolder(), "players");
        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }
        var fileName = player.getUniqueId() + ((isModMode) ? "_modmode" : "_normal") + ".yml";
        return new File(playersDir, fileName);
    }

    private void savePlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);

        // Keep 2 backups of saved player data.
        try {
            File backup1 = new File(stateFile.getPath() + ".1");
            File backup2 = new File(stateFile.getPath() + ".2");
            backup1.renameTo(backup2);
            stateFile.renameTo(backup1);
        } catch (Exception ex) {
            String msg = " raised saving state file backups for " + player.getName()
                    + " (" + player.getUniqueId().toString() + ").";
            log(ex.getClass().getName() + msg);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("health", player.getHealth());
        config.set("food", player.getFoodLevel());
        config.set("experience", player.getLevel() + player.getExp());
        config.set("world", player.getLocation().getWorld().getName());
        config.set("x", player.getLocation().getX());
        config.set("y", player.getLocation().getY());
        config.set("z", player.getLocation().getZ());
        config.set("pitch", player.getLocation().getPitch());
        config.set("yaw", player.getLocation().getYaw());
        config.set("helmet", player.getInventory().getHelmet());
        config.set("chestplate", player.getInventory().getChestplate());
        config.set("leggings", player.getInventory().getLeggings());
        config.set("boots", player.getInventory().getBoots());
        config.set("off-hand", player.getInventory().getItemInOffHand());
        for (PotionEffect potion : player.getActivePotionEffects()) {
            config.set("potions." + potion.getType().getName(), potion);
        }
        ItemStack[] inventory = player.getInventory().getContents();
        for (int slot = 0; slot < inventory.length; ++slot) {
            config.set("inventory." + slot, inventory[slot]);
        }

        ItemStack[] enderChest = player.getEnderChest().getContents();
        for (int slot = 0; slot < enderChest.length; ++slot) {
            config.set("enderchest." + slot, enderChest[slot]);
        }

        try {
            config.save(stateFile);
        } catch (Exception exception) {
            log("Failed to save player data for " + player.getName() + "(" + player.getUniqueId() + ")");
        }
    }

    private void loadPlayerData(Player player, boolean isModMode) {
        File stateFile = getStateFile(player, isModMode);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile);
            player.setHealth(config.getDouble("health"));
            player.setFoodLevel(config.getInt("food"));
            float level = (float) config.getDouble("experience");
            player.setLevel((int) Math.floor(level));
            player.setExp(level - player.getLevel());

            if (!isModMode) {
                String world = config.getString("world");
                double x = config.getDouble("x");
                double y = config.getDouble("y");
                double z = config.getDouble("z");
                float pitch = (float) config.getDouble("pitch");
                float yaw = (float) config.getDouble("yaw");

                Bukkit.getScheduler().runTask(this, () -> {
                    var chunkLoadStart = System.currentTimeMillis();
                    player.sendMessage(ChatColor.GRAY + "Requested chunks from server. Waiting...");
                    CompletableFuture<Boolean> future = new CompletableFuture<>();
                    var loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
                    loc.getWorld()
                            .getChunkAtAsync(loc)
                            .thenAccept(chunk -> future.complete(player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)))
                            .exceptionally(ex -> { future.completeExceptionally(ex); return null; })
                            .thenRunAsync(() -> {
                                var chunkLoadTime = System.currentTimeMillis() - chunkLoadStart;
                                var msg = String.format("%sSuccessfully loaded chunks in %d ms (%.2f ticks).", ChatColor.GRAY, chunkLoadTime, (double) chunkLoadTime / 50);
                                player.sendMessage(msg);
                            });
                });
            }

            player.getEnderChest().clear();
            player.getInventory().clear();
            player.getInventory().setHelmet(config.getItemStack("helmet"));
            player.getInventory().setChestplate(config.getItemStack("chestplate"));
            player.getInventory().setLeggings(config.getItemStack("leggings"));
            player.getInventory().setBoots(config.getItemStack("boots"));
            player.getInventory().setItemInOffHand(config.getItemStack("off-hand"));

            for (PotionEffect potion : player.getActivePotionEffects()) {
                player.removePotionEffect(potion.getType());
            }

            ConfigurationSection potions = config.getConfigurationSection("potions");
            if (potions != null) {
                for (String key : potions.getKeys(false)) {
                    PotionEffect potion = (PotionEffect) potions.get(key);
                    if (potion != null) {
                        player.addPotionEffect(potion);
                    }
                }
            }

            ConfigurationSection inventory = config.getConfigurationSection("inventory");
            if (inventory != null) {
                for (String key : inventory.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = inventory.getItemStack(key);
                        player.getInventory().setItem(slot, item);
                    } catch (Exception ex) {
                        log("Exception while loading " + player.getName() + "'s inventory: " + ex);
                    }
                }
            }

            ConfigurationSection enderChest = config.getConfigurationSection("enderchest");
            if (enderChest != null) {
                for (String key : enderChest.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = enderChest.getItemStack(key);
                        player.getEnderChest().setItem(slot, item);
                    } catch (Exception ex) {
                        log("Exception while loading " + player.getName() + "'s ender chest: " + ex);
                    }
                }
            }
        } catch (Exception ex) {
            log("Failed to load player data for " + player.getName() + "(" + player.getUniqueId() + ")");
        }
    }

}

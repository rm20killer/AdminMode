package nu.nerd.modmode;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles and exposes this plugin's configuration.
 */
class Configuration {

    /**
     * The configuration.
     */
    private FileConfiguration config;

    /**
     * If true, player data loads and saves are logged to the console.
     */
    public boolean DEBUG;

    /**
     * If true, players in ModMode will be able to fly.
     */
    public boolean ALLOW_FLIGHT;

    public boolean ALLOW_COLLISIONS;

    private final HashMap<Integer, ItemStack> MOD_KIT = new HashMap<>();

    /**
     * Constructor.
     */
    public Configuration() {
        reload();
        for (String uuidString : config.getStringList("modmode-cache")) {
            try {
                var uuid = UUID.fromString(uuidString);
                ModMode.MODMODE.add(uuid);
            } catch (IllegalArgumentException e) {
                ModMode.log("Failed to deserialize a UUID in the modmode-cache.");
            }
        }
    }

    /**
     * Load the configuration.
     */
    void reload() {
        ModMode.PLUGIN.saveDefaultConfig();
        ModMode.PLUGIN.reloadConfig();
        config = ModMode.PLUGIN.getConfig();

        DEBUG = config.getBoolean("debug.playerdata");
        ALLOW_FLIGHT = config.getBoolean("allow.flight", true);
        ALLOW_COLLISIONS = config.getBoolean("allow.collisions", true);

        MOD_KIT.clear();
        ConfigurationSection modKit = config.getConfigurationSection("mod-kit");
        if (modKit != null) {
            for (String key : modKit.getKeys(false)) {
                try {
                    Integer i = Integer.valueOf(key);
                    ItemStack item = config.getItemStack("mod-kit." + i, null);
                    MOD_KIT.put(i, item);
                } catch (Exception e) {
                    ModMode.log("Bad entry in mod-kit in config.yml: " + key);
                }
            }
        }

        BEFORE_ACTIVATION_COMMANDS = getCommandList("commands.activate.before");
        AFTER_ACTIVATION_COMMANDS = getCommandList("commands.activate.after");
        BEFORE_DEACTIVATION_COMMANDS = getCommandList("commands.deactivate.before");
        AFTER_DEACTIVATION_COMMANDS = getCommandList("commands.deactivate.after");
    }

    /**
     * Save the configuration.
     */
    void save() {
        config.set("modmode-cache", new ArrayList<>(
            ModMode.MODMODE.stream().map(UUID::toString).collect(Collectors.toList())
        ));
        for (Integer i : MOD_KIT.keySet()) {
            config.set("mod-kit." + i, MOD_KIT.get(i));
        }
        ModMode.PLUGIN.saveConfig();
    }

    /**
     * Returns the current mod kit as a map from inventory index to ItemStack.
     *
     * @return the current mod kit.
     */
    public Map<Integer, ItemStack> getModKit() {
        return new HashMap<>(MOD_KIT);
    }

    void saveModKit(PlayerInventory inventory) {
        MOD_KIT.clear();
        // slots 0 - 8 are hotbar
        for (int i = 0; i <= 8; i++) {
            MOD_KIT.put(i, inventory.getItem(i));
        }
    }

    private LinkedHashSet<String> getCommandList(String key) {
        return new LinkedHashSet<>(config.getStringList(key));
    }

    /**
     * Commands executed immediately before ModMode is activated.
     */
    public LinkedHashSet<String> BEFORE_ACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately after ModMode is activated.
     */
    public LinkedHashSet<String> AFTER_ACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately before ModMode is deactivated.
     */
    public LinkedHashSet<String> BEFORE_DEACTIVATION_COMMANDS = new LinkedHashSet<>();

    /**
     * Commands executed immediately after ModMode is deactivated.
     */
    public LinkedHashSet<String> AFTER_DEACTIVATION_COMMANDS = new LinkedHashSet<>();

}

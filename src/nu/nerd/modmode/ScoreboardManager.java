package nu.nerd.modmode;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * A class which encapsulates all of this plugin's hooked functionality
 * dependent on the NerdBoard plugin.
 */
final class ScoreboardManager {

    private static final Scoreboard SCOREBOARD = ModMode.PLUGIN.getServer().getScoreboardManager().getNewScoreboard();

    private static final Team modModeTeam = configureTeam("Mod Mode", ChatColor.GREEN, ModMode.CONFIG.ALLOW_COLLISIONS);
    private static final Team vanishedTeam = configureTeam("Vanished", ChatColor.BLUE, ModMode.CONFIG.ALLOW_COLLISIONS);
    private static final Team defaultTeam = configureTeam("Default", null, false);

    private ScoreboardManager() { }

    /**
     * Configures a {@link Team} with the given name, color, and collision
     * status.
     *
     * @param name the name of the team.
     * @param color (nullable) the team's color (i.e. player name color).
     * @param collisions if entity collisions should be enabled for
     *                        players on this team.
     * @return a {@link Team} with the given properties.
     */
    private static Team configureTeam(String name, ChatColor color, boolean collisions) {
        Team team = getOrCreateTeam(name);
        if (color != null) {
            team.setColor(color);
        }
        team.setOption(Team.Option.COLLISION_RULE, boolToStatus(collisions));
        return team;
    }

    /**
     * Look up a Team by name in the Scoreboard, or create a new one with the
     * specified name if not found.
     *
     * @param name the Team name.
     * @return the Team with that name.
     */
    private static Team getOrCreateTeam(String name) {
        Team team = SCOREBOARD.getTeam(name);
        if (team == null) {
            team = SCOREBOARD.registerNewTeam(name);
        }
        return team;
    }

    /**
     * Assign the player to the Team that corresponds to its vanish and modmode
     * states, and then update their scoreboard if necessary.
     *
     * The Team controls the name tag prefix (colour) and collision detection
     * between players.
     *
     * @param player the player.
     */
    @SuppressWarnings("deprecation")
    public static void reconcilePlayerWithVanishState(Player player) {
        boolean inModMode = ModMode.PLUGIN.isModMode(player);
        boolean isVanished = ModMode.PLUGIN.isVanished(player);
        Team team;
        if (inModMode) {
            team = modModeTeam;
        } else {
            team = isVanished ? vanishedTeam : defaultTeam;
        }
        team.addPlayer(player);
        if (player.getScoreboard() != SCOREBOARD) {
            player.setScoreboard(SCOREBOARD);
        }
    }

    /**
     * Translates a boolean to an {@link org.bukkit.scoreboard.Team.OptionStatus}
     * with the mapping:
     *      true -> OptionStatus.ALWAYS
     *      false -> OptionStatus.NEVER
     *
     * @param bool the boolean.
     * @return the translated OptionStatus.
     */
    private static Team.OptionStatus boolToStatus(boolean bool) {
        return bool ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
    }

}

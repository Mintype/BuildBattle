package org.mintype.buildBattle.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.mintype.buildBattle.game.GameState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManagerBB {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public void create(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "bb",
                "dummy",
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "RU Build Battle"
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("§7 ").setScore(8);

// Players
        Team players = board.registerNewTeam("players");
        players.addEntry("§p");
        players.setPrefix("§aPlayers: §f");
        obj.getScore("§p").setScore(7);

        obj.getScore("§7  ").setScore(6);

// Time
        Team time = board.registerNewTeam("time");
        time.addEntry("§t");
        obj.getScore("§t").setScore(5);

// Theme
        Team theme = board.registerNewTeam("theme");
        theme.addEntry("§a");
        obj.getScore("§a").setScore(4);

// Mode
        Team mode = board.registerNewTeam("mode");
        mode.addEntry("§m");
        obj.getScore("§m").setScore(3);

        obj.getScore("§7   ").setScore(2);

        obj.getScore("§eevents.rumc.club").setScore(1);

        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    public void updateAll(GameState state, int countdown, int gameTime, String theme, int teamSize) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p, state, countdown, gameTime, theme, teamSize);
        }
    }

    public void update(Player p, GameState state, int countdown, int gameTime, String themeName, int teamSize) {

        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            create(p);
            board = boards.get(p.getUniqueId());
        }

        int online = Bukkit.getOnlinePlayers().size();

        // Players
        Team players = board.getTeam("players");
        if (players != null) {
            players.setSuffix(online + "/" + Bukkit.getMaxPlayers());
        }

        Team time = board.getTeam("time");
        Team theme = board.getTeam("theme");
        Team mode = board.getTeam("mode");

        if (time == null || theme == null || mode == null) return;

        String modeText = (teamSize > 1)
                ? "Teams (" + teamSize + ")"
                : "Solo";

        themeName = themeName.isEmpty() ? "TBD" : themeName;

        switch (state) {

            case LOBBY -> {
                time.setPrefix("§eWaiting...");
                theme.setPrefix("§aTheme: §f" + themeName);
                mode.setPrefix("§aMode: §f" + modeText);
            }

            case STARTING -> {
                time.setPrefix("§eStarting in §6" + countdown);
                theme.setPrefix("§aTheme: §f" + themeName);
                mode.setPrefix("§aMode: §f" + modeText);
            }

            case BUILDING -> {
                int min = gameTime / 60;
                int sec = gameTime % 60;

                String timeColor;

                if (gameTime <= 30) {
                    timeColor = "§c"; // red
                } else if (gameTime <= 60) {
                    timeColor = "§6"; // orange/gold
                } else if (gameTime <= 120) {
                    timeColor = "§e"; // yellow
                } else {
                    timeColor = "§a"; // green
                }

                time.setPrefix("§fTime: " + timeColor + min + ":" + String.format("%02d", sec));
                theme.setPrefix("§aTheme: §f" + themeName);
                mode.setPrefix("§aMode: §f" + modeText);
            }

            case VOTING -> {
                time.setPrefix("§eNext plot in §6" + countdown);
                theme.setPrefix("§aTheme: §f" + themeName);
                mode.setPrefix("§aMode: §fVoting");
            }

            case ENDED -> {
                time.setPrefix("§cGame Ended");
                theme.setPrefix("§aTheme: §f" + themeName);
                mode.setPrefix("§aMode: §f" + modeText);
            }
        }
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
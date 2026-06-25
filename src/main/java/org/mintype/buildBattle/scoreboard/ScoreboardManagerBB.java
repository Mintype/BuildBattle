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
                ChatColor.GREEN + "" + ChatColor.BOLD + "Build Battle"
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("§7 ").setScore(6);

        obj.getScore("§aPlayers:").setScore(5);
        obj.getScore("§bStatus:").setScore(4);

        obj.getScore("§7  ").setScore(3);

        obj.getScore("§emode.rumc.club").setScore(2);

        // --- dynamic holders ---
        Team players = board.registerNewTeam("players");
        players.addEntry("§p");
        obj.getScore("§p").setScore(5);

        Team status = board.registerNewTeam("status");
        status.addEntry("§s");
        obj.getScore("§s").setScore(4);

        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    public void updateAll(GameState state, int countdown, int gameTime) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p, state, countdown, gameTime);
        }
    }

    public void update(Player p, GameState state, int countdown, int gameTime) {

        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            create(p);
            board = boards.get(p.getUniqueId());
        }

        int online = Bukkit.getOnlinePlayers().size();

        Team players = board.getTeam("players");
        if (players != null) {
            players.setPrefix("§f" + online + "/" + Bukkit.getMaxPlayers());
        }

        Team status = board.getTeam("status");
        if (status != null) {

            switch (state) {

                case LOBBY -> status.setPrefix("§eWaiting for host");

                case STARTING -> status.setPrefix("§eStarting in §6" + countdown);

                case BUILDING -> {
                    int min = gameTime / 60;
                    int sec = gameTime % 60;
                    status.setPrefix("§eTime: §a" + min + ":" + String.format("%02d", sec));
                }

                case ENDED -> status.setPrefix("§cGame ended");
            }
        }
    }
}
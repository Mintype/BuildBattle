package org.mintype.buildBattle;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.scoreboard.*;

import java.util.*;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mintype.buildBattle.game.GameState;
import org.mintype.buildBattle.plot.PlotManager;
import org.mintype.buildBattle.protection.GameProtectionManager;
import org.mintype.buildBattle.scoreboard.ScoreboardManagerBB;

public final class BuildBattle extends JavaPlugin implements Listener {

    private int countdown = -1;
    private int gameTime = -1; // seconds
    
    private String theme = "";

    private final HashSet<UUID> nightVisionPlayers = new HashSet<>();

    private GameProtectionManager protectionManager;
    private ScoreboardManagerBB scoreboardManager;
    private PlotManager plotManager;

    private GameState gameState = GameState.LOBBY;

    @Override
    public void onEnable() {
        protectionManager = new GameProtectionManager();
        scoreboardManager = new ScoreboardManagerBB();
        plotManager = new PlotManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(protectionManager, this);

        plotManager.startPlotBorders();
    }

    private Location getSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, 0, -14, 0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        p.teleport(getSpawn());
        if (!p.isOp()) {
            p.setGameMode(GameMode.ADVENTURE);
        }

        scoreboardManager.create(e.getPlayer());
        scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        scoreboardManager.remove(e.getPlayer());
        scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () ->
                e.getPlayer().teleport(getSpawn())
        );
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        e.setFoodLevel(20);
        e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        int plotId = plotManager.getPlotId(e.getBlock().getLocation());

        Bukkit.getLogger().info("[BREAK] player=" + p.getName()
                + " plotId=" + plotId);

        if (plotId == 0) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot break outside plots.");
            return;
        }

        if (plotId != plotManager.getPlayerPlot(p)) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot break other plots.");
            return;
        }

        p.sendMessage("§aBreaking in plot #" + plotId);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        int plotId = plotManager.getPlotId(e.getBlock().getLocation());

        Bukkit.getLogger().info("[PLACE] player=" + p.getName()
                + " plotId=" + plotId);

        if (plotId == 0) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot build outside plots.");
            return;
        }

        if (plotId != plotManager.getPlayerPlot(p)) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot build in other plots.");
            return;
        }

        p.sendMessage("§aBuilding in plot #" + plotId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        Action action = e.getAction();

        if (e.getClickedBlock() != null && plotManager.getPlotId(e.getClickedBlock().getLocation().clone().add(0, 1, 0)) == 0) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot break outside plots.");
            return;
        }

        if (e.getClickedBlock() != null && plotManager.getPlotId(e.getClickedBlock().getLocation().clone().add(0, 1, 0)) != plotManager.getPlayerPlot(p)) {
            e.setCancelled(true);
            p.sendMessage("§cYou cannot interact with other plots.");
            return;
        }

        // Only care about right click actions
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        // If holding a placeable block/item, allow (placement handled by BlockPlaceEvent anyway)
        if (e.getItem() != null && e.getItem().getType().isBlock()) {
            return;
        }

        // Otherwise treat as interaction (doors, levers, etc.)
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            int plotId = plotManager.getPlotId(e.getClickedBlock().getLocation());

            if (plotId == 0) {
                e.setCancelled(true);
                p.sendMessage("§cYou cannot interact outside plots.");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player p)) return true;

        if (command.getName().equalsIgnoreCase("nightvision")) {

            UUID id = p.getUniqueId();

            if (nightVisionPlayers.contains(id)) {
                nightVisionPlayers.remove(id);
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.sendMessage("§cNight vision disabled.");
            } else {
                nightVisionPlayers.add(id);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        Integer.MAX_VALUE,
                        0,
                        false,
                        false
                ));
                p.sendMessage("§aNight vision enabled.");
            }

            return true;
        }
        else if (command.getName().equalsIgnoreCase("addtime")) {

            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length != 1) {
                p.sendMessage("§cUsage: /addtime <seconds>");
                return true;
            }

            if (gameState != GameState.BUILDING) {

                if (gameState == GameState.LOBBY) {
                    p.sendMessage("§cGame has not started yet.");
                    return true;
                }

                p.sendMessage("§cYou cannot run this right now!");
                return true;
            }

            int seconds;

            try {
                seconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage("§cInvalid number.");
                return true;
            }

            if (seconds <= 0 || seconds > 3600) {
                p.sendMessage("§cInvalid time range.");
                return true;
            }

            gameTime += seconds; // make sure gameTime is your field

            Bukkit.broadcastMessage("§a+" + seconds + " seconds added!");

            return true;
        }
        else if (command.getName().equalsIgnoreCase("reset")) {

            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            gameState = GameState.LOBBY;
            countdown = -1;
            gameTime = -1;
            theme = "";

            scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());

            nightVisionPlayers.clear();

            plotManager.clearPlots();

            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.setGameMode(GameMode.ADVENTURE);
                pl.getInventory().clear();
                pl.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            Bukkit.broadcastMessage("§cGame has been reset!");

            return true;
        }
        else if (command.getName().equalsIgnoreCase("start")) {

            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            if (gameState != GameState.LOBBY) {
                p.sendMessage("§cGame is already in progress.");
                return true;
            }

            if (Bukkit.getOnlinePlayers().size() < 2) {
                p.sendMessage("§cYou need at least 2 players to start the game.");
                return true;
            }

            startGame();
            return true;
        }
        else if (command.getName().equalsIgnoreCase("floor")) {

            if (gameState != GameState.BUILDING) {
                p.sendMessage("§cYou cannot run this right now!");
                return true;
            }

            if (p.getItemInHand() == null || p.getItemInHand().getType() == Material.AIR) {
                p.sendMessage("§cHold a block in your hand.");
                return true;
            }

            Material mat = p.getItemInHand().getType();

            if (!mat.isBlock()) {
                p.sendMessage("§cThat is not a placeable block.");
                return true;
            }

            int plotId = plotManager.getPlayerPlot(p);

            if (plotId == 0) {
                p.sendMessage("§cYou do not have a plot.");
                return true;
            }

            plotManager.setPlotFloor(plotId, mat);

            p.sendMessage("§aPlot floor set to " + mat.name());

            return true;
        }
        else if  (command.getName().equalsIgnoreCase("teamsize")) {
            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length != 1) {
                p.sendMessage("§cUsage: /teamsize <size>");
                return true;
            }

            if (gameState != GameState.LOBBY) {
                p.sendMessage("§cYou cannot run this right now!");
                return true;
            }

            int teamSize;

            try {
                teamSize = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage("§cInvalid number.");
                return true;
            }

            if (teamSize <= 0 || teamSize > 18) {
                p.sendMessage("§cInvalid size range.");
                return true;
            }

            plotManager.setTeamSize(teamSize);

            scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());

            Bukkit.broadcastMessage("§aTeam size set to " + teamSize + ".");

            return true;
        }

        return false;
    }

    private void startGame() {
        if (gameState != GameState.LOBBY) return;
        gameState = GameState.STARTING;

        countdown = 5;

        plotManager.clearPlots();

        new BukkitRunnable() {
            @Override
            public void run() {

                if (countdown > 0) {
                    countdown--;

                    String subtitle;

                    if (countdown <= 3) {
                        subtitle = "§c" + countdown;
                    } else {
                        subtitle = "§e" + countdown;
                    }

                    if (countdown == 0) {
                        plotManager.assignPlots();
                        setAllGameMode(GameMode.CREATIVE);
                        subtitle = "§aGO!";
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(
                                "",
                                subtitle,
                                2,
                                10,
                                2
                        );
                    }

                    scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
                    return;
                }
                gameState = GameState.BUILDING;
                countdown = -1;

                Bukkit.broadcastMessage("§aGame started!");

                gameTime = 5 * 60; // 300 seconds

                startGameTimer();
                scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
                cancel();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startGameTimer() {

        new BukkitRunnable() {

            @Override
            public void run() {

                // stop if game is not running
                if (gameState != GameState.BUILDING) {
                    cancel();
                    return;
                }

                // tick down game time
                if (gameTime > 0) {
                    gameTime--;
                    scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
                    return;
                }

                // TIME UP → end game
                endGame();

                cancel();
            }

        }.runTaskTimer(this, 0L, 20L); // every 1 second
    }

    private void endGame() {
        gameState = GameState.ENDED;

        Bukkit.broadcastMessage("§cGame ended!");

        setAllGameMode(GameMode.SPECTATOR);

        scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
    }

    private void setAllGameMode(GameMode gameMode) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(gameMode);
        }
    }
}
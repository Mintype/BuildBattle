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
        scoreboardManager.updateAll(gameState, countdown, gameTime);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        scoreboardManager.updateAll(gameState, countdown, gameTime);
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

        p.sendMessage("§aBuilding in plot #" + plotId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        Action action = e.getAction();

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

        if (!p.isOp()) {
            p.sendMessage("§cNo permission.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("start")) {
            startGame();
            return true;
        }

        return false;
    }

    private void startGame() {
        if (gameState != GameState.LOBBY) return;
        gameState = GameState.STARTING;

        countdown = 10;

//        clearPlots();
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

                    scoreboardManager.updateAll(gameState, countdown, gameTime);
                    return;
                }
                gameState = GameState.BUILDING;
                countdown = -1;

                Bukkit.broadcastMessage("§aGame started!");

                gameTime = 5 * 60; // 300 seconds

                startGameTimer();
                scoreboardManager.updateAll(gameState, countdown, gameTime);
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
                    scoreboardManager.updateAll(gameState, countdown, gameTime);
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

        scoreboardManager.updateAll(gameState, countdown, gameTime);
    }

    private void setAllGameMode(GameMode gameMode) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(gameMode);
        }
    }
}
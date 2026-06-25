package org.mintype.buildBattle;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.util.*;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mintype.buildBattle.game.GameState;
import org.mintype.buildBattle.plot.PlotManager;
import org.mintype.buildBattle.protection.GameProtectionManager;
import org.mintype.buildBattle.scoreboard.ScoreboardManagerBB;

public final class BuildBattle extends JavaPlugin implements Listener {

    public static final int GAME_TIME = 10;
    private int countdown = -1;
    private int gameTime = -1; // seconds

    private int voteCountdown = -1;
    private int voteIndex = 1;
    private int votePhase = 0; // 0 = waiting, 1 = showing plot
    
    private String theme = "";

    private final HashSet<UUID> nightVisionPlayers = new HashSet<>();

    private GameProtectionManager protectionManager;
    private ScoreboardManagerBB scoreboardManager;
    private PlotManager plotManager;

    private GameState gameState = GameState.LOBBY;

    private FileConfiguration themesConfig;

    @Override
    public void onEnable() {
        scoreboardManager = new ScoreboardManagerBB();
        plotManager = new PlotManager(this);
        protectionManager = new GameProtectionManager(this, plotManager);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(protectionManager, this);

        plotManager.startPlotBorders();

        loadThemes();


    }

    private void loadThemes() {
        saveResource("themes.yml", false); // copies from jar to plugins folder if missing
        themesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "themes.yml"));
    }

    public String getRandomTheme() {
        List<String> themes = themesConfig.getStringList("random-themes");

        if (themes.isEmpty()) {
            return "Rutgers";
        }

        return themes.get(new Random().nextInt(themes.size()));
    }

    private Location getSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, 0, -14, 0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if(gameState != GameState.LOBBY) {
            // check if player is apart of the current game
            int plotID = plotManager.getPlayerPlot(p);
            // if 0 then they arent in the game
            if (plotID == 0) {
                p.teleport(getSpawn());
                p.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            p.teleport(getSpawn());
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getInventory().setItemInOffHand(null);
        }

//        if (!p.isOp()) {
//        p.setGameMode(GameMode.ADVENTURE);
//        }

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
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        if (gameState != GameState.BUILDING) {
            e.setCancelled(true);
            p.sendMessage("§cBuilding time is over.");
            return;
        }

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

        if (gameState != GameState.BUILDING) {
            e.setCancelled(true);
            p.sendMessage("§cBuilding time is over.");
            return;
        }

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

        if (gameState != GameState.BUILDING) {
            e.setCancelled(true);
            p.sendMessage("§cBuilding time is over.");
            return;
        }

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

            forceResetGame();

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
        else if (command.getName().equalsIgnoreCase("theme")) {

            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            if (gameState != GameState.LOBBY) {
                p.sendMessage("§cYou cannot run this right now!");
                return true;
            }

            if (args.length == 0) {
                p.sendMessage("§cUsage: /theme set <theme> | /theme random | /theme reset");
                return true;
            }

            switch (args[0].toLowerCase()) {

                case "set":
                    if (args.length < 2) {
                        p.sendMessage("§cUsage: /theme set <theme>");
                        return true;
                    }

                    this.theme = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    Bukkit.broadcastMessage("§aTheme set to §e" + this.theme);
                    break;

                case "random":
                    this.theme = getRandomTheme();
                    Bukkit.broadcastMessage("§aTheme randomly set to §e" + this.theme);
                    break;

                case "reset":
                    this.theme = "";
                    Bukkit.broadcastMessage("§aTheme reset.");
                    break;

                default:
                    p.sendMessage("§cUsage: /theme set <theme> | /theme random | /theme reset");
                    return true;
            }

            scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
            return true;
        }
        else if  (command.getName().equalsIgnoreCase("resetplot")) {
            if (!p.isOp()) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length != 1) {
                p.sendMessage("§cUsage: /resetplot <id>");
                return true;
            }

            int plotID;

            try {
                plotID = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage("§cInvalid number.");
                return true;
            }

            if (plotID <= 0 || plotID > 36) {
                p.sendMessage("§cInvalid size range.");
                return true;
            }

            plotManager.resetPlot(plotID);

            Bukkit.broadcastMessage("§aReset plot " + plotID + ".");

            return true;
        }

        return false;
    }

    private void startGame() {
        if (gameState != GameState.LOBBY) return;
        gameState = GameState.STARTING;

        if (theme.isEmpty()) {
            theme = getRandomTheme();
            Bukkit.broadcastMessage("§aTheme: §e" + theme);
        }

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
                        scoreboardManager.update(p, gameState, countdown, gameTime, theme, plotManager.getTeamSize());
                    }

//                    scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());
                    return;
                }
                gameState = GameState.BUILDING;
                countdown = -1;

                Bukkit.broadcastMessage("§aGame started!");

                gameTime = GAME_TIME; // 300 seconds

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

                // TIME UP → voting
                startVoting();
//                endGame();

                cancel();
            }

        }.runTaskTimer(this, 0L, 20L); // every 1 second
    }

    private void startVoting() {

        gameState = GameState.VOTING;
        plotManager.clearAllInventories();
        Bukkit.broadcastMessage("§cStop building! Its time to vote!");

        voteIndex = 1;
        votePhase = 0;
        voteCountdown = 3; // initial delay before first plot

        new BukkitRunnable() {

            @Override
            public void run() {

                if (gameState != GameState.VOTING) {
                    cancel();
                    return;
                }

                voteCountdown--;

                if (votePhase == 0) {

                    if (voteCountdown <= 0) {
                        votePhase = 1;
                        voteIndex = 1;
                        voteCountdown = 15;

                        showPlot(voteIndex);
                    }

                } else {

                    if (voteCountdown <= 0) {

                        voteIndex++;

                        if (voteIndex > plotManager.getActivePlotCount()) {
                            endGame();
                            cancel();
                            return;
                        }

                        voteCountdown = 15;
                        showPlot(voteIndex);
                    }
                }

                scoreboardManager.updateAll(
                        gameState,
                        voteCountdown,
                        -1,
                        theme,
                        plotManager.getTeamSize()
                );

//                if (gameState != GameState.VOTING) {
//                    cancel();
//                    return;
//                }
//
//                // INITIAL WAIT BEFORE FIRST PLOT
//                if (votePhase == 0) {
//
//                    voteCountdown--;
//
//                    if (voteCountdown <= 0) {
//                        votePhase = 1;
//                        voteCountdown = 15; // show time per plot
//
//                        plotManager.teleportAllToPlot(voteIndex);
//                    }
//
//                    scoreboardManager.updateAll(
//                            gameState,
//                            voteCountdown,
//                            -1, // gameTime not used in voting
//                            theme,
//                            plotManager.getTeamSize()
//                    );
//
//                    return;
//                }
//
//                // SHOW PLOT TIMER (15 → 0)
//                voteCountdown--;
//
//                if (voteCountdown <= 0) {
//
//                    voteIndex++;
//
//                    if (voteIndex > plotManager.getActivePlotCount()) {
//                        endGame();
//                        cancel();
//                        return;
//                    }
//
//                    int plotId = voteIndex;
//
//                    Bukkit.broadcastMessage("");
//                    Bukkit.broadcastMessage("§ePlot Voting §7- §a#" + plotId);
//
//                    List<Player> players = plotManager.getPlayersInPlot(plotId);
//
//                    if (!players.isEmpty()) {
//                        StringBuilder names = new StringBuilder();
//
//                        for (Player p : players) {
//                            names.append(p.getName()).append(" ");
//                        }
//
//                        Bukkit.broadcastMessage("§7Builders: §f" + names);
//                    }
//
//                    plotManager.teleportAllToPlot(plotId);
//
//                    voteCountdown = 15;
//                }
//
//                scoreboardManager.updateAll(
//                        gameState,
//                        voteCountdown,
//                        -1,
//                        theme,
//                        plotManager.getTeamSize()
//                );
            }

        }.runTaskTimer(this, 0L, 20L);
    }

    private void showPlot(int plotId) {

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§ePlot Voting §7- §a#" + plotId);

        List<Player> players = plotManager.getPlayersInPlot(plotId);

        if (players != null && !players.isEmpty()) {

            StringBuilder names = new StringBuilder();

            for (Player p : players) {
                names.append(p.getName()).append(" ");
            }

            Bukkit.broadcastMessage("§7Builders: §f" + names);
        }

        plotManager.teleportAllToPlot(plotId);
    }

    private void endGame() {
        gameState = GameState.ENDED;

        Bukkit.broadcastMessage("§cGame ended!");

        scoreboardManager.updateAll(
                gameState,
                countdown,
                gameTime,
                theme,
                plotManager.getTeamSize()
        );

        new BukkitRunnable() {
            @Override
            public void run() {
                forceResetGame();
            }
        }.runTaskLater(this, 20L * 5); // 5 seconds
    }

    private void forceResetGame() {
        gameState = GameState.LOBBY;
        countdown = -1;
        gameTime = -1;
        theme = "";

        scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());

        plotManager.clearPlots();
        plotManager.resetAllPlots();

        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.setGameMode(GameMode.ADVENTURE);
            pl.getInventory().clear();
            pl.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private void setAllGameMode(GameMode gameMode) {
        for (Player p : plotManager.getActivePlayers()) {
            p.setGameMode(gameMode);
        }
    }

    public GameState getGameState() {
        return gameState;
    }
}
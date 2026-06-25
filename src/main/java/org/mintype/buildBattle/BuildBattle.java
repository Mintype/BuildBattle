package org.mintype.buildBattle;

import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.GameMode;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.scoreboard.*;
import org.bukkit.ChatColor;

import java.util.*;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BuildBattle extends JavaPlugin implements Listener {

    private static final int PLOT_SIZE = 32;
    private static final int GAP = 10;
    private static final int GRID_SIZE = 6;

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 33;

    private int countdown = -1;
    private int gameTime = -1; // seconds

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final HashSet<UUID> nightVisionPlayers = new HashSet<>();
    private final Map<Player, Integer> playerPlot = new HashMap<>();
    private final Map<Integer, List<Player>> plotPlayers = new HashMap<>();

    private enum GameState {
        LOBBY,
        STARTING,
        BUILDING,
        VOTING,
        ENDED
    }

    private GameState gameState = GameState.LOBBY;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        startPlotBorders();

    }

    private Location getSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, 0, -14, 0);
    }

    private void clearPlots() {
        playerPlot.clear();
        plotPlayers.clear();

        for (int i = 1; i <= 36; i++) {
            plotPlayers.put(i, new ArrayList<>());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        p.teleport(getSpawn());
        if (!p.isOp()) {
            p.setGameMode(GameMode.ADVENTURE);
        }

        createScoreboard(e.getPlayer());
        updateScoreboards();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        updateScoreboards();
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

        int plotId = getPlotId(e.getBlock().getLocation());

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

        int plotId = getPlotId(e.getBlock().getLocation());

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
            int plotId = getPlotId(e.getClickedBlock().getLocation());

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

    private void startPlotBorders() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorlds().get(0);

                for (int plotX = 0; plotX < GRID_SIZE; plotX++) {
                    for (int plotZ = 0; plotZ < GRID_SIZE; plotZ++) {

                        int startX = plotX * (PLOT_SIZE + GAP);
                        int startZ = plotZ * (PLOT_SIZE + GAP);

                        int endX = startX + PLOT_SIZE;
                        int endZ = startZ + PLOT_SIZE;

                        drawPlot(world, startX, startZ, endX, endZ);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L); // every 0.5 seconds
    }

    private void drawPlot(World world, int minX, int minZ, int maxX, int maxZ) {

        // Bottom + Top X edges
        for (double x = minX; x <= maxX; x++) {
            spawn(world, x, MIN_Y, minZ);
            spawn(world, x, MIN_Y, maxZ);

            spawn(world, x, MAX_Y, minZ);
            spawn(world, x, MAX_Y, maxZ);
        }

        // Bottom + Top Z edges
        for (double z = minZ; z <= maxZ; z++) {
            spawn(world, minX, MIN_Y, z);
            spawn(world, maxX, MIN_Y, z);

            spawn(world, minX, MAX_Y, z);
            spawn(world, maxX, MAX_Y, z);
        }

        // Vertical edges
        for (double y = MIN_Y; y <= MAX_Y; y++) {
            spawn(world, minX, y, minZ);
            spawn(world, minX, y, maxZ);
            spawn(world, maxX, y, minZ);
            spawn(world, maxX, y, maxZ);
        }
    }

    private void spawn(World world, double x, double y, double z) {
        world.spawnParticle(
                Particle.END_ROD,
                x + 0.5,
                y + 0.5,
                z + 0.5,
                1,
                0,
                0,
                0,
                0
        );
    }

    private boolean isInPlot(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Y restriction (inclusive 0–33)
        if (y < 0 || y > 33) return false;

        if (x < 0 || z < 0) return false;

        int step = PLOT_SIZE + GAP;

        int gridX = x / step;
        int gridZ = z / step;

        int startX = gridX * step;
        int startZ = gridZ * step;

        int endX = startX + PLOT_SIZE;
        int endZ = startZ + PLOT_SIZE;

        return x >= startX && x < endX &&
                z >= startZ && z < endZ;
    }

    private int getPlotId(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Y restriction (0–33 inclusive)
        if (y < 0 || y > 33) return 0;

        if (x < 0 || z < 0) return 0;

        int step = PLOT_SIZE + GAP;

        int gridX = x / step;
        int gridZ = z / step;

        // check if inside actual plot area (not gap)
        int startX = gridX * step;
        int startZ = gridZ * step;

        int endX = startX + PLOT_SIZE;
        int endZ = startZ + PLOT_SIZE;

        boolean inside =
                x >= startX && x < endX &&
                        z >= startZ && z < endZ;

        if (!inside) return 0;

        // convert 2D grid → 1–36 index
        return gridZ * 6 + gridX + 1;
    }

    private void createScoreboard(Player p) {

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "bb",
                "dummy",
                ChatColor.GREEN + "" + ChatColor.BOLD + "Build Battle"
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // STATIC LINES (never change)
        obj.getScore("§7 ").setScore(6);
        obj.getScore("§aPlayers:").setScore(5);
        obj.getScore("§bMode: §fSolo").setScore(2);
        obj.getScore("§eevents.rumc.club").setScore(1);

        // DYNAMIC LINES (via teams)
        Team playersTeam = board.registerNewTeam("players");
        playersTeam.addEntry("§p");
        obj.getScore("§p").setScore(4);

        Team statusTeam = board.registerNewTeam("status");
        statusTeam.addEntry("§s");
        obj.getScore("§s").setScore(3);

        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    private void updateScoreboard(Player p) {

        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) return;

        Team playersTeam = board.getTeam("players");
        Team statusTeam = board.getTeam("status");

        if (playersTeam != null) {
            int online = Bukkit.getOnlinePlayers().size();
            playersTeam.setPrefix("§f" + online + "/" + Bukkit.getMaxPlayers());
        }

        if (statusTeam != null) {

            switch (gameState) {

                case LOBBY -> statusTeam.setPrefix("§eWaiting for host");

                case STARTING -> statusTeam.setPrefix("§eStarting in §6" + countdown);

                case BUILDING -> {
                    int min = gameTime / 60;
                    int sec = gameTime % 60;
                    statusTeam.setPrefix(String.format("§eTime left: §a%d:%02d", min, sec));
                }

                default -> statusTeam.setPrefix("§cGame ended");
            }
        }
    }

    private void updateScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateScoreboard(p);
        }
    }

    private void giveScoreboard(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "bb",
                "dummy",
                ChatColor.GREEN + "" + ChatColor.BOLD + "Build Battle"
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int online = Bukkit.getOnlinePlayers().size();

        obj.getScore("§7 ").setScore(6);
        obj.getScore("§aPlayers: §f" + online + "/" + Bukkit.getMaxPlayers()).setScore(5);

        String status;

        switch (gameState) {

            case LOBBY -> status = "§eWaiting for host";

            case STARTING -> status = "§eStarting in §6" + countdown;

            case BUILDING -> {
                int min = gameTime / 60;
                int sec = gameTime % 60;
                status = String.format("§eTime left: §a%d:%02d", min, sec);
            }

            default -> status = "§cGame ended";
        }

        obj.getScore(status).setScore(4);

        obj.getScore("§bMode: §fSolo").setScore(3);
        obj.getScore("§7  ").setScore(2);
        obj.getScore("§eevents.rumc.club").setScore(1);

        p.setScoreboard(board);
    }

    private void startGame() {
        if (gameState != GameState.LOBBY) return;
        gameState = GameState.STARTING;

        countdown = 10;

        clearPlots();

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
                        assignPlots();
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

                    updateScoreboards();
                    return;
                }
                gameState = GameState.BUILDING;
                countdown = -1;

//                assignPlots();

                setAllGameMode(GameMode.CREATIVE);

                Bukkit.broadcastMessage("§aGame started!");

                gameTime = 5 * 60; // 300 seconds

                startGameTimer();
                updateScoreboards();
                cancel();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void sendTitle(Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
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
                    updateScoreboards();
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

        updateScoreboards();
    }

    private void setAllGameMode(GameMode gameMode) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(gameMode);
        }
    }

    private void assignPlots() {

        int plotId = 1;

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (plotId > 36) {
                p.sendMessage("§cNo plot available.");
                continue;
            }

            playerPlot.put(p, plotId);
            plotPlayers.get(plotId).add(p);

            teleportToPlot(p, plotId);

            p.sendMessage("§aYou are in plot #" + plotId);

            plotId++;
        }
    }

    private void teleportToPlot(Player p, int plotId) {

        int step = PLOT_SIZE + GAP;

        int gridX = (plotId - 1) % 6;
        int gridZ = (plotId - 1) / 6;

        int startX = gridX * step;
        int startZ = gridZ * step;

        Location loc = new Location(
                Bukkit.getWorlds().get(0),
                startX + (PLOT_SIZE / 2.0),
                1,
                startZ + (PLOT_SIZE / 2.0)
        );

        p.teleport(loc);
    }

    private int getPlayerPlot(Player p) {
        return playerPlot.getOrDefault(p, 0);
    }
}
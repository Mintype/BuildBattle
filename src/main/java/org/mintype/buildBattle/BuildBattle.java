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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import org.mintype.buildBattle.voting.Rating;

public final class BuildBattle extends JavaPlugin implements Listener {

    public static final int GAME_TIME = 3 * 60;
    private int countdown = -1;
    private int gameTime = -1; // seconds

    private int voteCountdown = -1;
    private int voteIndex = 1;
    private int votePhase = 0; // 0 = waiting, 1 = showing plot

    Map<Integer, Map<UUID, Rating>> playerVotes = new HashMap<>();
    
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

        if (gameState == GameState.VOTING) {
            e.setCancelled(true);
            return;
        }
        if (gameState != GameState.BUILDING) {
            e.setCancelled(true);
            p.sendMessage("§cBuilding time is over.");
            return;
        }

        int plotId = plotManager.getPlotId(e.getBlock().getLocation());

//        Bukkit.getLogger().info("[BREAK] player=" + p.getName()
//                + " plotId=" + plotId);

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

//        p.sendMessage("§aBreaking in plot #" + plotId);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        if (gameState == GameState.VOTING) {
            e.setCancelled(true);
            return;
        }
        if (gameState != GameState.BUILDING) {
            e.setCancelled(true);
            p.sendMessage("§cBuilding time is over.");
            return;
        }

        int plotId = plotManager.getPlotId(e.getBlock().getLocation());

//        Bukkit.getLogger().info("[PLACE] player=" + p.getName()
//                + " plotId=" + plotId);

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

//        p.sendMessage("§aBuilding in plot #" + plotId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (p.isOp()) return;

        if (gameState == GameState.VOTING) {
            e.setCancelled(true);
            return;
        }
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

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }
            if (!p.hasPermission("buildbattle.addtime")) {
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

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }
            if (!p.hasPermission("buildbattle.reset")) {
                p.sendMessage("§cNo permission.");
                return true;
            }

            forceResetGame();

            Bukkit.broadcastMessage("§cGame has been reset!");

            return true;
        }
        else if (command.getName().equalsIgnoreCase("start")) {

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }
            if (!p.hasPermission("buildbattle.start")) {
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

            if (!p.hasPermission("buildbattle.floor")) {
                p.sendMessage("§cNo permission.");
                return true;
            }

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

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }

            if (!p.hasPermission("buildbattle.teamsize")) {
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

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }

            if (!p.hasPermission("buildbattle.theme")) {
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

//            if (!p.isOp()) {
//                p.sendMessage("§cNo permission.");
//                return true;
//            }

            if (!p.hasPermission("buildbattle.resetplot")) {
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
        plotManager.resetAllPlots();

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
                        for (Player p : plotManager.getActivePlayers()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        }
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        // tick sound
                        if (countdown <= 3 && countdown > 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                        }
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

                    if (gameTime == 120) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("", "§e2 Minutes Remaining!", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }

                    if (gameTime == 60) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("", "§c1 Minute Remaining!", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
                        }
                    }

                    if (gameTime <= 5 && gameTime > 0) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("", "§c" + gameTime, 0, 20, 0);
                            p.playSound(
                                    p.getLocation(),
                                    Sound.BLOCK_NOTE_BLOCK_PLING,
                                    1f,
                                    1f
                            );
                        }
                    }

                    scoreboardManager.updateAll(
                            gameState,
                            countdown,
                            gameTime,
                            theme,
                            plotManager.getTeamSize()
                    );

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
            }

        }.runTaskTimer(this, 0L, 20L);
    }

    private void showPlot(int plotId) {

        plotManager.clearAllInventories();

        for (Player p : plotManager.getActivePlayers()) {
            giveVoteItems(p);
        }

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

        plotManager.clearAllInventories();

        // 1. Collect scores
        Map<Integer, Integer> scores = new HashMap<>();

        for (int plotId = 1; plotId <= 36; plotId++) {
            scores.put(plotId, getScore(plotId));
        }

        // 2. Sort by score
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // 3. Get winner plot ID
        int winningPlotId = sorted.getFirst().getKey();

        // 4. Teleport everyone to winner plot
        plotManager.teleportAllToPlot(winningPlotId);

        // Winner sounds
        for (Player p : plotManager.getActivePlayers()) {
            p.playSound(
                    p.getLocation(),
                    Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    1.0f,
                    1.0f
            );

            p.playSound(
                    p.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.0f
            );
        }

        List<Player> winners = plotManager.getPlayersInPlot(winningPlotId);

        for (Player p : winners) {
            p.sendTitle(
                    "",
                    "§6§lWINNER!",
                    10,
                    60,
                    20
            );
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§lTOP 3 PLOTS");
        Bukkit.broadcastMessage("");

        // 3. Top 3 broadcast
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {

            Map.Entry<Integer, Integer> entry = sorted.get(i);
            int plotId = entry.getKey();

            Bukkit.broadcastMessage(
                    "§e#" + (i + 1) +
                            " §7Plot §f" + plotId +
                            " §7- §a" + entry.getValue() + " pts"
            );

            List<Player> players = plotManager.getPlayersInPlot(plotId);

            StringBuilder names = new StringBuilder();

            for (Player p : players) {
                names.append(p.getName()).append(", ");
            }

            if (names.length() > 2) {
                names.setLength(names.length() - 2);
            }

            Bukkit.broadcastMessage("§7Builders: §f" + names);
            Bukkit.broadcastMessage("");
        }

        // 4. Individual results
        for (Player p : Bukkit.getOnlinePlayers()) {

            int plotId = plotManager.getPlayerPlot(p);
            if (plotId == 0) continue;

            int score = scores.getOrDefault(plotId, 0);

            int rank = 0;

            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).getKey() == plotId) {
                    rank = i + 1;
                    break;
                }
            }

            List<Player> players = plotManager.getPlayersInPlot(plotId);

            StringBuilder names = new StringBuilder();

            for (Player pl : players) {
                names.append(pl.getName()).append(", ");
            }

            if (names.length() > 2) {
                names.setLength(names.length() - 2);
            }

            if (rank > 0 && rank <= 3) {
                p.sendMessage("§aYour plot (#" + plotId + ") placed §6#" + rank +
                        " §awith §e" + score + " points!");
            } else {
                p.sendMessage("§aYour plot (#" + plotId + ")");
                p.sendMessage("§7Score: §e" + score);
            }

            p.sendMessage("§7Builders: §f" + names);
        }

        // 5. scoreboard update
        scoreboardManager.updateAll(
                gameState,
                countdown,
                gameTime,
                theme,
                plotManager.getTeamSize()
        );

        // 6. reset after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                forceResetGame();
            }
        }.runTaskLater(this, 20L * 10);
    }

    private void forceResetGame() {
        gameState = GameState.LOBBY;
        countdown = -1;
        gameTime = -1;
        theme = "";

        voteCountdown = -1;
        voteIndex = 1;
        votePhase = 0;

        resetVotes();

        scoreboardManager.updateAll(gameState, countdown, gameTime, theme, plotManager.getTeamSize());

        plotManager.clearPlots();
        plotManager.resetAllPlots();

        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.setGameMode(GameMode.ADVENTURE);
            pl.getInventory().clear();
            pl.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private Map<Integer, Integer> getAllScores() {

        Map<Integer, Integer> scores = new HashMap<>();

        for (int plotId = 1; plotId <= 36; plotId++) {
            scores.put(plotId, getScore(plotId));
        }

        return scores;
    }

    private void setAllGameMode(GameMode gameMode) {
        for (Player p : plotManager.getActivePlayers()) {
            p.setGameMode(gameMode);
        }
    }

    public GameState getGameState() {
        return gameState;
    }

    public void giveVoteItems(Player p) {

        p.getInventory().clear();

        int slot = 0;

        Rating[] ratings = Rating.values();

        for (int i = ratings.length - 1; i >= 0; i--) {

            Rating rating = ratings[i];

            ItemStack item = new ItemStack(rating.getMaterial());
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(rating.getDisplay());
            item.setItemMeta(meta);

            p.getInventory().setItem(slot, item);

            slot++;
        }
    }

    @EventHandler
    public void onVoteClick(PlayerInteractEvent e) {

        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item == null) return;

        Rating rating = getRating(item);
        if (rating == null) return;

        int plotId = plotManager.getPlayerPlot(p);
        if (plotId == 0) return;

        addVote(p, voteIndex, rating);
    }

    private Rating getRating(ItemStack item) {

        if (item == null || !item.hasItemMeta()) return null;

        for (Rating r : Rating.values()) {
            if (item.getType() == r.getMaterial()) {
                return r;
            }
        }

        return null;
    }

    public void addVote(Player p, int plotId, Rating rating) {

        int playerPlot = plotManager.getPlayerPlot(p);

        if (playerPlot == plotId) {
            p.sendMessage("§cYou cannot vote for your own plot!");
            return;
        }

        playerVotes.putIfAbsent(plotId, new HashMap<>());
        Map<UUID, Rating> votes = playerVotes.get(plotId);

        Rating oldVote = votes.get(p.getUniqueId());

        // same vote again
        if (oldVote == rating) {
            p.sendMessage("§eYou already voted §f" + rating.getDisplay());
            return;
        }

        // changing vote
        if (oldVote != null) {
            p.sendMessage("§eYou changed your vote to §f" + rating.getDisplay());
        } else {
            p.sendMessage("§aYou voted §f" + rating.getDisplay());
        }

        votes.put(p.getUniqueId(), rating);
    }

    public void resetVotes() {
        playerVotes.clear();
    }

    public int getScore(int plotId) {

        Map<UUID, Rating> votes = playerVotes.get(plotId);

        if (votes == null) return 0;

        int score = 0;

        for (Rating rating : votes.values()) {

            int weight = switch (rating) {
                case LEGENDARY -> 7;
                case EPIC -> 6;
                case GREAT -> 5;
                case GOOD -> 4;
                case MID -> 3;
                case BAD -> 2;
                case TERRIBLE -> 1;
            };

            score += weight;
        }

        return score;
    }
}
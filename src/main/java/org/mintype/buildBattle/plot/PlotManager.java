package org.mintype.buildBattle.plot;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlotManager {

    private static final int PLOT_SIZE = 32;
    private static final int GAP = 10;
    private static final int GRID_SIZE = 6;

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 33;

    private static final Material DEFAULT_FLOOR = Material.WHITE_TERRACOTTA;

    private final Map<Player, Integer> playerPlot = new HashMap<>();
    private final Map<Integer, List<Player>> plotPlayers = new HashMap<>();

    private int teamSize = 1;

    private final JavaPlugin plugin;

    public PlotManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void assignPlots() {

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int plotId = 1;

        java.util.Collections.shuffle(players);

        for (int i = 0; i < players.size(); i += teamSize) {

            if (plotId > 36) {
                players.get(i).sendMessage("§cNo plot available.");
                continue;
            }

            int end = Math.min(i + teamSize, players.size());
            List<Player> group = players.subList(i, end);

            plotPlayers.putIfAbsent(plotId, new ArrayList<>());

            for (Player p : group) {
                playerPlot.put(p, plotId);
                plotPlayers.get(plotId).add(p);

                teleportToPlot(p, plotId);
                p.sendMessage("§aYou are in plot #" + plotId);
            }

            plotId++;
        }
    }

    public void clearPlots() {
        playerPlot.clear();
        plotPlayers.clear();

        for (int i = 1; i <= 36; i++) {
            plotPlayers.put(i, new ArrayList<>());
        }
    }

    private int[] getPlotBounds(int plotId) {

        int step = PLOT_SIZE + GAP;

        int gridX = (plotId - 1) % GRID_SIZE;
        int gridZ = (plotId - 1) / GRID_SIZE;

        int startX = gridX * step;
        int startZ = gridZ * step;

        int endX = startX + PLOT_SIZE;
        int endZ = startZ + PLOT_SIZE;

        return new int[]{startX, endX, startZ, endZ};
    }

    public void resetPlot(int plotId) {

        World w = Bukkit.getWorlds().get(0);

        int[] b = getPlotBounds(plotId);
        int startX = b[0];
        int endX = b[1];
        int startZ = b[2];
        int endZ = b[3];

        // 1. clear blocks + reset floor
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {

                    if (y == 0) {
                        w.getBlockAt(x, y, z).setType(DEFAULT_FLOOR);
                    } else {
                        w.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // 2. remove entities inside plot
        for (Entity entity : w.getEntities()) {
            Location loc = entity.getLocation();

            if (loc.getBlockX() >= startX && loc.getBlockX() < endX &&
                    loc.getBlockZ() >= startZ && loc.getBlockZ() < endZ) {

                if (entity instanceof Player) continue;

                entity.remove();
            }
        }

//        // 3. clear player tracking for this plot
//        List<Player> players = plotPlayers.getOrDefault(plotId, new ArrayList<>());
//
//        for (Player p : players) {
//            playerPlot.remove(p);
//        }
//
//        plotPlayers.put(plotId, new ArrayList<>());
    }

    public void resetAllPlots() {
        for (int i = 1; i <= 36; i++) {
            resetPlot(i);
        }
    }

    public void startPlotBorders() {
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
        }.runTaskTimer(plugin, 0L, 10L); // every 0.5 seconds
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

    public void teleportToPlot(Player p, int plotId) {

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

    public void setPlotFloor(int plotId, Material mat) {

        World w = Bukkit.getWorlds().get(0);

        int plotSize = 32;
        int gap = 10;
        int gridSize = 6;

        int step = plotSize + gap;

        int gridX = (plotId - 1) % gridSize;
        int gridZ = (plotId - 1) / gridSize;

        int startX = gridX * step;
        int startZ = gridZ * step;

        int y = 0; // floor level (change if needed)

        for (int x = 0; x < plotSize; x++) {
            for (int z = 0; z < plotSize; z++) {
                w.getBlockAt(startX + x, y, startZ + z).setType(mat);
            }
        }
    }

    public boolean isInPlot(Location loc) {
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

    public int getPlotId(Location loc) {
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

    public int getPlayerPlot(Player p) {
        return playerPlot.getOrDefault(p, 0);
    }

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }
}
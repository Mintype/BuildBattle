package org.mintype.buildBattle;

import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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

public final class BuildBattle extends JavaPlugin implements Listener {

    private static final int PLOT_SIZE = 32;
    private static final int GAP = 10;
    private static final int GRID_SIZE = 6;

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 33;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        startPlotBorders();
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

//    @EventHandler
//    public void onInteract(PlayerInteractEvent e) {
//        Player p = e.getPlayer();
//
//        if (!p.isOp()) {
//            e.setCancelled(true);
//        }
//    }

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
}
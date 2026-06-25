package org.mintype.buildBattle.protection;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.mintype.buildBattle.BuildBattle;
import org.mintype.buildBattle.game.GameState;
import org.mintype.buildBattle.plot.PlotManager;

public class GameProtectionManager implements Listener {

    private BuildBattle buildBattle;
    private PlotManager plotManager;

    public GameProtectionManager(BuildBattle buildBattle, PlotManager plotManager) {
        this.buildBattle = buildBattle;
        this.plotManager = plotManager;
    }

    // toggle if needed later
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private boolean off() {
        return !enabled;
    }

    // ALL EXPLOSIONS
    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (off()) return;

        e.setCancelled(true);
        e.blockList().clear();
    }

    // TNT ignition / priming safety
    @EventHandler
    public void onPrime(ExplosionPrimeEvent e) {
        if (off()) return;

        e.setCancelled(true);
    }

    // FIRE / LAVA / SPREAD
    @EventHandler
    public void onIgnite(BlockIgniteEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    // PORTALS
    @EventHandler
    public void onPortalCreate(PortalCreateEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onFrameInsert(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getClickedBlock() == null || e.getItem() == null) return;

        if (e.getClickedBlock().getType() == Material.END_PORTAL_FRAME &&
                e.getItem().getType() == Material.ENDER_EYE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onUseEye(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getClickedBlock() == null || e.getItem() == null) return;

        if (e.getClickedBlock().getType() == Material.END_PORTAL_FRAME &&
                e.getItem().getType() == Material.ENDER_EYE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPearl(PlayerTeleportEvent e) {
        if (off()) return;

        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            e.setCancelled(true);
        }
    }

//    // ENDER CRYSTALS
//    @EventHandler
//    public void onCrystalExplode(EntityExplodeEvent e) {
//        if (off()) return;
//
//        if (e.getEntity() instanceof EnderCrystal) {
//            e.setCancelled(true);
//            e.blockList().clear();
//        }
//    }

    @EventHandler
    public void onCrystalHit(EntityDamageByEntityEvent e) {
        if (off()) return;

        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;

        e.setCancelled(true);

        crystal.remove(); // disappears instantly
    }

//    // CREEPERS (extra safety even though explode is cancelled globally)
//    @EventHandler
//    public void onCreeper(EntityExplodeEvent e) {
//        if (off()) return;
//
//        if (e.getEntity() instanceof Creeper) {
//            e.setCancelled(true);
//            e.blockList().clear();
//        }
//    }

    @EventHandler
    public void onAnyExplode(EntityExplodeEvent e) {
        if (off()) return;

        e.setCancelled(true);
        e.blockList().clear();
    }

    // optional: prevent lava bucket fire behavior indirectly
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getItem() == null) return;

        Material type = e.getItem().getType();

        if (type == Material.LAVA_BUCKET || type == Material.FLINT_AND_STEEL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent e) {
        if (off()) return;
        e.setNewCurrent(0);
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent e) {
        if (off()) return;

        Material type = e.getBlock().getType();

        if (type == Material.REDSTONE_WIRE ||
                type == Material.REPEATER ||
                type == Material.COMPARATOR ||
                type == Material.PISTON ||
                type == Material.STICKY_PISTON ||
                type == Material.DISPENSER ||
                type == Material.DROPPER ||
                type == Material.OBSERVER) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent e) {
        if (off()) return;

        if (e.getVehicle() instanceof Minecart) {
            e.getVehicle().remove();
        }
    }

    @EventHandler
    public void onPlaceMinecart(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getItem() == null) return;

        Material t = e.getItem().getType();

        if (t == Material.MINECART ||
                t == Material.HOPPER_MINECART ||
                t == Material.TNT_MINECART ||
                t == Material.FURNACE_MINECART) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstoneInputs(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getClickedBlock() == null) return;

        Material t = e.getClickedBlock().getType();

        if (t == Material.LEVER ||
                t.name().contains("BUTTON") ||
                t.name().contains("PRESSURE_PLATE")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobHit(EntityDamageByEntityEvent e) {
        if (off()) return;

        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;

        if (!(e.getDamager() instanceof Player p)) return;

        if (buildBattle.getGameState() != GameState.BUILDING) return;

        int entityPlot = plotManager.getPlotId(entity.getLocation());
        int playerPlot = plotManager.getPlayerPlot(p);

        if (entityPlot == playerPlot) {
            entity.setHealth(0.0);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (off()) return;

        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;

        if (!(e.getDamager() instanceof Player p)) return;

        if (buildBattle.getGameState() != GameState.BUILDING) return;

        int entityPlot = plotManager.getPlotId(entity.getLocation());
        int playerPlot = plotManager.getPlayerPlot(p);

        // invalid safety
        if (entityPlot == -1 || playerPlot == -1) {
            e.setCancelled(true);
            return;
        }

        // not same plot → block ALL damage
        if (entityPlot != playerPlot) {
            e.setCancelled(true);
            return;
        }

        // ✔ same plot → allow your logic or instant kill
        entity.setHealth(0.0);
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (off()) return;

        LivingEntity entity = e.getEntity();

        if (entity instanceof Wither) {
            e.setCancelled(true);
            return;
        }

        entity.setAI(false);
        entity.setSilent(true);
        entity.setGravity(false);
    }

    @EventHandler
    public void onBlockBreakByEntity(EntityChangeBlockEvent e) {
        if (off()) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (off()) return;

        e.setDropItems(false);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (off()) return;

        e.getDrops().clear();
        e.setDroppedExp(0);
    }


    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (off()) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onWitherSkull(EntityDamageByEntityEvent e) {
        if (off()) return;

        if (e.getDamager() instanceof WitherSkull) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (off()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPotionThrow(PlayerInteractEvent e) {
        if (off()) return;

        if (e.getItem() == null) return;

        Material t = e.getItem().getType();

        if (t == Material.SPLASH_POTION ||
                t == Material.LINGERING_POTION ||
                t == Material.ENDER_PEARL ||
                t == Material.EXPERIENCE_BOTTLE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent e) {
        if (off()) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (off()) return;

        if (e.getEntity() instanceof Arrow ||
                e.getEntity() instanceof SpectralArrow ||
                e.getEntity() instanceof Snowball ||
                e.getEntity() instanceof Egg) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (off()) return;
        e.getEntity().remove();
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


}
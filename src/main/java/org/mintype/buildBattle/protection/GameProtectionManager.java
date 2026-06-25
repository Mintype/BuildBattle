package org.mintype.buildBattle.protection;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.PortalCreateEvent;

public class GameProtectionManager implements Listener {

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


}
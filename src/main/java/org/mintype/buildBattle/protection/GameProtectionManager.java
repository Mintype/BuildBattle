package org.mintype.buildBattle.protection;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
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
}
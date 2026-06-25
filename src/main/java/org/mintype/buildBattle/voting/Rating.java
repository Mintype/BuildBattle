package org.mintype.buildBattle.voting;

import org.bukkit.Material;

public enum Rating {

    LEGENDARY("§wLegendary", Material.LIGHT_BLUE_CONCRETE),
    EPIC("§3Epic", Material.CYAN_CONCRETE),
    GREAT("§2Great", Material.GREEN_CONCRETE),
    GOOD("§aGood", Material.LIME_CONCRETE),
    MID("§eMid", Material.YELLOW_CONCRETE),
    BAD("§vBad", Material.ORANGE_CONCRETE),
    TERRIBLE("§cTerrible", Material.RED_CONCRETE);

    private final String display;
    private final Material material;

    Rating(String display, Material material) {
        this.display = display;
        this.material = material;
    }

    public String getDisplay() {
        return display;
    }

    public Material getMaterial() {
        return material;
    }
}
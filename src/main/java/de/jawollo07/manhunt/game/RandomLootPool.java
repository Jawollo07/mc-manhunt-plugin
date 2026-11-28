package de.jawollo07.manhunt.game;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Utilities for the per-arena configurable random loot pool. */
public final class RandomLootPool {
    private static final List<Material> DEFAULTS = List.of(
            Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.BAKED_POTATO,
            Material.CARROT, Material.GOLDEN_CARROT, Material.APPLE, Material.GOLDEN_APPLE,
            Material.ARROW, Material.TIPPED_ARROW, Material.BOW, Material.CROSSBOW,
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT, Material.EMERALD,
            Material.LAPIS_LAZULI, Material.REDSTONE, Material.DIAMOND, Material.OBSIDIAN,
            Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE, Material.TNT, Material.STRING,
            Material.LEATHER, Material.FEATHER, Material.BONE, Material.GUNPOWDER,
            Material.OAK_PLANKS, Material.COBBLESTONE, Material.TORCH, Material.CRAFTING_TABLE,
            Material.IRON_PICKAXE, Material.IRON_AXE, Material.SHIELD, Material.COOKED_CHICKEN,
            Material.SADDLE, Material.NAME_TAG, Material.BOOK, Material.COMPASS
    );

    private RandomLootPool() { }

    public static List<Material> defaults() {
        return DEFAULTS;
    }

    public static List<Material> availableItems() {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(material -> !material.isAir())
                .sorted(Comparator.comparing(Material::name))
                .toList();
    }

    /** Empty setting means legacy defaults; "-" explicitly means an empty pool. */
    public static Set<Material> parse(String value) {
        if (value == null || value.isBlank()) return new LinkedHashSet<>(DEFAULTS);
        if (value.trim().equals("-")) return new LinkedHashSet<>();
        Set<Material> materials = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            Material material = Material.matchMaterial(token.trim().toUpperCase(Locale.ROOT));
            if (material != null && material.isItem() && !material.isAir()) materials.add(material);
        }
        return materials;
    }

    public static String encode(Set<Material> materials) {
        if (materials == null || materials.isEmpty()) return "-";
        return materials.stream().filter(Material::isItem).filter(material -> !material.isAir())
                .map(Material::name).sorted().reduce((left, right) -> left + "," + right).orElse("-");
    }
}

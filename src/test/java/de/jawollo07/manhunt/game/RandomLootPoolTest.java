package de.jawollo07.manhunt.game;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomLootPoolTest {
    @Test
    void emptySettingUsesTheLegacyDefaultPool() {
        assertEquals(new LinkedHashSet<>(RandomLootPool.defaults()), RandomLootPool.parse(""));
        assertTrue(RandomLootPool.parse("").contains(Material.BREAD));
    }

    @Test
    void explicitEmptyPoolIsDifferentFromUnsetPool() {
        assertTrue(RandomLootPool.parse("-").isEmpty());
    }

    @Test
    void encodesAndParsesSelectedMaterials() {
        Set<Material> selected = new LinkedHashSet<>(Set.of(Material.DIAMOND, Material.BREAD, Material.TNT));
        String encoded = RandomLootPool.encode(selected);
        assertEquals(Set.of(Material.DIAMOND, Material.BREAD, Material.TNT), RandomLootPool.parse(encoded));
    }

    @Test
    void ignoresUnknownAndNonItemMaterials() {
        assertEquals(Set.of(Material.BREAD),
                RandomLootPool.parse("BREAD,NOT_A_REAL_MATERIAL, AIR"));
    }
}

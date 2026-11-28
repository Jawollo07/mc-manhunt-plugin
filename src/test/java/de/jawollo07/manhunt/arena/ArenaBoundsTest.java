package de.jawollo07.manhunt.arena;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class ArenaBoundsTest {
    @Test
    void readsAndNormalizesTwoCornersAndIncludesTheirBoundaryBlocks() {
        Arena arena = new Arena("test", "Test", "template")
                .withModeSetting("general", "bounds_pos1_x", "200")
                .withModeSetting("general", "bounds_pos1_y", "64")
                .withModeSetting("general", "bounds_pos1_z", "100")
                .withModeSetting("general", "bounds_pos2_x", "100")
                .withModeSetting("general", "bounds_pos2_y", "21")
                .withModeSetting("general", "bounds_pos2_z", "784");

        ArenaBounds bounds = ArenaBounds.from(arena).orElseThrow();

        assertEquals(100.0, bounds.minX(), 0.001);
        assertEquals(200.0, bounds.maxX(), 0.001);
        assertEquals(21.0, bounds.minY(), 0.001);
        assertEquals(64.0, bounds.maxY(), 0.001);
        assertEquals(100.0, bounds.minZ(), 0.001);
        assertEquals(784.0, bounds.maxZ(), 0.001);
        assertTrue(bounds.contains(100, 21, 100));
        assertTrue(bounds.contains(200.9, 64.9, 784.9));
        assertFalse(bounds.contains(201.1, 40, 400));
        assertFalse(bounds.contains(150, 65.1, 400));
        assertFalse(bounds.contains(150, 40, 785.1));
    }

    @Test
    void treatsMissingOrPartialBoundsAsNotReadyRatherThanCrashingMenus() {
        Arena empty = new Arena("test", "Test", "template");
        assertTrue(ArenaBounds.from(empty).isEmpty());
        assertFalse(ArenaBounds.hasAny(empty));

        Arena partial = empty.withModeSetting("general", "bounds_pos1_x", "200");
        assertTrue(ArenaBounds.from(partial).isEmpty());
        assertTrue(ArenaBounds.hasAny(partial));
    }
}

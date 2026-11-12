package de.jawollo07.manhunt.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleManagerTest {
    @Test
    void assignsAndReplacesOneRolePerPlayer() {
        RoleManager manager = new RoleManager();
        UUID player = UUID.randomUUID();
        UUID team = UUID.randomUUID();

        RoleAssignment first = manager.assign(player, "assassin_target");
        RoleAssignment second = manager.assign(player, "assassin_hunter", team);

        assertEquals("assassin_target", first.roleId());
        assertEquals("assassin_hunter", manager.getRoleId(player).orElseThrow());
        assertEquals(team, manager.getAssignment(player).orElseThrow().teamId());
        assertEquals(1, manager.size());
        assertNotEquals(first.assignedAt(), null);
        assertNotEquals(second.assignedAt(), null);
    }

    @Test
    void removesAndClearsAssignments() {
        RoleManager manager = new RoleManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.assign(first, "hunter");
        manager.assign(second, "survivor");

        assertTrue(manager.remove(first));
        assertFalse(manager.remove(first));
        assertEquals(1, manager.size());

        manager.clear();
        assertTrue(manager.getAssignments().isEmpty());
    }

    @Test
    void rejectsBlankRoleIds() {
        RoleManager manager = new RoleManager();
        assertThrows(IllegalArgumentException.class,
                () -> manager.assign(UUID.randomUUID(), " "));
    }

    @Test
    void exposesReadOnlyAssignments() {
        RoleManager manager = new RoleManager();
        manager.assign(UUID.randomUUID(), "juggernaut");
        assertThrows(UnsupportedOperationException.class,
                () -> manager.getAssignments().clear());
    }
}

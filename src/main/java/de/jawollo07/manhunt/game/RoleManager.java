package de.jawollo07.manhunt.game;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-game role assignments. Role identifiers are owned by game-mode modules;
 * this class only guarantees one current assignment per player.
 */
public final class RoleManager {
    private final Map<UUID, RoleAssignment> assignments = new LinkedHashMap<>();

    public RoleAssignment assign(UUID playerId, String roleId) {
        return assign(playerId, roleId, null);
    }

    public RoleAssignment assign(UUID playerId, String roleId, UUID teamId) {
        RoleAssignment assignment = new RoleAssignment(
                Objects.requireNonNull(playerId, "playerId"),
                roleId,
                teamId,
                null
        );
        assignments.put(playerId, assignment);
        return assignment;
    }

    public Optional<RoleAssignment> getAssignment(UUID playerId) {
        return Optional.ofNullable(assignments.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public Optional<String> getRoleId(UUID playerId) {
        return getAssignment(playerId).map(RoleAssignment::roleId);
    }

    public Collection<RoleAssignment> getAssignments() {
        return Collections.unmodifiableCollection(assignments.values());
    }

    public boolean hasAssignment(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public boolean remove(UUID playerId) {
        return assignments.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    public void clear() {
        assignments.clear();
    }

    public int size() {
        return assignments.size();
    }
}

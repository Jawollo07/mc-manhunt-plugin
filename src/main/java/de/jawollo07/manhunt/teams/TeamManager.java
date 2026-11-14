package de.jawollo07.manhunt.teams;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TeamManager {
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerTeams = new LinkedHashMap<>();

    public Team createTeam(String name, String color, int maxTeamSize, boolean friendlyFire) {
        Team team = new Team(UUID.randomUUID(), name, color, maxTeamSize, friendlyFire);
        teams.put(team.getId(), team);
        return team;
    }

    public Optional<Team> getTeam(UUID teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    public Optional<Team> getTeamOf(UUID playerId) {
        UUID teamId = playerTeams.get(playerId);
        return teamId == null ? Optional.empty() : getTeam(teamId);
    }

    public Collection<Team> getTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public void assignPlayer(UUID playerId, UUID teamId) {
        Objects.requireNonNull(playerId, "playerId");
        Team target = requireTeam(teamId);
        UUID currentTeamId = playerTeams.get(playerId);
        if (teamId.equals(currentTeamId)) return;
        if (!target.canAcceptMembers()) {
            throw new IllegalStateException("Team is inactive or full");
        }

        if (currentTeamId != null) {
            Team current = teams.get(currentTeamId);
            if (current != null) current.removeMember(playerId);
        }

        if (!target.addMember(playerId)) {
            throw new IllegalStateException("Could not add player to team");
        }
        playerTeams.put(playerId, teamId);
    }

    public boolean removePlayer(UUID playerId) {
        UUID teamId = playerTeams.remove(playerId);
        if (teamId == null) return false;
        Team team = teams.get(teamId);
        return team != null && team.removeMember(playerId);
    }

    public void deleteTeam(UUID teamId) {
        Team team = requireTeam(teamId);
        for (UUID member : team.getMembers()) {
            playerTeams.remove(member);
        }
        team.deactivate();
        teams.remove(teamId);
    }

    public void clear() {
        teams.values().forEach(Team::deactivate);
        teams.clear();
        playerTeams.clear();
    }

    private Team requireTeam(UUID teamId) {
        Team team = teams.get(Objects.requireNonNull(teamId, "teamId"));
        if (team == null) throw new IllegalArgumentException("Unknown team: " + teamId);
        return team;
    }
}

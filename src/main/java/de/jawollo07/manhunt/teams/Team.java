package de.jawollo07.manhunt.teams;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Team {
    private final UUID id;
    private final String name;
    private final String color;
    private final int maxTeamSize;
    private final boolean friendlyFire;
    private final Set<UUID> members = new LinkedHashSet<>();

    private int score;
    private int kills;
    private int deaths;
    private boolean active = true;

    public Team(UUID id, String name, String color, int maxTeamSize, boolean friendlyFire) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Team name must not be blank");
        if (color == null || color.isBlank()) throw new IllegalArgumentException("Team color must not be blank");
        if (maxTeamSize < 1) throw new IllegalArgumentException("Team size must be positive");
        this.name = name;
        this.color = color;
        this.maxTeamSize = maxTeamSize;
        this.friendlyFire = friendlyFire;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public int getMaxTeamSize() { return maxTeamSize; }
    public boolean isFriendlyFireEnabled() { return friendlyFire; }
    public int getScore() { return score; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public boolean isActive() { return active; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public int getMemberCount() { return members.size(); }
    public boolean hasMember(UUID playerId) { return members.contains(playerId); }
    public boolean canAcceptMembers() { return active && members.size() < maxTeamSize; }

    boolean addMember(UUID playerId) {
        if (!canAcceptMembers()) return false;
        return members.add(Objects.requireNonNull(playerId, "playerId"));
    }

    boolean removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    void deactivate() {
        active = false;
    }

    public void addScore(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Score increment cannot be negative");
        score = Math.addExact(score, amount);
    }

    public void recordKill() { kills++; }
    public void recordDeath() { deaths++; }
}

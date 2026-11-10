package de.jawollo07.manhunt.teams;

import java.util.Set;
import java.util.UUID;

public final class Team {
    UUID teamID;
    String teamName;
    String teamColor;
    Set<UUID> teamMembers;
    int maxTeamSize;
    boolean friendlyFire;
    int score;
    int kills;
    int deaths;
    boolean active;
}

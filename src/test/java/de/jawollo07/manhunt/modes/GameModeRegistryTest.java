package de.jawollo07.manhunt.modes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameModeRegistryTest {
    @Test
    void createsFreshSessionForEachGame() {
        GameModeRegistry registry = new GameModeRegistry();
        registry.register(new GameModeFactory() {
            @Override public String getId() { return "assassin"; }
            @Override public String getDisplayName() { return "Assassin"; }
            @Override public GameMode createSession() {
                return new GameMode() {
                    @Override public String getId() { return "assassin"; }
                    @Override public String getDisplayName() { return "Assassin"; }
                };
            }
        });

        GameMode first = registry.createSession("ASSASSIN").orElseThrow();
        GameMode second = registry.createSession("assassin").orElseThrow();

        assertNotSame(first, second);
    }

    @Test
    void rejectsDuplicateModeIdsIgnoringCase() {
        GameModeRegistry registry = new GameModeRegistry();
        GameModeFactory factory = new GameModeFactory() {
            @Override public String getId() { return "assassin"; }
            @Override public String getDisplayName() { return "Assassin"; }
            @Override public GameMode createSession() {
                return new GameMode() {
                    @Override public String getId() { return "assassin"; }
                    @Override public String getDisplayName() { return "Assassin"; }
                };
            }
        };

        registry.register(factory);
        assertThrows(IllegalArgumentException.class, () -> registry.register(factory));
    }
}

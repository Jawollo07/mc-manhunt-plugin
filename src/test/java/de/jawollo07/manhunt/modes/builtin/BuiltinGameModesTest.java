package de.jawollo07.manhunt.modes.builtin;

import de.jawollo07.manhunt.modes.GameMode;
import de.jawollo07.manhunt.modes.GameModeFactory;
import de.jawollo07.manhunt.modes.GameModeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinGameModesTest {
    @Test
    void registersAllFourBuiltInModesAndCreatesFreshSessions() {
        GameModeRegistry registry = new GameModeRegistry();
        List<GameModeFactory> factories = BuiltinGameModes.factories(null);
        factories.forEach(registry::register);

        assertEquals(Set.of("assassin", "hitman", "juggernaut", "infection"),
                registry.getAll().stream().map(GameModeFactory::getId).collect(Collectors.toSet()));

        for (GameModeFactory factory : factories) {
            GameMode first = registry.createSession(factory.getId()).orElseThrow();
            GameMode second = registry.createSession(factory.getId()).orElseThrow();
            assertEquals(factory.getId(), first.getId());
            assertEquals(factory.getDisplayName(), first.getDisplayName());
            assertNotSame(first, second, "Each round must receive an isolated mode session");
        }
    }

    @Test
    void hitmanRequiresThreePlayersWhileOtherModesRequireTwo() {
        GameModeRegistry registry = new GameModeRegistry();
        BuiltinGameModes.factories(null).forEach(registry::register);

        assertEquals(3, registry.createSession("hitman").orElseThrow().getMinimumPlayers());
        assertEquals(2, registry.createSession("assassin").orElseThrow().getMinimumPlayers());
        assertEquals(2, registry.createSession("juggernaut").orElseThrow().getMinimumPlayers());
        assertEquals(2, registry.createSession("infection").orElseThrow().getMinimumPlayers());
    }

    @Test
    void modeIdsAreCaseInsensitive() {
        GameModeRegistry registry = new GameModeRegistry();
        BuiltinGameModes.factories(null).forEach(registry::register);

        assertTrue(registry.isRegistered("ASSASSIN"));
        assertTrue(registry.isRegistered("HitMan"));
        assertTrue(registry.isRegistered("JUGGERNAUT"));
        assertTrue(registry.isRegistered("InFeCtIoN"));
    }
}

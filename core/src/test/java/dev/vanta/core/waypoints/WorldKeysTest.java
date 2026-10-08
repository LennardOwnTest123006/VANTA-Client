package dev.vanta.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.GameBridge;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorldKeysTest {
    @Test
    void derivesSingleplayerMultiplayerAndNone() {
        assertEquals("sp:New World", WorldKeys.derive(true, true, Optional.of("New World"), Optional.empty()));
        assertEquals("sp:My World", WorldKeys.derive(true, true, Optional.of("  My World  "), Optional.empty()),
                "level names are trimmed but keep their case");
        assertEquals("mp:play.example.net", WorldKeys.derive(true, false, Optional.empty(),
                Optional.of("Play.Example.NET")));
        assertEquals("mp:play.example.net:25566", WorldKeys.derive(true, false, Optional.empty(),
                Optional.of(" play.example.net:25566 ")));
        assertEquals(WorldKeys.NONE, WorldKeys.derive(false, true, Optional.of("New World"),
                Optional.of("play.example.net")), "outside a world nothing counts");
        assertEquals(WorldKeys.NONE, WorldKeys.derive(true, true, Optional.empty(), Optional.of("srv")),
                "singleplayer without a level name is not filed under the server");
        assertEquals(WorldKeys.NONE, WorldKeys.derive(true, true, Optional.of("   "), Optional.empty()));
        assertEquals(WorldKeys.NONE, WorldKeys.derive(true, false, Optional.of("New World"), Optional.empty()),
                "a server without an address is not filed under a level");
        assertEquals(WorldKeys.NONE, WorldKeys.derive(true, false, Optional.empty(), Optional.of("")));
    }

    @Test
    void factoriesAndPredicates() {
        assertEquals("sp:Alpha", WorldKeys.singleplayer("Alpha"));
        assertEquals(WorldKeys.NONE, WorldKeys.singleplayer(null));
        assertEquals("mp:srv", WorldKeys.multiplayer("SRV"));
        assertEquals(WorldKeys.NONE, WorldKeys.multiplayer(null));

        assertTrue(WorldKeys.isNone(WorldKeys.NONE));
        assertTrue(WorldKeys.isNone(null));
        assertTrue(WorldKeys.isNone(" "));
        assertFalse(WorldKeys.isNone("sp:Alpha"));
        assertTrue(WorldKeys.isSingleplayer("sp:Alpha"));
        assertFalse(WorldKeys.isSingleplayer("sp:"), "prefix alone is not a world");
        assertFalse(WorldKeys.isSingleplayer("mp:srv"));
        assertFalse(WorldKeys.isSingleplayer(null));
        assertTrue(WorldKeys.isMultiplayer("mp:srv"));
        assertFalse(WorldKeys.isMultiplayer("mp:"));
        assertFalse(WorldKeys.isMultiplayer("sp:Alpha"));

        assertEquals("Alpha", WorldKeys.bareName("sp:Alpha"));
        assertEquals("srv:25565", WorldKeys.bareName("mp:srv:25565"));
        assertEquals("", WorldKeys.bareName(WorldKeys.NONE));
        assertEquals("", WorldKeys.bareName(null));
    }

    @Test
    void displayNamesComeFromTheLanguageFile() {
        assertEquals("Singleplayer: Alpha", WorldKeys.displayName("sp:Alpha"));
        assertEquals("Server: play.example.net", WorldKeys.displayName("mp:play.example.net"));
        assertEquals("No world", WorldKeys.displayName(WorldKeys.NONE));
        assertEquals("No world", WorldKeys.displayName(null));
    }

    @Test
    void gameBridgeDefaultBuildsTheKeyFromItsOtherMethods() {
        FakeGameBridge game = new FakeGameBridge();
        assertEquals(Optional.of("New World"), game.singleplayerLevelName());
        assertEquals("sp:New World", game.worldKey());
        assertEquals(game.worldKey(), WorldKeys.forGame(game));

        game.levelName = Optional.of("Hardcore 3");
        assertEquals("sp:Hardcore 3", game.worldKey());

        game.onServer("Play.Example.net", "Example", 42);
        assertEquals(Optional.empty(), game.singleplayerLevelName(), "no level on a server");
        assertEquals("mp:play.example.net", game.worldKey());

        game.onTitleScreen();
        assertEquals(WorldKeys.NONE, game.worldKey());
        assertEquals(Optional.empty(), game.singleplayerLevelName());

        game.inWorld = true;
        game.singleplayer = true;
        game.levelName = Optional.empty();
        assertEquals(WorldKeys.NONE, game.worldKey(), "host without level names: no key, no misfiled waypoints");
    }

    @Test
    void hostsWithoutTheNewMethodsGetTheDefaults() {
        // A host that implements only the abstract methods: default methods run the interface's own code.
        FakeGameBridge fake = new FakeGameBridge();
        GameBridge minimal = (GameBridge) Proxy.newProxyInstance(GameBridge.class.getClassLoader(),
                new Class<?>[] {GameBridge.class}, (proxy, method, args) -> method.isDefault()
                        ? InvocationHandler.invokeDefault(proxy, method, args)
                        : method.invoke(fake, args));
        assertTrue(minimal.isInWorld());
        assertTrue(minimal.isSingleplayer());
        assertEquals(Optional.empty(), minimal.singleplayerLevelName());
        assertEquals(WorldKeys.NONE, minimal.worldKey(), "singleplayer, but the level is unknown");

        fake.onServer("Play.Example.net", "Example", 42);
        assertEquals("mp:play.example.net", minimal.worldKey());
        fake.onTitleScreen();
        assertEquals(WorldKeys.NONE, minimal.worldKey());
    }
}

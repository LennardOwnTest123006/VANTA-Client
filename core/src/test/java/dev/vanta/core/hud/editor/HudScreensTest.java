package dev.vanta.core.hud.editor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.editor.CrosshairEditorScreen;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudScreensTest {
    @TempDir
    Path dir;

    @Test
    void registersTheEditorAndCrosshairScreens() {
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(),
                new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        HudScreens.register(services);
        assertTrue(services.screens().isRegistered(ScreenId.HUD_EDITOR));
        assertTrue(services.screens().isRegistered(ScreenId.CROSSHAIR));
        assertInstanceOf(HudEditorScreen.class, services.screens().create(ScreenId.HUD_EDITOR).orElseThrow());
        assertInstanceOf(CrosshairEditorScreen.class, services.screens().create(ScreenId.CROSSHAIR).orElseThrow());
    }
}

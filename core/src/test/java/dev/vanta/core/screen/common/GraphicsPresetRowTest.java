package dev.vanta.core.screen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.widget.Select;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Settings row of the graphics preset shows the game's real preset, Custom included, and never writes it. */
class GraphicsPresetRowTest {
    @TempDir
    Path dir;

    @Test
    @SuppressWarnings("unchecked")
    void rowShowsCustomAndSnapsBackWhenCustomIsChosen() {
        FakeOptionsBridge options = new FakeOptionsBridge();
        options.graphicsPresetBundle = true;
        SettingsStore store = new SettingsStore(VantaSettings.registry(), new JsonStore(MutableClock.standard()),
                dir.resolve("settings.json"), Optional.of(options));
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        options.setFromGame(VanillaOption.PARTICLES, "MINIMAL");

        SettingRow row = new SettingRow(VantaSettings.VIDEO_GRAPHICS_MODE, store, id -> { });
        Select<Object> select = (Select<Object>) row.editor();
        assertEquals("CUSTOM", select.value(), "the game is on Custom; the row must not claim Fast or Fancy");
        UiTestSupport ui = new UiTestSupport();
        ui.place(row, 0, 0, 400, SettingRow.HEIGHT);
        TestCanvas canvas = ui.render(row);
        assertTrue(canvas.hasText("Custom"), "labelled Custom");

        select.select(ui.ctx(), select.options().indexOf("FANCY"));
        assertEquals("FANCY", options.values().get(VanillaOption.GRAPHICS_MODE), "picking Fancy applies it");
        assertEquals("FANCY", select.value());

        int writes = options.setOrder.size();
        select.select(ui.ctx(), select.options().indexOf("CUSTOM"));
        assertEquals("FANCY", select.value(), "Custom cannot be chosen: the row snaps back to the live preset");
        assertEquals("FANCY", options.values().get(VanillaOption.GRAPHICS_MODE));
        assertEquals(writes, options.setOrder.size(), "nothing written for the refused choice");
    }
}

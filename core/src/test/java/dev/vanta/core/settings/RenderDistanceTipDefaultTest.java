package dev.vanta.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.Migrations;
import org.junit.jupiter.api.Test;

/** The "Render distance tip" is opt-in from client 1.6.0 on, also for settings written before. */
class RenderDistanceTipDefaultTest {
    @Test
    void offByDefault() {
        assertFalse(VantaSettings.PERFORMANCE_RENDER_DISTANCE_SUGGESTIONS.defaultValue());
    }

    @Test
    void oldFilesLoseTheStoredValueAndKeepEverythingElse() {
        JsonObject old = JsonParser.parseString("""
                {"schemaVersion": 1, "values": {"performance.dynamicRenderDistanceSuggestions": true,
                 "hud.enabled": false}}""").getAsJsonObject();
        Migrations.Result result = SettingsStore.MIGRATIONS.migrate(old);
        assertEquals(Migrations.Status.MIGRATED, result.status());
        JsonObject values = result.json().getAsJsonObject("values");
        assertFalse(values.has("performance.dynamicRenderDistanceSuggestions"));
        assertTrue(values.has("hud.enabled"));
        assertEquals(2, result.json().get("schemaVersion").getAsInt());
    }
}

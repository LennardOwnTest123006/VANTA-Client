package dev.vanta.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationsTest {
    @Test
    void chainMustBeContiguous() {
        assertThrows(IllegalArgumentException.class, () -> new Migrations(3, List.of(Migrations.step(1, 2, j -> j))));
        assertThrows(IllegalArgumentException.class, () -> new Migrations(3, List.of(Migrations.step(2, 3, j -> j))));
        assertThrows(IllegalArgumentException.class, () -> Migrations.step(1, 3, j -> j));
    }

    @Test
    void appliesStepsInOrderWithoutMutatingInput() {
        Migrations migrations = new Migrations(3, List.of(
                Migrations.step(1, 2, j -> {
                    j.addProperty("a", 1);
                    return j;
                }),
                Migrations.step(2, 3, j -> {
                    j.addProperty("b", j.get("a").getAsInt() + 1);
                    return j;
                })));
        JsonObject input = new JsonObject();
        Migrations.Result result = migrations.migrate(input);
        assertEquals(Migrations.Status.MIGRATED, result.status());
        assertEquals(1, result.fromVersion());
        assertEquals(3, result.toVersion());
        assertEquals(2, result.json().get("b").getAsInt());
        assertEquals(3, result.json().get("schemaVersion").getAsInt());
        assertFalse(input.has("a"), "input untouched");
        assertTrue(result.changed());
    }

    @Test
    void upToDateAndNewerAreReported() {
        Migrations migrations = Migrations.none(2);
        JsonObject current = new JsonObject();
        current.addProperty("schemaVersion", 2);
        assertEquals(Migrations.Status.UP_TO_DATE, migrations.migrate(current).status());
        JsonObject newer = new JsonObject();
        newer.addProperty("schemaVersion", 5);
        Migrations.Result result = migrations.migrate(newer);
        assertEquals(Migrations.Status.NEWER_THAN_SUPPORTED, result.status());
        assertEquals(5, result.json().get("schemaVersion").getAsInt());
    }
}

package dev.vanta.launcher.ui.view;

import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dialog layer over a whole session: every "Remove mod" confirmation, sign-in and update dialog is a fresh object
 * shown once, and a closed one must not stay reachable through the layer (no JavaFX toolkit is needed: reduced motion
 * makes every transition instant).
 */
class DialogLayerTest {

    @BeforeEach
    void instantMotion() {
        Motion.reducedProperty().set(true);
    }

    @AfterEach
    void restoreMotion() {
        Motion.reducedProperty().set(false);
    }

    @Test
    void closedDialogsAreNotRetainedByTheLayer() throws Exception {
        final DialogLayer layer = new DialogLayer();
        final List<WeakReference<VBox>> cards = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final VBox card = new VBox();
            cards.add(new WeakReference<>(card));
            layer.show(() -> card);
            assertTrue(layer.isOpen());
            layer.close();
        }
        assertFalse(layer.isOpen());
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (cards.stream().anyMatch(r -> r.get() != null) && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        assertEquals(0, cards.stream().filter(r -> r.get() != null).count(),
            "closed dialog cards are collectable: the layer keeps no listener that references them");
    }

    /** The open dialog still follows the window height with the layer's single listener; a closed one is left alone. */
    @Test
    void onlyTheOpenDialogFollowsTheLayerHeight() {
        final DialogLayer layer = new DialogLayer();
        final VBox first = new VBox();
        first.setPrefHeight(100);
        layer.show(() -> first);
        layer.resize(400, 120);
        assertEquals(120 - 48, first.getMaxHeight(), 0.001, "a card taller than the window shrinks to it");
        layer.resize(400, 600);
        assertEquals(Region.USE_PREF_SIZE, first.getMaxHeight(), 0.001, "and grows back");
        layer.close();
        final VBox second = new VBox();
        second.setPrefHeight(100);
        layer.show(() -> second);
        layer.resize(400, 90);
        assertEquals(90 - 48, second.getMaxHeight(), 0.001, "the dialog shown next is clamped");
        assertEquals(Region.USE_PREF_SIZE, first.getMaxHeight(), 0.001, "the closed one is left alone");
    }
}

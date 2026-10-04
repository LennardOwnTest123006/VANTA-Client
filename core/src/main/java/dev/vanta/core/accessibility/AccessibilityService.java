package dev.vanta.core.accessibility;

import dev.vanta.core.settings.SettingChange;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Derives {@link AccessibilityState} from the settings store and pushes updates to listeners (the UI theme).
 */
public final class AccessibilityService {
    private static final Set<String> WATCHED = Set.of(
            VantaSettings.GENERAL_UI_SCALE.id(),
            VantaSettings.ACCESSIBILITY_REDUCED_MOTION.id(),
            VantaSettings.ACCESSIBILITY_HIGH_CONTRAST.id(),
            VantaSettings.ACCESSIBILITY_LARGE_TEXT.id(),
            VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY.id(),
            VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE.id());

    private final SettingsStore settings;
    private final List<Consumer<AccessibilityState>> listeners = new CopyOnWriteArrayList<>();
    private AccessibilityState state;

    public AccessibilityService(SettingsStore settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.state = AccessibilityState.fromSettings(settings);
        settings.addListener(this::onSettingChanged);
    }

    /** Current state. */
    public AccessibilityState state() {
        return state;
    }

    /** Subscribes; the listener is called immediately with the current state and after every change. */
    public Runnable listen(Consumer<AccessibilityState> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        listener.accept(state);
        return () -> listeners.remove(listener);
    }

    /** Recomputes the state (e.g. after {@code SettingsStore.load()}). */
    public void refresh() {
        AccessibilityState next = AccessibilityState.fromSettings(settings);
        if (!next.equals(state)) {
            state = next;
            for (Consumer<AccessibilityState> listener : listeners) {
                listener.accept(next);
            }
        }
    }

    private void onSettingChanged(SettingChange change) {
        if (WATCHED.contains(change.setting().id())) {
            refresh();
        }
    }
}

package dev.vanta.core.lab;

import dev.vanta.core.ai.LabToggle;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Typed access to the {@link LabFeature} toggles stored in {@link SettingsStore}. Listeners fire for every way the
 * value changes (this class, the Settings screen, a profile switch, reset), on the render thread.
 */
public final class LabSettings {
    private static final Map<LabFeature, Setting<Boolean>> SETTINGS;

    static {
        Map<LabFeature, Setting<Boolean>> map = new EnumMap<>(LabFeature.class);
        map.put(LabFeature.DYNAMIC_HUD, VantaSettings.LAB_DYNAMIC_HUD);
        map.put(LabFeature.ANIMATED_CROSSHAIR, VantaSettings.LAB_ANIMATED_CROSSHAIR);
        map.put(LabFeature.WAYPOINT_BEAMS, VantaSettings.LAB_WAYPOINT_BEAMS);
        map.put(LabFeature.FRAMETIME_GRAPH, VantaSettings.LAB_FRAMETIME_GRAPH);
        map.put(LabFeature.SCREEN_TRANSITIONS, VantaSettings.LAB_SCREEN_TRANSITIONS);
        for (LabFeature feature : LabFeature.values()) {
            Setting<Boolean> setting = map.get(feature);
            if (setting == null || !setting.id().equals(feature.settingId())) {
                throw new IllegalStateException("LabFeature " + feature + " has no matching setting");
            }
        }
        SETTINGS = Collections.unmodifiableMap(map);
    }

    private final SettingsStore settings;
    private final List<BiConsumer<LabFeature, Boolean>> listeners = new CopyOnWriteArrayList<>();

    public LabSettings(SettingsStore settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        settings.addListener(change -> featureOf(change.setting())
                .ifPresent(feature -> fire(feature, change.newValueAs(setting(feature)))));
    }

    /** The setting behind a feature. */
    public static Setting<Boolean> setting(LabFeature feature) {
        return SETTINGS.get(Objects.requireNonNull(feature, "feature"));
    }

    /** The feature behind a setting, if it is a Lab toggle. */
    public static Optional<LabFeature> featureOf(Setting<?> setting) {
        for (Map.Entry<LabFeature, Setting<Boolean>> entry : SETTINGS.entrySet()) {
            if (entry.getValue().id().equals(setting.id())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** Current state. */
    public boolean isEnabled(LabFeature feature) {
        return settings.get(setting(feature));
    }

    /**
     * Sets a feature.
     *
     * @return true when the value changed (listeners fired)
     */
    public boolean set(LabFeature feature, boolean enabled) {
        return settings.set(setting(feature), enabled);
    }

    /** Flips a feature and returns its new state. */
    public boolean toggle(LabFeature feature) {
        boolean next = !isEnabled(feature);
        set(feature, next);
        return next;
    }

    /** Features currently on. */
    public Set<LabFeature> enabled() {
        Set<LabFeature> out = EnumSet.noneOf(LabFeature.class);
        for (LabFeature feature : LabFeature.values()) {
            if (isEnabled(feature)) {
                out.add(feature);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Every feature with its state, in enum order. */
    public Map<LabFeature, Boolean> snapshot() {
        Map<LabFeature, Boolean> out = new EnumMap<>(LabFeature.class);
        for (LabFeature feature : LabFeature.values()) {
            out.put(feature, isEnabled(feature));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Listens to one feature.
     *
     * @return a handle that unsubscribes when run
     */
    public Runnable onChange(LabFeature feature, Consumer<Boolean> listener) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(listener, "listener");
        return onAnyChange((f, enabled) -> {
            if (f == feature) {
                listener.accept(enabled);
            }
        });
    }

    /**
     * Listens to every feature.
     *
     * @return a handle that unsubscribes when run
     */
    public Runnable onAnyChange(BiConsumer<LabFeature, Boolean> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    /**
     * These toggles as the assistant's {@link LabToggle}: feature ids in enum order, {@code set} succeeds for every
     * known id (also when the value did not change), unknown ids are refused.
     */
    public LabToggle asToggle() {
        return new LabToggle() {
            @Override
            public List<String> features() {
                List<String> ids = new ArrayList<>();
                for (LabFeature feature : LabFeature.values()) {
                    ids.add(feature.id());
                }
                return ids;
            }

            @Override
            public boolean isEnabled(String feature) {
                return LabFeature.fromId(feature).map(LabSettings.this::isEnabled).orElse(false);
            }

            @Override
            public boolean set(String feature, boolean enabled) {
                Optional<LabFeature> known = LabFeature.fromId(feature);
                if (known.isEmpty()) {
                    return false;
                }
                LabSettings.this.set(known.get(), enabled);
                return true;
            }
        };
    }

    private void fire(LabFeature feature, boolean enabled) {
        for (BiConsumer<LabFeature, Boolean> listener : listeners) {
            try {
                listener.accept(feature, enabled);
            } catch (RuntimeException e) {
                CoreLog.warn(e, "Lab listener failed for {}", feature.id());
            }
        }
    }
}

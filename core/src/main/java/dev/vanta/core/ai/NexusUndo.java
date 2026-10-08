package dev.vanta.core.ai;

import com.google.gson.JsonElement;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Undo for assistant turns. A {@link Turn} snapshots the HUD layout, every setting value and the active profile
 * before the turn's actions run; {@link #commit(Turn, String)} keeps only what actually changed (plus restore steps
 * individual actions registered, such as a toggled lab feature) and {@link #undoLast()} puts it back: the profile
 * first, then the changed setting values, then the layout, then the registered steps in reverse order.
 */
public final class NexusUndo {
    /** How many turns can be undone. */
    public static final int MAX_TURNS = 20;

    /** One turn being recorded. */
    public final class Turn {
        private final HudLayout hudBefore;
        private final Map<String, JsonElement> settingsBefore;
        private final Optional<String> profileBefore;
        private final List<Runnable> restores = new ArrayList<>();

        private Turn() {
            this.hudBefore = hud.layout();
            this.settingsBefore = new LinkedHashMap<>(settings.snapshot(true));
            this.profileBefore = profiles.activeId();
        }

        /** Registers a step that reverts something the snapshots do not cover (waypoints, lab features). */
        public void addRestore(Runnable restore) {
            restores.add(Objects.requireNonNull(restore, "restore"));
        }
    }

    /** A committed turn. */
    private record Entry(String label, Optional<String> profile, Map<String, JsonElement> settings,
                         Optional<HudLayout> hud, List<Runnable> restores) {
    }

    private final HudStore hud;
    private final SettingsStore settings;
    private final ProfileManager profiles;
    private final Predicate<String> profileActivator;
    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * @param profileActivator re-activates a profile by id (the composition root's {@code activateProfile})
     */
    public NexusUndo(HudStore hud, SettingsStore settings, ProfileManager profiles, Predicate<String> profileActivator) {
        this.hud = Objects.requireNonNull(hud, "hud");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.profileActivator = Objects.requireNonNull(profileActivator, "profileActivator");
    }

    /** Snapshots the current state; call before the turn's actions run. */
    public Turn begin() {
        return new Turn();
    }

    /**
     * Records what the turn changed.
     *
     * @return true when something can be undone
     */
    public boolean commit(Turn turn, String label) {
        Objects.requireNonNull(turn, "turn");
        Optional<String> profileAfter = profiles.activeId();
        Optional<String> profile = turn.profileBefore.isPresent() && !turn.profileBefore.equals(profileAfter)
                ? turn.profileBefore : Optional.empty();
        Map<String, JsonElement> after = settings.snapshot(true);
        Map<String, JsonElement> changed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : turn.settingsBefore.entrySet()) {
            if (!Objects.equals(e.getValue(), after.get(e.getKey()))) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        Optional<HudLayout> layout = turn.hudBefore.equals(hud.layout()) ? Optional.empty()
                : Optional.of(turn.hudBefore);
        if (profile.isEmpty() && changed.isEmpty() && layout.isEmpty() && turn.restores.isEmpty()) {
            return false;
        }
        entries.push(new Entry(label == null ? "" : label, profile, changed, layout, List.copyOf(turn.restores)));
        while (entries.size() > MAX_TURNS) {
            entries.removeLast();
        }
        return true;
    }

    /** True when a turn can be undone. */
    public boolean canUndo() {
        return !entries.isEmpty();
    }

    /** Number of undoable turns. */
    public int depth() {
        return entries.size();
    }

    /** The assistant message of the turn {@link #undoLast()} would revert. */
    public Optional<String> lastLabel() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.peek().label());
    }

    /**
     * Reverts the most recent turn.
     *
     * @return false when nothing was recorded
     */
    public boolean undoLast() {
        Entry entry = entries.poll();
        if (entry == null) {
            return false;
        }
        entry.profile().ifPresent(id -> {
            if (!profileActivator.test(id)) {
                CoreLog.warn("Undo could not re-activate profile {}", id);
            }
        });
        for (Map.Entry<String, JsonElement> e : entry.settings().entrySet()) {
            Optional<Setting<?>> setting = settings.registry().find(e.getKey());
            setting.ifPresent(s -> settings.setJson(s, e.getValue()));
        }
        entry.hud().ifPresent(hud::setLayout);
        List<Runnable> restores = entry.restores();
        for (int i = restores.size() - 1; i >= 0; i--) {
            try {
                restores.get(i).run();
            } catch (RuntimeException e) {
                CoreLog.warn(e, "Undo step failed");
            }
        }
        return true;
    }

    /** Forgets every recorded turn. */
    public void clear() {
        entries.clear();
    }
}

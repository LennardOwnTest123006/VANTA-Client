package dev.vanta.launcher.ui.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Logs page model: two sources (launcher, game), a text filter and level toggles. Lines arrive from any thread
 * through the {@link LogBuffer}s and are batched onto the UI thread.
 */
public final class LogsViewModel {

    /** Log source tabs. */
    public enum Source {
        /** Launcher messages. */
        LAUNCHER,
        /** Game process output. */
        GAME
    }

    private final UiExecutors executors;
    private final Map<Source, ObservableList<LogLine>> all = new EnumMap<>(Source.class);
    private final Map<Source, FilteredList<LogLine>> filtered = new EnumMap<>(Source.class);
    private final Map<Source, ConcurrentLinkedQueue<LogLine>> pending = new EnumMap<>(Source.class);
    private final Map<Source, AtomicBoolean> flushScheduled = new EnumMap<>(Source.class);
    private final ObjectProperty<Source> source = new SimpleObjectProperty<>(Source.LAUNCHER);
    private final StringProperty filter = new SimpleStringProperty("");
    private final Set<LogLevel> enabledLevels = EnumSet.allOf(LogLevel.class);
    private final Map<LogLevel, BooleanProperty> levelToggles = new EnumMap<>(LogLevel.class);
    private final BooleanProperty follow = new SimpleBooleanProperty(true);
    private final ReadOnlyIntegerWrapper visibleCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper totalCount = new ReadOnlyIntegerWrapper(0);
    private final int capacity;

    /**
     * @param executors   executors
     * @param launcherLog launcher buffer
     * @param gameLog     game buffer
     */
    public LogsViewModel(final UiExecutors executors, final LogBuffer launcherLog, final LogBuffer gameLog) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.capacity = Math.max(launcherLog.capacity(), gameLog.capacity());
        for (Source s : Source.values()) {
            all.put(s, FXCollections.observableArrayList());
            filtered.put(s, new FilteredList<>(all.get(s), line -> true));
            pending.put(s, new ConcurrentLinkedQueue<>());
            flushScheduled.put(s, new AtomicBoolean());
        }
        for (LogLevel level : LogLevel.values()) {
            final BooleanProperty p = new SimpleBooleanProperty(true);
            p.addListener((obs, old, now) -> {
                if (now) {
                    enabledLevels.add(level);
                } else {
                    enabledLevels.remove(level);
                }
                applyFilter();
            });
            levelToggles.put(level, p);
        }
        filter.addListener((obs, old, now) -> applyFilter());
        source.addListener((obs, old, now) -> updateCounts());
        all.get(Source.LAUNCHER).setAll(launcherLog.snapshot());
        all.get(Source.GAME).setAll(gameLog.snapshot());
        launcherLog.addListener(line -> enqueue(Source.LAUNCHER, line));
        gameLog.addListener(line -> enqueue(Source.GAME, line));
        applyFilter();
    }

    /** @return selected source */
    public ObjectProperty<Source> sourceProperty() {
        return source;
    }

    /** @return filter text */
    public StringProperty filterProperty() {
        return filter;
    }

    /**
     * @param level level
     * @return toggle for the level
     */
    public BooleanProperty levelEnabledProperty(final LogLevel level) {
        return levelToggles.get(level);
    }

    /** @return auto-scroll to the newest line */
    public BooleanProperty followProperty() {
        return follow;
    }

    /** @return lines visible for the current source after filtering */
    public ReadOnlyIntegerProperty visibleCountProperty() {
        return visibleCount.getReadOnlyProperty();
    }

    /** @return all lines of the current source */
    public ReadOnlyIntegerProperty totalCountProperty() {
        return totalCount.getReadOnlyProperty();
    }

    /**
     * @param s source
     * @return filtered, observable lines of a source
     */
    public FilteredList<LogLine> lines(final Source s) {
        return filtered.get(s);
    }

    /** @return filtered lines of the current source */
    public FilteredList<LogLine> currentLines() {
        return filtered.get(source.get());
    }

    /** @return whether the current source has no lines at all */
    public boolean currentIsEmpty() {
        return all.get(source.get()).isEmpty();
    }

    /** Clears the current source's view (the on-disk log is untouched). */
    public void clearView() {
        all.get(source.get()).clear();
        updateCounts();
    }

    /** @return the visible lines of the current source as text */
    public String copyText() {
        return currentLines().stream().map(LogLine::text).collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Appends a line directly (UI thread; used when the model itself produces a message).
     *
     * @param s    source
     * @param line line
     */
    void appendNow(final Source s, final LogLine line) {
        final ObservableList<LogLine> list = all.get(s);
        list.add(line);
        trim(list);
        if (s == source.get()) {
            updateCounts();
        }
    }

    private void enqueue(final Source s, final LogLine line) {
        pending.get(s).add(line);
        if (flushScheduled.get(s).compareAndSet(false, true)) {
            executors.onUi(() -> flush(s));
        }
    }

    private void flush(final Source s) {
        flushScheduled.get(s).set(false);
        final ConcurrentLinkedQueue<LogLine> queue = pending.get(s);
        final List<LogLine> batch = new ArrayList<>();
        LogLine line;
        while ((line = queue.poll()) != null) {
            batch.add(line);
        }
        if (batch.isEmpty()) {
            return;
        }
        final ObservableList<LogLine> list = all.get(s);
        list.addAll(batch);
        trim(list);
        if (s == source.get()) {
            updateCounts();
        }
    }

    private void trim(final ObservableList<LogLine> list) {
        final int excess = list.size() - capacity;
        if (excess > 0) {
            list.remove(0, excess);
        }
    }

    private void applyFilter() {
        final String needle = filter.get() == null ? "" : filter.get().trim().toLowerCase(Locale.ROOT);
        final Set<LogLevel> levels = EnumSet.copyOf(enabledLevels.isEmpty() ? EnumSet.noneOf(LogLevel.class) : enabledLevels);
        final Predicate<LogLine> predicate = line -> levels.contains(line.level()) && line.matches(needle);
        for (FilteredList<LogLine> f : filtered.values()) {
            f.setPredicate(predicate);
        }
        updateCounts();
    }

    private void updateCounts() {
        visibleCount.set(currentLines().size());
        totalCount.set(all.get(source.get()).size());
    }
}

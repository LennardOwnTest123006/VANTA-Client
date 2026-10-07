package dev.vanta.launcher.core.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.FabricModInfo;
import dev.vanta.launcher.core.modrinth.ModJarCheck;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The start check: before the game starts (PLAY, "PLAY via Minecraft Launcher", the command line) and when the
 * launcher window opens, mods in the VANTA instance that would stop Minecraft while starting are switched off.
 *
 * <ol>
 *   <li><b>Check</b>: every enabled {@code mods/*.jar} except the VANTA Client and Fabric API (the launcher manages
 *       those) is checked with {@link ModJarCheck}; a flagged jar is switched off (one whose {@code fabric.mod.json}
 *       id is VANTA's or Fabric's is only reported).</li>
 *   <li><b>Crash report</b>: the newest {@code crash-reports/*.txt} that was not handled before. When it holds Fabric's
 *       {@code Could not execute entrypoint stage '<stage>' due to errors, provided by '<modid>'} and is newer than the
 *       enabled jar that provides {@code <modid>} (its {@code fabric.mod.json} id, or else the id of a jar nested in it),
 *       that jar is switched off. VANTA, Fabric API and its modules, Fabric Loader, Minecraft and Java are never switched
 *       off; they are only reported. Each report is acted on once (remembered in {@link LauncherPaths#startupCheckFile()});
 *       a report without that line that changed in the last {@link #WRITE_SETTLE} may still be being written and is
 *       looked at again on the next run.</li>
 * </ol>
 *
 * <p>Switching off renames {@code x.jar} to {@code x.jar.disabled} through {@link ModrinthService#setEnabled}, so a jar
 * {@code modrinth.json} tracks is recorded as disabled there too and the Mods page can switch it on again. Nothing is
 * deleted. Runs for the same instance never overlap.</p>
 */
public final class StartupGuard {

    /** Fabric's message when a mod's entrypoint failed (group 1: stage, group 2: mod id). */
    static final Pattern ENTRYPOINT_FAILURE =
        Pattern.compile("Could not execute entrypoint stage '([^']*)' due to errors, provided by '([^']*)'");
    /** Mod ids that are never switched off. */
    static final Set<String> PROTECTED_IDS = Set.of("vanta", "fabric-api", "fabric", "fabricloader", "minecraft", "java");
    /** Version of the check; jars that passed an older one are checked again. */
    static final int CHECK_VERSION = 1;
    private static final int MAX_HANDLED_REPORTS = 64;
    private static final int MAX_REPORT_BYTES = 4 * 1024 * 1024;
    private static final Logger LOG = LauncherLog.get("StartupCheck");
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    /** A crash report without the entrypoint line that changed more recently than this is looked at again later. */
    static final Duration WRITE_SETTLE = Duration.ofSeconds(30);

    private final LauncherPaths paths;
    private final ModrinthService modrinth;
    private final Clock clock;

    /** Where a switch-off comes from. */
    public enum Source {
        /** {@link ModJarCheck} flagged the jar. */
        CHECK,
        /** A crash report names the mod. */
        CRASH_REPORT
    }

    /**
     * One mod the check acted on.
     *
     * @param file        the jar (where it was before it was switched off)
     * @param modId       Fabric mod id (empty when unknown)
     * @param modName     name from {@code fabric.mod.json} (the file name when unknown)
     * @param modVersion  version from {@code fabric.mod.json} (empty when unknown)
     * @param reason      why (English)
     * @param source      check or crash report
     * @param switchedOff whether it was switched off (false: only reported)
     * @param crashReport file name of the crash report (empty for {@link Source#CHECK})
     */
    public record Action(Path file, String modId, String modName, String modVersion, String reason, Source source, boolean switchedOff,
                         String crashReport) {

        public Action {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(source, "source");
            modId = modId == null ? "" : modId;
            modName = modName == null || modName.isBlank() ? file.getFileName().toString() : modName;
            modVersion = modVersion == null ? "" : modVersion;
            reason = reason == null ? "" : reason;
            crashReport = crashReport == null ? "" : crashReport;
        }

        /** @return the jar's file name */
        public String fileName() {
            return file.getFileName().toString();
        }

        /** @return {@code "<name> <version>"} */
        public String label() {
            return modVersion.isEmpty() ? modName : modName + " " + modVersion;
        }
    }

    /**
     * Outcome of a run.
     *
     * @param checked number of mod jars the check looked at
     * @param actions what it did, in order
     */
    public record Report(int checked, List<Action> actions) {

        /** No jar checked, nothing done. */
        public static final Report EMPTY = new Report(0, List.of());

        public Report {
            actions = List.copyOf(actions);
        }

        /** @return the mods that were switched off */
        public List<Action> switchedOff() {
            return actions.stream().filter(Action::switchedOff).toList();
        }

        /** @return mods that were only reported (protected, or the rename failed) */
        public List<Action> reportedOnly() {
            return actions.stream().filter(a -> !a.switchedOff()).toList();
        }

        /**
         * @param other a later run
         * @return both runs' actions; the larger jar count
         */
        public Report plus(final Report other) {
            final List<Action> all = new ArrayList<>(actions);
            all.addAll(other.actions());
            return new Report(Math.max(checked, other.checked()), all);
        }

        /**
         * The lines the command line prints (the CI job looks for these prefixes).
         *
         * @return {@code Startup check: switched off <file> (<name> <version>): <reason>} per switched-off jar, a line per
         *     reported mod, and {@code Startup check: <n> mods checked, nothing to switch off} when nothing was switched off
         */
        public List<String> cliLines() {
            final List<String> lines = new ArrayList<>();
            for (Action a : actions) {
                lines.add(a.switchedOff()
                    ? "Startup check: switched off " + a.fileName() + " (" + a.label() + "): " + a.reason()
                    : "Startup check: did not switch off " + a.fileName() + " (" + a.label() + "): " + a.reason());
            }
            if (switchedOff().isEmpty()) {
                lines.add("Startup check: " + checked + " mods checked, nothing to switch off");
            }
            return lines;
        }
    }

    /**
     * @param paths    launcher paths (the instance)
     * @param modrinth content service ({@link ModrinthService#setEnabled} switches mods off)
     */
    public StartupGuard(final LauncherPaths paths, final ModrinthService modrinth) {
        this(paths, modrinth, Clock.systemUTC());
    }

    /**
     * @param paths    launcher paths (the instance)
     * @param modrinth content service ({@link ModrinthService#setEnabled} switches mods off)
     * @param clock    clock (how old a crash report is)
     */
    public StartupGuard(final LauncherPaths paths, final ModrinthService modrinth, final Clock clock) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.modrinth = Objects.requireNonNull(modrinth, "modrinth");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Both parts: the check, then the crash report.
     *
     * @return what was done
     * @throws IOException when {@code mods/} cannot be read
     */
    public Report run() throws IOException {
        final ReentrantLock lock = lock();
        try {
            return checkModsLocked().plus(recoverLocked());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Only the check of {@code mods/*.jar}.
     *
     * @return what was done
     * @throws IOException when {@code mods/} cannot be read
     */
    public Report checkMods() throws IOException {
        final ReentrantLock lock = lock();
        try {
            return checkModsLocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Only the crash report part (after the game exited, while the Minecraft Launcher plays).
     *
     * @return what was done
     * @throws IOException when a folder cannot be read
     */
    public Report recoverFromCrashReport() throws IOException {
        final ReentrantLock lock = lock();
        try {
            return recoverLocked();
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lock() throws IOException {
        final ReentrantLock lock = LOCKS.computeIfAbsent(paths.instanceDir().toAbsolutePath().normalize(), p -> new ReentrantLock());
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for another start check", e);
        }
        return lock;
    }

    // ---------------------------------------------------------------- check

    private Report checkModsLocked() throws IOException {
        final State state = State.load(paths.startupCheckFile());
        final List<Action> actions = new ArrayList<>();
        int checked = 0;
        for (Path jar : enabledJars()) {
            final String name = jar.getFileName().toString();
            if (ModrinthService.isManaged(name)) {
                continue;
            }
            checked++;
            final String stamp = stamp(jar);
            if (stamp.equals(state.passed.get(name))) {
                continue;
            }
            final ModJarCheck.Result result = ModJarCheck.check(jar);
            if (result.flagged()) {
                state.passed.remove(name);
                LOG.log(Level.FINE, "{0}: {1} uses KeyMapping{2}{3}", new Object[] {name, result.className(), result.descriptor(),
                    result.nestedJar().isEmpty() ? "" : " (in " + result.nestedJar() + ")"});
                if (!result.modId().isEmpty() && isProtected(result.modId())) {
                    // VANTA or Fabric under a file name the launcher does not manage: never switched off, only reported.
                    actions.add(new Action(jar, result.modId(), result.modName(), result.modVersion(),
                        ModJarCheck.REASON + "; VANTA does not switch off " + result.modId(), Source.CHECK, false, ""));
                    continue;
                }
                actions.add(switchOff(jar, result.modId(), result.modName(), result.modVersion(), ModJarCheck.REASON, Source.CHECK, ""));
            } else if (result.status() == ModJarCheck.Status.PASSED) {
                state.passed.put(name, stamp);
            } else {
                LOG.log(Level.FINE, "{0}: {1}", new Object[] {name, result.detail()});
            }
        }
        state.passed.keySet().removeIf(n -> !Files.isRegularFile(paths.modsDir().resolve(n)));
        state.saveQuietly();
        return new Report(checked, actions);
    }

    // ---------------------------------------------------------------- crash report

    private Report recoverLocked() throws IOException {
        final Optional<Path> newest = newestCrashReport();
        if (newest.isEmpty()) {
            return Report.EMPTY;
        }
        final Path report = newest.get();
        final String reportName = report.getFileName().toString();
        final State state = State.load(paths.startupCheckFile());
        if (state.handled.contains(reportName)) {
            return Report.EMPTY;
        }
        final List<Action> actions = new ArrayList<>();
        final Optional<String> modId = providerOf(readReport(report));
        if (modId.isPresent()) {
            act(report, reportName, modId.get(), actions);
        } else if (beingWritten(report)) {
            // Minecraft may still be writing it (the crash report watch looks every few seconds): look again later.
            return Report.EMPTY;
        }
        state.handled.add(reportName);
        state.saveQuietly();
        return new Report(0, actions);
    }

    private void act(final Path report, final String reportName, final String modId, final List<Action> actions) throws IOException {
        final String reason = "Minecraft stopped while starting because of it (crash report " + reportName + ")";
        final Optional<Path> provider = jarProviding(modId);
        if (provider.isPresent() && Files.getLastModifiedTime(report).compareTo(Files.getLastModifiedTime(provider.get())) <= 0) {
            // The jar was installed or replaced after the crash: the report says nothing about it.
            LOG.log(Level.FINE, "The crash report {0} is older than {1}; ignored", new Object[] {reportName, provider.get().getFileName()});
            return;
        }
        if (isProtected(modId) || provider.map(p -> ModrinthService.isManaged(p.getFileName().toString())).orElse(false)) {
            LOG.log(Level.INFO, "The crash report {0} names {1}; the launcher does not switch it off", new Object[] {reportName, modId});
            final Path file = provider.orElse(paths.modsDir().resolve(modId));
            final Optional<FabricModInfo> info = provider.flatMap(FabricModInfo::read);
            actions.add(new Action(file, modId, info.map(FabricModInfo::name).orElse(modId), info.map(FabricModInfo::version).orElse(""),
                reason + "; VANTA does not switch off " + modId, Source.CRASH_REPORT, false, reportName));
            return;
        }
        if (provider.isEmpty()) {
            LOG.log(Level.FINE, "The crash report {0} names {1}, which no enabled jar in mods/ provides", new Object[] {reportName, modId});
            return;
        }
        final Path jar = provider.get();
        final Optional<FabricModInfo> info = FabricModInfo.read(jar);
        actions.add(switchOff(jar, modId, info.map(FabricModInfo::name).orElse(modId), info.map(FabricModInfo::version).orElse(""), reason,
            Source.CRASH_REPORT, reportName));
    }

    /**
     * @param text crash report
     * @return the mod id Fabric names as provider of the failing entrypoint
     */
    static Optional<String> providerOf(final String text) {
        final Matcher m = ENTRYPOINT_FAILURE.matcher(text);
        return m.find() && !m.group(2).isBlank() ? Optional.of(m.group(2)) : Optional.empty();
    }

    /**
     * @param modId Fabric mod id
     * @return whether the launcher never switches it off (VANTA, Fabric API and its modules, Fabric Loader, Minecraft, Java)
     */
    static boolean isProtected(final String modId) {
        final String id = modId.toLowerCase(Locale.ROOT);
        return PROTECTED_IDS.contains(id) || id.startsWith("fabric-");
    }

    private Optional<Path> newestCrashReport() throws IOException {
        final Path dir = paths.crashReportsDir();
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        final List<Path> reports = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    reports.add(p);
                }
            }
        }
        return reports.stream().max(Comparator.comparing(StartupGuard::modifiedMillis).thenComparing(p -> p.getFileName().toString()));
    }

    private static long modifiedMillis(final Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private static String readReport(final Path report) throws IOException {
        try (InputStream in = Files.newInputStream(report)) {
            return new String(in.readNBytes(MAX_REPORT_BYTES), StandardCharsets.UTF_8);
        }
    }

    /**
     * @param report a crash report without Fabric's entrypoint line
     * @return whether it changed in the last {@link #WRITE_SETTLE} (it may still be being written)
     */
    private boolean beingWritten(final Path report) {
        final long age = clock.millis() - modifiedMillis(report);
        return age >= 0 && age < WRITE_SETTLE.toMillis();
    }

    /** @return the enabled jar whose own id is {@code modId}, else the first one with a nested jar of that id */
    private Optional<Path> jarProviding(final String modId) throws IOException {
        final List<Path> jars = enabledJars();
        for (Path jar : jars) {
            if (FabricModInfo.read(jar).map(FabricModInfo::id).filter(modId::equals).isPresent()) {
                return Optional.of(jar);
            }
        }
        for (Path jar : jars) {
            if (FabricModInfo.providedIds(jar).contains(modId)) {
                return Optional.of(jar);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- shared

    /** @return enabled {@code mods/*.jar} in name order */
    private List<Path> enabledJars() throws IOException {
        final Path mods = paths.modsDir();
        if (!Files.isDirectory(mods)) {
            return List.of();
        }
        final List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    jars.add(p);
                }
            }
        }
        jars.sort(null);
        return jars;
    }

    private Action switchOff(final Path jar, final String modId, final String name, final String version, final String reason,
                             final Source source, final String report) {
        final String label = version.isEmpty() ? name : name + " " + version;
        try {
            modrinth.setEnabled(contentFor(jar), false);
            LOG.log(Level.FINE, "Switched off {0} ({1}): {2}", new Object[] {jar.getFileName(), label, reason});
            return new Action(jar, modId, name, version, reason, source, true, report);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not switch off " + jar.getFileName() + " (" + label + ")", e);
            return new Action(jar, modId, name, version, reason + "; it could not be switched off: " + e.getMessage(), source, false, report);
        }
    }

    /** @return the jar as the Mods page lists it (tracked in modrinth.json or not) */
    private ModrinthService.InstalledContent contentFor(final Path jar) throws IOException {
        final Path normal = jar.toAbsolutePath().normalize();
        for (ModrinthService.InstalledContent c : modrinth.installed()) {
            if (c.type() == ContentType.MOD && c.path().toAbsolutePath().normalize().equals(normal)) {
                return c;
            }
        }
        return new ModrinthService.InstalledContent(ContentType.MOD, jar.getFileName().toString(), "", jar, true, false, false, "", List.of(), false);
    }

    /** @return size and modification time: a jar that changed is checked again */
    private static String stamp(final Path jar) throws IOException {
        return CHECK_VERSION + ":" + Files.size(jar) + ":" + Files.getLastModifiedTime(jar).toMillis();
    }

    /**
     * {@code startup-check.json}: {@code {"handledCrashReports":["crash-....txt"],"passed":{"x.jar":"1:size:mtime"}}}.
     * Unreadable content is treated as empty; a failed write only costs a repeated check.
     */
    private static final class State {
        private final Path file;
        final Set<String> handled = new LinkedHashSet<>();
        final Map<String, String> passed = new LinkedHashMap<>();

        private State(final Path file) {
            this.file = file;
        }

        static State load(final Path file) {
            final State s = new State(file);
            if (!Files.isRegularFile(file)) {
                return s;
            }
            try {
                final JsonElement tree = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (tree.isJsonObject()) {
                    final JsonObject o = tree.getAsJsonObject();
                    if (o.get("handledCrashReports") instanceof JsonArray a) {
                        a.forEach(e -> {
                            if (e.isJsonPrimitive()) {
                                s.handled.add(e.getAsString());
                            }
                        });
                    }
                    if (o.get("passed") instanceof JsonObject p) {
                        p.entrySet().forEach(e -> {
                            if (e.getValue().isJsonPrimitive()) {
                                s.passed.put(e.getKey(), e.getValue().getAsString());
                            }
                        });
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.FINE, "Unreadable {0}; starting empty", file);
            }
            return s;
        }

        void saveQuietly() {
            final JsonObject o = new JsonObject();
            o.addProperty("schemaVersion", 1);
            final JsonArray handledArray = new JsonArray();
            final List<String> recent = new ArrayList<>(handled);
            recent.subList(0, Math.max(0, recent.size() - MAX_HANDLED_REPORTS)).clear();
            recent.forEach(handledArray::add);
            o.add("handledCrashReports", handledArray);
            final JsonObject passedObject = new JsonObject();
            passed.forEach(passedObject::addProperty);
            o.add("passed", passedObject);
            try {
                AtomicFiles.writeString(file, Json.treeToJson(o) + System.lineSeparator());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not write " + file, e);
            }
        }
    }
}

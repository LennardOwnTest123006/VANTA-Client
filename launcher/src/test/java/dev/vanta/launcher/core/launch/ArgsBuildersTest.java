package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgsBuildersTest {

    private static final VersionJson VANILLA = Json.parse(Fixtures.read("1.21.11.json"), VersionJson.class);
    private static final VersionJson MERGED = Json.parse(Fixtures.read("fabric-profile-0.19.5.json"), FabricProfileJson.class)
        .toVersionJson().mergeOnto(VANILLA);

    private static Map<String, String> values() {
        final Map<String, String> v = new HashMap<>();
        v.put("natives_directory", "/data/instances/vanta-1.21.11/natives");
        v.put("launcher_name", "VANTA-Launcher");
        v.put("launcher_version", "1.0.0");
        v.put("classpath", "/a.jar:/b.jar");
        v.put("auth_player_name", "Steve");
        v.put("version_name", "vanta-1.21.11");
        v.put("game_directory", "/data/instances/vanta-1.21.11");
        v.put("assets_root", "/data/assets");
        v.put("assets_index_name", "26");
        v.put("auth_uuid", "3f2a1b4c5d6e7f8091a2b3c4d5e6f708");
        v.put("auth_access_token", "secret-token");
        v.put("clientid", "VANTA-Launcher/1.0.0");
        v.put("auth_xuid", "2535412345678901");
        v.put("user_type", "msa");
        v.put("version_type", "release");
        v.put("resolution_width", "1600");
        v.put("resolution_height", "900");
        v.put("quickPlaySingleplayer", "My World");
        return v;
    }

    @Test
    void jvmArgsOnLinuxContainHeapGcEncodingClasspathAndNoPlatformSpecificFlags() {
        final OsInfo linux = new OsInfo("linux", "x64", "6.8");
        final List<String> args = new JvmArgsBuilder(new RuleEvaluator(linux), "1.0.0")
            .build(MERGED, new ArgumentExpander(values()), 4096, List.of());
        assertEquals("-Xmx4096M", args.get(0));
        assertTrue(args.contains("-Xms1024M"));
        assertTrue(args.contains("-XX:+UseG1GC"));
        assertTrue(args.contains("-Dfile.encoding=UTF-8"));
        assertTrue(args.contains("-Dvanta.launcher=1.0.0"));
        assertTrue(args.contains("-Dvanta.launcher.restartable=true"), "the launcher can restart a game it started");
        assertTrue(args.contains("-Djava.library.path=/data/instances/vanta-1.21.11/natives"));
        assertTrue(args.contains("-Dminecraft.launcher.brand=VANTA-Launcher"));
        assertTrue(args.contains("-DFabricMcEmu= net.minecraft.client.main.Main "));
        final int cp = args.indexOf("-cp");
        assertTrue(cp > 0);
        assertEquals("/a.jar:/b.jar", args.get(cp + 1));
        assertFalse(args.contains("-XstartOnFirstThread"));
        assertFalse(args.stream().anyMatch(a -> a.startsWith("-XX:HeapDumpPath")));
        assertFalse(args.contains("-Xss1M"));
        assertEquals(1, args.stream().filter(a -> a.equals("-cp")).count());
    }

    @Test
    void jvmArgsPerPlatform() {
        final List<String> mac = new JvmArgsBuilder(new RuleEvaluator(new OsInfo("osx", "arm64", "14")), "1.0.0")
            .build(MERGED, new ArgumentExpander(values()), 2048, List.of());
        assertTrue(mac.contains("-XstartOnFirstThread"));
        final List<String> win = new JvmArgsBuilder(new RuleEvaluator(new OsInfo("windows", "x64", "10.0")), "1.0.0")
            .build(MERGED, new ArgumentExpander(values()), 2048, List.of());
        assertTrue(win.stream().anyMatch(a -> a.startsWith("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance")));
        assertFalse(win.contains("-Xss1M"));
        final List<String> win32 = new JvmArgsBuilder(new RuleEvaluator(new OsInfo("windows", "x86", "10.0")), "1.0.0")
            .build(MERGED, new ArgumentExpander(values()), 1024, List.of());
        assertTrue(win32.contains("-Xss1M"));
    }

    @Test
    void userArgsWinAndDisableGcDefaults() {
        final List<String> args = new JvmArgsBuilder(new RuleEvaluator(new OsInfo("linux", "x64", "")), "1.0.0")
            .build(MERGED, new ArgumentExpander(values()), 3072, List.of("-XX:+UseZGC", "-Dfile.encoding=ISO-8859-1", "-Xms2048M"));
        assertFalse(args.contains("-XX:+UseG1GC"));
        assertTrue(args.contains("-XX:+UseZGC"));
        assertEquals(1, args.stream().filter(a -> a.startsWith("-Dfile.encoding=")).count());
        assertTrue(args.contains("-Dfile.encoding=ISO-8859-1"));
        assertEquals(1, args.stream().filter(a -> a.startsWith("-Xms")).count());
        assertTrue(args.contains("-Xms2048M"));
    }

    @Test
    void legacyVersionWithoutJvmArgumentsGetsClasspathAndNatives() {
        final VersionJson legacy = Json.parse("{\"id\":\"1.8.9\",\"mainClass\":\"net.minecraft.client.main.Main\","
            + "\"minecraftArguments\":\"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory}\"}", VersionJson.class);
        final List<String> jvm = new JvmArgsBuilder(new RuleEvaluator(new OsInfo("linux", "x64", "")), "1.0.0")
            .build(legacy, new ArgumentExpander(values()), 2048, List.of());
        assertTrue(jvm.contains("-cp"));
        assertTrue(jvm.contains("-Djava.library.path=/data/instances/vanta-1.21.11/natives"));
        final List<String> game = new GameArgsBuilder(new RuleEvaluator(new OsInfo("linux", "x64", ""), Map.of()))
            .build(legacy, new ArgumentExpander(values()));
        assertEquals(List.of("--username", "Steve", "--version", "vanta-1.21.11", "--gameDir", "/data/instances/vanta-1.21.11"), game);
    }

    @Test
    void gameArgsWithoutFeatures() {
        final List<String> args = new GameArgsBuilder(new RuleEvaluator(new OsInfo("linux", "x64", ""), Map.of()))
            .build(MERGED, new ArgumentExpander(values()));
        assertEquals(List.of("--username", "Steve", "--version", "vanta-1.21.11", "--gameDir", "/data/instances/vanta-1.21.11",
            "--assetsDir", "/data/assets", "--assetIndex", "26", "--uuid", "3f2a1b4c5d6e7f8091a2b3c4d5e6f708",
            "--accessToken", "secret-token", "--clientId", "VANTA-Launcher/1.0.0", "--xuid", "2535412345678901", "--userType", "msa", "--versionType", "release"), args);
    }

    @Test
    void gameArgsWithResolutionAndQuickPlay() {
        final Map<String, Boolean> features = Map.of(GameArgsBuilder.FEATURE_RESOLUTION, true, GameArgsBuilder.FEATURE_QUICK_PLAY_SINGLEPLAYER, true);
        final List<String> args = new GameArgsBuilder(new RuleEvaluator(new OsInfo("windows", "x64", ""), features))
            .build(MERGED, new ArgumentExpander(values()));
        assertTrue(args.containsAll(List.of("--width", "1600", "--height", "900", "--quickPlaySingleplayer", "My World")));
        assertFalse(args.contains("--demo"));
        assertFalse(args.contains("--quickPlayPath"));
        assertFalse(args.contains("--quickPlayMultiplayer"));
    }

    @Test
    void demoFeatureAddsDemoFlag() {
        final List<String> args = new GameArgsBuilder(new RuleEvaluator(new OsInfo("linux", "x64", ""), Map.of(GameArgsBuilder.FEATURE_DEMO, true)))
            .build(MERGED, new ArgumentExpander(values()));
        assertTrue(args.contains("--demo"));
    }
}

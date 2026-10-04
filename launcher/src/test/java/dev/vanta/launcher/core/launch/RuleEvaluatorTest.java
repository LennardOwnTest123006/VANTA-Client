package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Rule;
import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluatorTest {

    private static final OsInfo WIN_X64 = new OsInfo("windows", "x64", "10.0");
    private static final OsInfo WIN_X86 = new OsInfo("windows", "x86", "10.0");
    private static final OsInfo WIN_ARM = new OsInfo("windows", "arm64", "10.0");
    private static final OsInfo MAC_ARM = new OsInfo("osx", "arm64", "14.5");
    private static final OsInfo MAC_X64 = new OsInfo("osx", "x64", "13.6");
    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8.0");

    private static Rule allow(final String os, final String arch) {
        return new Rule("allow", new Rule.Os(os, arch, null), null);
    }

    private static Rule disallow(final String os) {
        return new Rule("disallow", new Rule.Os(os, null, null), null);
    }

    @Test
    void noRulesAllows() {
        assertTrue(new RuleEvaluator(LINUX).allows(List.of()));
        assertTrue(new RuleEvaluator(LINUX).allows(null));
    }

    @ParameterizedTest
    @CsvSource({
        "windows, x64, true", "windows, x86, true", "windows, arm64, true", "osx, arm64, false", "linux, x64, false"
    })
    void osNameRule(final String os, final String arch, final boolean expected) {
        final RuleEvaluator e = new RuleEvaluator(new OsInfo(os, arch, "1"));
        assertEquals(expected, e.allows(List.of(allow("windows", null))));
    }

    @Test
    void archRuleMatrix() {
        final List<Rule> winArm = List.of(allow("windows", "arm64"));
        assertTrue(new RuleEvaluator(WIN_ARM).allows(winArm));
        assertFalse(new RuleEvaluator(WIN_X64).allows(winArm));
        assertFalse(new RuleEvaluator(MAC_ARM).allows(winArm));

        final List<Rule> x86Only = List.of(new Rule("allow", new Rule.Os(null, "x86", null), null));
        assertTrue(new RuleEvaluator(WIN_X86).allows(x86Only));
        assertFalse(new RuleEvaluator(WIN_X64).allows(x86Only));
        assertFalse(new RuleEvaluator(LINUX).allows(x86Only));

        final List<Rule> macArm = List.of(allow("osx", "arm64"));
        assertTrue(new RuleEvaluator(MAC_ARM).allows(macArm));
        assertFalse(new RuleEvaluator(MAC_X64).allows(macArm));
    }

    @Test
    void lastMatchingRuleWins() {
        // Classic Mojang pattern: allow everything, disallow osx
        final List<Rule> rules = List.of(new Rule("allow", null, null), disallow("osx"));
        assertTrue(new RuleEvaluator(LINUX).allows(rules));
        assertTrue(new RuleEvaluator(WIN_X64).allows(rules));
        assertFalse(new RuleEvaluator(MAC_ARM).allows(rules));
    }

    @Test
    void noMatchingRuleDisallows() {
        assertFalse(new RuleEvaluator(LINUX).allows(List.of(allow("osx", null))));
    }

    @Test
    void versionRegex() {
        final List<Rule> win10 = List.of(new Rule("allow", new Rule.Os("windows", null, "^10\\."), null));
        assertTrue(new RuleEvaluator(new OsInfo("windows", "x64", "10.0")).allows(win10));
        assertFalse(new RuleEvaluator(new OsInfo("windows", "x64", "6.1")).allows(win10));
        final List<Rule> broken = List.of(new Rule("allow", new Rule.Os("windows", null, "("), null));
        assertFalse(new RuleEvaluator(WIN_X64).allows(broken), "invalid regex never matches");
    }

    @Test
    void featureRules() {
        final Rule demo = new Rule("allow", null, Map.of("is_demo_user", true));
        final Rule resolution = new Rule("allow", null, Map.of("has_custom_resolution", true));
        final Rule notDemo = new Rule("allow", null, Map.of("is_demo_user", false));

        final RuleEvaluator none = new RuleEvaluator(LINUX, Map.of());
        assertFalse(none.allows(List.of(demo)));
        assertFalse(none.allows(List.of(resolution)));
        assertTrue(none.allows(List.of(notDemo)), "missing feature counts as false");

        final RuleEvaluator withResolution = new RuleEvaluator(LINUX, Map.of("has_custom_resolution", true));
        assertTrue(withResolution.allows(List.of(resolution)));
        assertFalse(withResolution.allows(List.of(demo)));

        final Rule both = new Rule("allow", null, Map.of("has_custom_resolution", true, "is_demo_user", true));
        assertFalse(withResolution.allows(List.of(both)), "all features must match");
    }

    @Test
    void osInfoNormalisation() {
        assertEquals(new OsInfo("windows", "x64", "10.0"), OsInfo.fromProperties("Windows 11", "amd64", "10.0"));
        assertEquals(new OsInfo("osx", "arm64", "14.5"), OsInfo.fromProperties("Mac OS X", "aarch64", "14.5"));
        assertEquals(new OsInfo("linux", "x64", "6.8"), OsInfo.fromProperties("Linux", "x86_64", "6.8"));
        assertEquals("x86", OsInfo.normaliseArch("i386"));
        assertEquals("32", new OsInfo("windows", "x86", "").bits());
        assertEquals("64", LINUX.bits());
        assertEquals("mac", MAC_ARM.adoptiumOs());
        assertEquals("aarch64", MAC_ARM.adoptiumArch());
        assertEquals("x64", LINUX.adoptiumArch());
        assertEquals(";", WIN_X64.classpathSeparator());
        assertEquals("java.exe", WIN_X64.javaExecutableName());
    }
}

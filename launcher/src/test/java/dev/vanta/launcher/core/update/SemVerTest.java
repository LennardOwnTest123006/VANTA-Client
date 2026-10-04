package dev.vanta.launcher.core.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerTest {

    @Test
    void parsesAndPrints() {
        final SemVer v = SemVer.parse("v1.2.3-beta.2+build.7");
        assertEquals(1, v.major());
        assertEquals(2, v.minor());
        assertEquals(3, v.patch());
        assertEquals(List.of("beta", "2"), v.preRelease());
        assertEquals("build.7", v.build());
        assertTrue(v.isPreRelease());
        assertEquals("1.2.3-beta.2+build.7", v.toString());
        assertEquals("1.0.0", SemVer.of(1, 0, 0).toString());
    }

    @Test
    void rejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("01.2.3"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("abc"));
        assertTrue(SemVer.tryParse("nope").isEmpty());
        assertTrue(SemVer.tryParse(null).isEmpty());
        assertTrue(SemVer.tryParse("1.0.0").isPresent());
    }

    @Test
    void precedenceFollowsSemver2() {
        final List<String> ordered = List.of("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
            "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0");
        for (int i = 1; i < ordered.size(); i++) {
            final SemVer lower = SemVer.parse(ordered.get(i - 1));
            final SemVer higher = SemVer.parse(ordered.get(i));
            assertTrue(higher.isNewerThan(lower), higher + " > " + lower);
            assertFalse(lower.isNewerThan(higher));
        }
        assertEquals(0, SemVer.parse("1.0.0+a").compareTo(SemVer.parse("1.0.0+b")), "build metadata ignored for precedence");
        assertFalse(SemVer.parse("1.0.0+a").equals(SemVer.parse("1.0.0+b")));
        assertEquals(SemVer.parse("1.0.0"), SemVer.parse("v1.0.0"));
        assertEquals(SemVer.parse("1.0.0").hashCode(), SemVer.parse("1.0.0").hashCode());
    }
}

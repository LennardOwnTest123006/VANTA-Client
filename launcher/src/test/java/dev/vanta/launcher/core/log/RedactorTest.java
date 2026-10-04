package dev.vanta.launcher.core.log;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactorTest {

    @Test
    void registeredSecretsAreReplaced() {
        final Redactor r = new Redactor();
        r.register("s3cr3t-token-value");
        assertEquals("token=[redacted] end", r.apply("token=s3cr3t-token-value end"));
        assertEquals(List.of("--accessToken", "[redacted]"), r.apply(List.of("--accessToken", "s3cr3t-token-value")));
    }

    @Test
    void shortSecretsAreIgnored() {
        final Redactor r = new Redactor();
        r.register("0");
        r.register("   ");
        assertFalse(r.hasSecrets());
        assertEquals("0 stays", r.apply("0 stays"));
    }

    @Test
    void patternsMaskUnregisteredTokens() {
        final Redactor r = new Redactor();
        assertEquals("java --accessToken [redacted] --uuid abc", r.apply("java --accessToken eyJhbGciOi.payload.sig --uuid abc"));
        assertEquals("{\"access_token\": \"[redacted]\", \"expires_in\": 86400}", r.apply("{\"access_token\": \"abcDEF123\", \"expires_in\": 86400}"));
        assertEquals("Authorization: Bearer [redacted]", r.apply("Authorization: Bearer abc.def.ghi"));
        assertEquals("identity XBL3.0 x=[redacted]", r.apply("identity XBL3.0 x=123456;eyJ.token"));
        assertEquals("\"Token\":\"[redacted]\"", r.apply("\"Token\":\"eyJlbmMiOiJB.XBL\""));
        assertEquals("ticket d=[redacted] end", r.apply("ticket d=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9 end"));
    }

    @Test
    void jwtShapedValuesAreMaskedEverywhere() {
        final Redactor r = new Redactor();
        final String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertEquals("token [redacted] here", r.apply("token " + jwt + " here"));
    }

    @Test
    void formatterRedactsMessagesAndStackTraces() {
        Redactor.global().register("global-secret-xyz");
        final LauncherLog.RedactingFormatter formatter = new LauncherLog.RedactingFormatter();
        final LogRecord record = new LogRecord(Level.INFO, "launching with global-secret-xyz");
        record.setLoggerName("VANTA.Test");
        record.setThrown(new IllegalStateException("boom global-secret-xyz"));
        final String text = formatter.format(record);
        assertFalse(text.contains("global-secret-xyz"));
        assertTrue(text.contains("[redacted]"));
        assertTrue(text.contains("INFO [VANTA.Test]"));
        Redactor.global().unregister("global-secret-xyz");
    }
}

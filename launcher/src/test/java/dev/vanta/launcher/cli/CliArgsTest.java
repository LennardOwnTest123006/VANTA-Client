package dev.vanta.launcher.cli;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgsTest {

    @Test
    void parsesCommandOptionsAndFlags() {
        final CliArgs args = CliArgs.parse(new String[] {"--install", "--client-jar", "/tmp/vanta.jar", "--data-dir=/data", "--no-assets"});
        assertTrue(args.isValid(), args.errors().toString());
        assertEquals(CliCommand.INSTALL, args.command());
        assertEquals(Optional.of("/tmp/vanta.jar"), args.option("client-jar"));
        assertEquals(Optional.of("/data"), args.option("data-dir"));
        assertTrue(args.has("no-assets"));
        assertFalse(args.has("dev-offline"));
    }

    @Test
    void launchWithDevOffline() {
        final CliArgs args = CliArgs.parse(new String[] {"--dev-offline", "--username", "CI", "--launch", "--exit-after", "10"});
        assertTrue(args.isValid());
        assertEquals(CliCommand.LAUNCH, args.command());
        assertTrue(args.has("dev-offline"));
        assertEquals(Optional.of("CI"), args.option("username"));
        assertEquals(Optional.of("10"), args.option("exit-after"));
    }

    @Test
    void shortAliasesAndHelpDefault() {
        assertEquals(CliCommand.VERSION, CliArgs.parse(new String[] {"-v"}).command());
        assertEquals(CliCommand.HELP, CliArgs.parse(new String[] {"-h"}).command());
        final CliArgs empty = CliArgs.parse(new String[0]);
        assertEquals(CliCommand.HELP, empty.command());
        assertTrue(empty.isValid());
    }

    @Test
    void errors() {
        assertEquals("Unknown option '--bogus'", CliArgs.parse(new String[] {"--version", "--bogus"}).errors().get(0));
        assertEquals("Option --client-jar requires a value", CliArgs.parse(new String[] {"--install", "--client-jar"}).errors().get(0));
        assertEquals("Option --client-jar requires a value", CliArgs.parse(new String[] {"--install", "--client-jar", "--no-assets"}).errors().get(0));
        assertTrue(CliArgs.parse(new String[] {"--install", "--launch"}).errors().get(0).startsWith("Only one command"));
        assertTrue(CliArgs.parse(new String[] {"--no-assets"}).errors().get(0).startsWith("No command given"));
        assertEquals("Unexpected argument 'stray'", CliArgs.parse(new String[] {"--version", "stray"}).errors().get(0));
        assertEquals("Flag --no-assets does not take a value", CliArgs.parse(new String[] {"--install", "--no-assets=true"}).errors().get(0));
        assertTrue(CliArgs.parse(new String[] {"--install", "--install"}).isValid(), "repeating the same command is tolerated");
    }

    @Test
    void commandLookupAndExitCodes() {
        assertEquals(Optional.of(CliCommand.CHECK_JAVA), CliCommand.fromFlag("--check-java"));
        assertEquals(Optional.empty(), CliCommand.fromFlag("--nope"));
        assertEquals(0, ExitCode.OK.code());
        assertEquals(2, ExitCode.USAGE.code());
        assertEquals(6, ExitCode.JAVA_NOT_FOUND.code());
        assertEquals(7, ExitCode.NOT_PUBLISHED.code());
        assertEquals(10, ExitCode.GAME_FAILED.code());
        for (ExitCode c : ExitCode.values()) {
            assertFalse(c.description().isBlank());
        }
    }
}

package dev.vanta.launcher.cli;

import java.util.Optional;

/**
 * CLI commands (mutually exclusive flags).
 */
public enum CliCommand {
    /** Install Minecraft, Fabric and the VANTA client. */
    INSTALL("--install", "Install Minecraft, Fabric Loader, Fabric API, VANTA Client and the performance pack"),
    /** Set up VANTA as a profile of the official Minecraft Launcher. */
    INSTALL_OFFICIAL_PROFILE("--install-official-profile",
        "Install Fabric API + VANTA Client (+ performance pack) and add the profile 'VANTA <version>' to the official Minecraft Launcher"),
    /** Start the official Minecraft Launcher. */
    OPEN_OFFICIAL_LAUNCHER("--open-official-launcher",
        "Start the official Minecraft Launcher (play VANTA there with the profile 'VANTA <version>')"),
    /** Launch the game. */
    LAUNCH("--launch", "Launch the installed instance"),
    /** Detect Java runtimes. */
    CHECK_JAVA("--check-java", "List detected Java runtimes and the one that would be used"),
    /** Install a Temurin runtime. */
    INSTALL_JAVA("--install-java", "Download and install Eclipse Temurin 21 (verified) into the launcher directory"),
    /** Check for updates. */
    CHECK_UPDATE("--check-update", "Check the release manifests for launcher and client updates"),
    /** Install the Local AI (llama-server runtime and model). */
    INSTALL_LOCAL_AI("--install-local-ai",
        "Download and install the Local AI (llama-server from llama.cpp and the Qwen3 GGUF model, SHA-256 verified) into local-ai/"),
    /** Report the Local AI install state. */
    LOCAL_AI_STATUS("--local-ai-status", "Show whether the Local AI is installed, its versions, folder and size on disk"),
    /** Remove the Local AI folder. */
    REMOVE_LOCAL_AI("--remove-local-ai", "Delete the Local AI folder (runtime and model) and switch its automatic installation off"),
    /** Print the launch command. */
    PRINT_COMMAND("--print-command", "Print the game command line (secrets redacted) without launching"),
    /** Print the version. */
    VERSION("--version", "Print the launcher version"),
    /** Print usage. */
    HELP("--help", "Show this help");

    private final String flag;
    private final String description;

    CliCommand(final String flag, final String description) {
        this.flag = flag;
        this.description = description;
    }

    /** @return the command line flag */
    public String flag() {
        return flag;
    }

    /** @return description for the help text */
    public String description() {
        return description;
    }

    /**
     * @param flag flag text
     * @return command for the flag
     */
    public static Optional<CliCommand> fromFlag(final String flag) {
        for (CliCommand c : values()) {
            if (c.flag.equals(flag)) {
                return Optional.of(c);
            }
        }
        if ("-v".equals(flag) || "-V".equals(flag)) {
            return Optional.of(VERSION);
        }
        if ("-h".equals(flag)) {
            return Optional.of(HELP);
        }
        return Optional.empty();
    }
}

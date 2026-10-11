package dev.vanta.core.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one assistant turn produced.
 *
 * @param message  the assistant's text for the player (empty when {@link #error()} is set)
 * @param applied  changes made, in order
 * @param rejected proposed changes that validation refused
 * @param undoable true when {@link NexusUndo#undoLast()} would revert this turn
 * @param error    why no answer arrived (server, network, unparseable reply)
 * @param source   who answered: the Local AI model, or the instant commands without the model (from client 1.6.0 on)
 */
public record NexusReply(String message, List<AppliedAction> applied, List<RejectedAction> rejected, boolean undoable,
                         Optional<LocalAiException> error, Source source) {
    /** Who answered a turn. */
    public enum Source {
        /** The Local AI model (llama-server). */
        MODEL,
        /** {@link NexusCommands}: understood and applied at once, without the model. */
        COMMANDS
    }

    public NexusReply {
        Objects.requireNonNull(message, "message");
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(source, "source");
    }

    /** A turn answered by the model (or failed on the way to it). */
    public NexusReply(String message, List<AppliedAction> applied, List<RejectedAction> rejected, boolean undoable,
                      Optional<LocalAiException> error) {
        this(message, applied, rejected, undoable, error, Source.MODEL);
    }

    /** A reply without an answer. */
    public static NexusReply failed(LocalAiException error) {
        return new NexusReply("", List.of(), List.of(), false, Optional.of(error));
    }

    /** True when the turn failed before anything could be applied. */
    public boolean isError() {
        return error.isPresent();
    }

    /** True when the instant commands answered, without the model. */
    public boolean instant() {
        return source == Source.COMMANDS;
    }
}

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
 */
public record NexusReply(String message, List<AppliedAction> applied, List<RejectedAction> rejected, boolean undoable,
                         Optional<LocalAiException> error) {
    public NexusReply {
        Objects.requireNonNull(message, "message");
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
        Objects.requireNonNull(error, "error");
    }

    /** A reply without an answer. */
    public static NexusReply failed(LocalAiException error) {
        return new NexusReply("", List.of(), List.of(), false, Optional.of(error));
    }

    /** True when the turn failed before anything could be applied. */
    public boolean isError() {
        return error.isPresent();
    }
}

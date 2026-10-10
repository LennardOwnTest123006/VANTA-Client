package dev.vanta.core.timer;

import java.util.Locale;
import java.util.Objects;

/**
 * Parses the arguments of the client command {@code /vanta timer ...}:
 * <ul>
 *   <li>{@code <minutes> [label]}: whole minutes from 1 to 1440 ({@code /vanta timer 10 Tea});</li>
 *   <li>{@code <m:ss> [label]}: minutes and seconds ({@code /vanta timer 2:30});</li>
 *   <li>{@code stop} or {@code cancel}: stops a ringing alarm or cancels a running or paused timer.</li>
 * </ul>
 * {@link #run(TimerService)} applies a parsed command; the client only turns the result into chat feedback.
 */
public final class TimerCommand {
    /** Error key: the first word is not a duration. */
    public static final String INVALID = "vanta.command.timer.invalid";

    private TimerCommand() {
    }

    /** A parsed command. */
    public sealed interface Parsed permits Start, Stop, Invalid {
    }

    /**
     * Start a timer.
     *
     * @param millis duration in milliseconds
     * @param label  label (possibly empty)
     */
    public record Start(long millis, String label) implements Parsed {
        public Start {
            label = TimerService.cleanLabel(label);
        }
    }

    /** Stop the alarm or cancel the timer. */
    public record Stop() implements Parsed {
    }

    /**
     * Not understood.
     *
     * @param errorKey translation key of the reason
     */
    public record Invalid(String errorKey) implements Parsed {
        public Invalid {
            Objects.requireNonNull(errorKey, "errorKey");
        }
    }

    /** What running a command did. */
    public enum Outcome {
        /** A timer was started. */
        STARTED,
        /** A ringing alarm was stopped. */
        STOPPED,
        /** A running or paused timer was cancelled. */
        CANCELLED,
        /** {@code stop} without a timer. */
        NOTHING_TO_STOP
    }

    /** Parses the text after {@code /vanta timer }. */
    public static Parsed parse(String arguments) {
        String text = arguments == null ? "" : arguments.trim();
        if (text.isEmpty()) {
            return new Invalid(INVALID);
        }
        int space = indexOfWhitespace(text);
        String first = space < 0 ? text : text.substring(0, space);
        String rest = space < 0 ? "" : text.substring(space + 1).trim();
        String word = first.toLowerCase(Locale.ROOT);
        if ((word.equals("stop") || word.equals("cancel")) && rest.isEmpty()) {
            return new Stop();
        }
        TimerFormats.Duration duration;
        int colon = first.indexOf(':');
        if (colon >= 0) {
            if (first.indexOf(':', colon + 1) >= 0 || colon == 0 || colon == first.length() - 1) {
                return new Invalid(INVALID);
            }
            String seconds = first.substring(colon + 1);
            if (seconds.length() != 2) {
                return new Invalid(INVALID);
            }
            duration = TimerFormats.parseDuration(first.substring(0, colon), seconds);
        } else {
            duration = TimerFormats.parseDuration(first, "");
        }
        if (!duration.ok()) {
            return new Invalid(duration.errorKey().equals(TimerFormats.Duration.NOT_A_NUMBER) ? INVALID
                    : duration.errorKey());
        }
        return new Start(duration.millis(), rest);
    }

    /**
     * Applies a {@link Start} or {@link Stop} to the timer.
     *
     * @throws IllegalArgumentException for {@link Invalid}
     */
    public static Outcome run(Parsed command, TimerService timer) {
        Objects.requireNonNull(timer, "timer");
        if (command instanceof Start start) {
            if (!timer.start(start.millis(), start.label())) {
                throw new IllegalArgumentException("duration out of range: " + start.millis());
            }
            return Outcome.STARTED;
        }
        if (command instanceof Stop) {
            if (timer.isRinging()) {
                timer.stop();
                return Outcome.STOPPED;
            }
            return timer.cancel() ? Outcome.CANCELLED : Outcome.NOTHING_TO_STOP;
        }
        throw new IllegalArgumentException("not a runnable timer command: " + command);
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}

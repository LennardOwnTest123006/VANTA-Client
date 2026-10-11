package dev.vanta.core.timer;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parses the arguments of the client command {@code /vanta timer ...}:
 * <ul>
 *   <li>{@code <minutes> [label]}: whole minutes from 1 to 1440 ({@code /vanta timer 10 Tea});</li>
 *   <li>{@code <m:ss> [label]}: minutes and seconds ({@code /vanta timer 2:30});</li>
 *   <li>{@code stop} or {@code cancel}: stops a ringing alarm or cancels a running or paused timer.</li>
 * </ul>
 * {@link #run(Parsed, TimerService)} applies a parsed command and {@link #execute(String, TimerService, ZoneId)}
 * does both and names the chat line to print, so the client only sends that line.
 */
public final class TimerCommand {
    /** Error key: the first word is not a duration. */
    public static final String INVALID = "vanta.command.timer.invalid";
    /** Feedback key: a timer without a label started ({@code duration}, {@code rings at}). */
    public static final String STARTED = "vanta.command.timer.started";
    /** Feedback key: a labelled timer started ({@code label}, {@code duration}, {@code rings at}). */
    public static final String STARTED_LABELLED = "vanta.command.timer.started_labelled";
    /** Feedback key: a ringing alarm was stopped. */
    public static final String STOPPED = "vanta.command.timer.stopped";
    /** Feedback key: a running or paused timer was cancelled. */
    public static final String CANCELLED = "vanta.command.timer.cancelled";
    /** Feedback key: {@code stop} without a timer. */
    public static final String NOTHING = "vanta.command.timer.nothing";

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

    /**
     * The chat line {@code /vanta timer <arguments>} answers with.
     *
     * @param key   translation key of the line
     * @param args  its format arguments (already formatted text)
     * @param error true for a red error line (input not understood), false for plain feedback
     */
    public record Reply(String key, List<String> args, boolean error) {
        public Reply {
            Objects.requireNonNull(key, "key");
            args = List.copyOf(args);
        }

        /** The arguments for {@code Lang.tr(key, args)}. */
        public Object[] argsArray() {
            return args.toArray();
        }
    }

    /**
     * Parses and runs {@code /vanta timer <arguments>} and names the chat line to print: the duration and the time of
     * day the timer rings (12-hour time with AM/PM in {@code zone}, the PC's time zone in the game) after a start,
     * what was stopped or cancelled, or why the input was not understood (nothing changes then).
     */
    public static Reply execute(String arguments, TimerService timer, ZoneId zone) {
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(zone, "zone");
        Parsed parsed = parse(arguments);
        if (parsed instanceof Invalid invalid) {
            return new Reply(invalid.errorKey(), List.of(), true);
        }
        return switch (run(parsed, timer)) {
            case STARTED -> {
                String duration = TimerFormats.remaining(timer.durationMillis());
                String ringsAt = TimerFormats.timeOfDay(timer.endsAt().orElseThrow(), zone);
                String label = timer.label();
                yield label.isEmpty()
                        ? new Reply(STARTED, List.of(duration, ringsAt), false)
                        : new Reply(STARTED_LABELLED, List.of(label, duration, ringsAt), false);
            }
            case STOPPED -> new Reply(STOPPED, List.of(), false);
            case CANCELLED -> new Reply(CANCELLED, List.of(), false);
            case NOTHING_TO_STOP -> new Reply(NOTHING, List.of(), false);
        };
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

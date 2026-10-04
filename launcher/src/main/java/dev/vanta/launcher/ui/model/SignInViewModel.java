package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.DeviceCode;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Device code sign-in: request a code, show it, poll until approval, complete the Xbox/Minecraft chain.
 */
public final class SignInViewModel {

    /** Dialog state. */
    public enum State {
        /** Nothing started. */
        IDLE,
        /** Requesting a code. */
        STARTING,
        /** Code displayed, waiting for approval in the browser. */
        WAITING,
        /** Signed in. */
        DONE,
        /** Failed; see {@link #errorTextProperty()}. */
        FAILED,
        /** Microsoft client id missing. */
        NOT_CONFIGURED
    }

    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final ErrorMessages errors;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.IDLE);
    private final StringProperty userCode = new SimpleStringProperty("");
    private final StringProperty verificationUri = new SimpleStringProperty("");
    private final StringProperty statusText = new SimpleStringProperty("");
    private final StringProperty countdownText = new SimpleStringProperty("");
    private final StringProperty errorText = new SimpleStringProperty("");
    private final BooleanProperty expired = new SimpleBooleanProperty(false);
    private final ReadOnlyObjectWrapper<Account> account = new ReadOnlyObjectWrapper<>();

    private DeviceCode code;
    private CancellationToken token;
    private Future<?> running;
    private Consumer<Account> onSignedIn = a -> { };

    /**
     * @param backend   backend
     * @param executors executors
     * @param messages  messages
     * @param formats   formats
     */
    public SignInViewModel(final LauncherBackend backend, final UiExecutors executors, final Messages messages, final Formats formats) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.errors = new ErrorMessages(messages, formats);
    }

    /** @return state */
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /** @return state */
    public State state() {
        return state.get();
    }

    /** @return the code the user types */
    public ReadOnlyStringProperty userCodeProperty() {
        return userCode;
    }

    /** @return verification page */
    public ReadOnlyStringProperty verificationUriProperty() {
        return verificationUri;
    }

    /** @return status line */
    public ReadOnlyStringProperty statusTextProperty() {
        return statusText;
    }

    /** @return "expires in m:ss" */
    public ReadOnlyStringProperty countdownTextProperty() {
        return countdownText;
    }

    /** @return error text when failed */
    public ReadOnlyStringProperty errorTextProperty() {
        return errorText;
    }

    /** @return whether the code expired */
    public ReadOnlyBooleanProperty expiredProperty() {
        return expired;
    }

    /** @return signed-in account when done */
    public ReadOnlyObjectProperty<Account> accountProperty() {
        return account.getReadOnlyProperty();
    }

    /**
     * @param hook runs on the UI thread with the account after a successful sign-in
     */
    public void onSignedIn(final Consumer<Account> hook) {
        this.onSignedIn = Objects.requireNonNull(hook, "hook");
    }

    /** Starts (or restarts) the flow. */
    public void begin() {
        cancelInternal();
        errorText.set("");
        expired.set(false);
        userCode.set("");
        verificationUri.set("");
        countdownText.set("");
        account.set(null);
        if (!backend.signInConfigured()) {
            state.set(State.NOT_CONFIGURED);
            statusText.set(messages.get("signin.notConfigured.title"));
            return;
        }
        state.set(State.STARTING);
        statusText.set(messages.get("signin.starting"));
        final CancellationToken t = new CancellationToken();
        token = t;
        running = Async.run(executors, backend::beginSignIn, issued -> {
            if (token != t) {
                return;
            }
            code = issued;
            userCode.set(issued.userCode());
            verificationUri.set(issued.verificationUri());
            state.set(State.WAITING);
            statusText.set(messages.get("signin.waiting"));
            tick(Instant.now(backend.clock()));
            poll(t, issued);
        }, this::fail);
    }

    private void poll(final CancellationToken t, final DeviceCode issued) {
        running = Async.run(executors, () -> backend.completeSignIn(issued, t), signedIn -> {
            if (token != t) {
                return;
            }
            account.set(signedIn);
            state.set(State.DONE);
            statusText.set(messages.format("signin.done", signedIn.name()));
            countdownText.set("");
            onSignedIn.accept(signedIn);
        }, this::fail);
    }

    /**
     * Updates the countdown. Called by the view's timer once per second.
     *
     * @param now current time
     */
    public void tick(final Instant now) {
        if (code == null || state.get() != State.WAITING) {
            return;
        }
        final Duration left = Duration.between(now, code.expiresAt());
        if (left.isNegative() || left.isZero()) {
            countdownText.set("");
            expired.set(true);
            errorText.set(messages.get("signin.expired"));
            state.set(State.FAILED);
            statusText.set(messages.get("signin.expired"));
            cancelInternal();
            return;
        }
        countdownText.set(messages.format("signin.expiresIn", formats.countdown(left)));
    }

    /** Cancels the flow (the dialog closes). */
    public void cancel() {
        cancelInternal();
        if (state.get() == State.STARTING || state.get() == State.WAITING) {
            state.set(State.IDLE);
            statusText.set("");
        }
    }

    private void cancelInternal() {
        if (token != null) {
            token.cancel();
            token = null;
        }
        if (running != null) {
            running.cancel(true);
            running = null;
        }
        code = null;
    }

    private void fail(final Throwable error) {
        if (Async.isCancellation(error)) {
            return;
        }
        running = null;
        token = null;
        code = null;
        countdownText.set("");
        if (error instanceof AuthNotConfiguredException) {
            state.set(State.NOT_CONFIGURED);
            statusText.set(messages.get("signin.notConfigured.title"));
            return;
        }
        final String text = errors.describe(error);
        errorText.set(text);
        statusText.set(text);
        state.set(State.FAILED);
    }
}

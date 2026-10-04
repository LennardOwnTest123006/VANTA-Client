package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.DeviceCode;
import dev.vanta.launcher.core.auth.NoGameOwnershipException;
import dev.vanta.launcher.core.auth.XboxAuthException;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private SignInViewModel vm;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        vm = new SignInViewModel(ctx.backend, ctx.executors, ctx.messages, ctx.formats);
    }

    @Test
    void notConfiguredIsExplainedWithoutContactingMicrosoft() {
        ctx.backend.signInConfigured = false;
        vm.begin();
        assertEquals(SignInViewModel.State.NOT_CONFIGURED, vm.state());
        assertTrue(ctx.backend.calls.isEmpty());
    }

    @Test
    void happyPathDeliversTheAccount() {
        ctx.backend.signInResult = FakeBackend.microsoftAccount("Nova");
        ctx.backend.approveSignIn();
        final AtomicReference<Account> delivered = new AtomicReference<>();
        vm.onSignedIn(delivered::set);
        vm.begin();
        // Direct executors: begin → code → poll → complete all run inline.
        assertEquals(SignInViewModel.State.DONE, vm.state());
        assertEquals("Nova", delivered.get().name());
        assertEquals("QW7X9K2P", vm.userCodeProperty().get());
        assertEquals("https://www.microsoft.com/link", vm.verificationUriProperty().get());
        assertEquals(ctx.messages.format("signin.done", "Nova"), vm.statusTextProperty().get());
        assertEquals("", vm.countdownTextProperty().get());
    }

    @Test
    void countdownAndExpiry() {
        // Use a backend whose completeSignIn blocks: do not approve; run begin on a thread-free path by faking state.
        ctx.backend.signInResult = FakeBackend.microsoftAccount("Nova");
        ctx.backend.deviceCode = new DeviceCode("d", "ABCD1234", "https://www.microsoft.com/link", 120, 5, "", FakeBackend.CLOCK.millis());
        final SignInViewModel waiting = new SignInViewModel(ctx.backend, new UiExecutors(java.util.concurrent.Executors.newSingleThreadExecutor(),
            Runnable::run), ctx.messages, ctx.formats);
        waiting.begin();
        waitFor(() -> waiting.state() == SignInViewModel.State.WAITING);
        waiting.tick(FakeBackend.CLOCK.instant().plus(Duration.ofSeconds(30)));
        assertEquals(ctx.messages.format("signin.expiresIn", "1:30"), waiting.countdownTextProperty().get());
        waiting.tick(FakeBackend.CLOCK.instant().plus(Duration.ofSeconds(121)));
        assertEquals(SignInViewModel.State.FAILED, waiting.state());
        assertTrue(waiting.expiredProperty().get());
        assertEquals(ctx.messages.get("signin.expired"), waiting.errorTextProperty().get());
        ctx.backend.approveSignIn();
    }

    @Test
    void xboxErrorsAreMappedToFriendlyText() {
        ctx.backend.signInFailure = new XboxAuthException(XboxAuthException.XERR_CHILD_ACCOUNT, null);
        ctx.backend.approveSignIn();
        vm.begin();
        assertEquals(SignInViewModel.State.FAILED, vm.state());
        assertEquals(ctx.messages.get("error.auth.child"), vm.errorTextProperty().get());

        ctx.backend.signInFailure = new NoGameOwnershipException("This Microsoft account");
        vm.begin();
        assertEquals(ctx.messages.get("error.auth.noGame"), vm.errorTextProperty().get());

        ctx.backend.signInFailure = new AuthException("Sign-in was declined in the browser.");
        vm.begin();
        assertEquals(ctx.messages.get("error.auth.declined"), vm.errorTextProperty().get());
    }

    @Test
    void cancelResetsToIdle() {
        ctx.backend.signInResult = FakeBackend.microsoftAccount("Nova");
        final SignInViewModel waiting = new SignInViewModel(ctx.backend, new UiExecutors(java.util.concurrent.Executors.newSingleThreadExecutor(),
            Runnable::run), ctx.messages, ctx.formats);
        waiting.begin();
        waitFor(() -> waiting.state() == SignInViewModel.State.WAITING);
        waiting.cancel();
        assertEquals(SignInViewModel.State.IDLE, waiting.state());
        assertNull(waiting.accountProperty().get());
        ctx.backend.approveSignIn();
        // A late approval after cancel must not flip the state.
        sleep(100);
        assertEquals(SignInViewModel.State.IDLE, waiting.state());
    }

    private static void waitFor(final java.util.function.BooleanSupplier condition) {
        final Instant deadline = Instant.now().plusSeconds(10);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not met in time");
            }
            sleep(10);
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

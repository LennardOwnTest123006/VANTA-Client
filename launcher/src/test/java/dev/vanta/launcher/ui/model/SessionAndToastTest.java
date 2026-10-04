package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAndToastTest {

    @TempDir
    Path tmp;

    @Test
    void sessionLoadsEverythingAndSwitchesAccounts() {
        final TestContext ctx = new TestContext(tmp);
        final FakeBackend backend = ctx.backend;
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        backend.accounts.add(new dev.vanta.launcher.core.auth.Account("2f6e6a4e-6c0e-4a3d-9f0b-2d1b4c9e7a22", "Kai", "", "t", 1L, "r",
            dev.vanta.launcher.core.auth.AccountType.MICROSOFT));
        backend.javaInstalls.add(backend.system17());
        backend.javaInstalls.add(backend.temurin21());
        backend.instance = FakeBackend.installedInstance("1.0.0");
        backend.offlineAllowed = true;
        assertFalse(ctx.session.loadedProperty().get());
        ctx.session.refreshAll();
        assertTrue(ctx.session.loadedProperty().get());
        assertEquals("Nova", ctx.session.account().orElseThrow().name());
        assertEquals(2, ctx.session.javaInstalls().size());
        assertEquals(21, ctx.session.java().orElseThrow().major(), "pick skips Java 17");
        assertTrue(ctx.session.instance().isPresent());
        assertTrue(ctx.session.offlineAllowedProperty().get());
        assertTrue(ctx.session.signInConfiguredProperty().get());

        ctx.session.switchAccount("2f6e6a4e-6c0e-4a3d-9f0b-2d1b4c9e7a22");
        assertEquals("Kai", ctx.session.account().orElseThrow().name());
        final java.util.concurrent.atomic.AtomicReference<List<String>> names = new java.util.concurrent.atomic.AtomicReference<>();
        ctx.session.loadAccounts(list -> names.set(list.stream().map(a -> a.name()).toList()));
        assertEquals(List.of("Kai", "Nova"), names.get());

        ctx.session.selectJava(backend.system17(), () -> { });
        assertEquals(backend.system17().home().toString(), backend.settings.javaPath());
        assertEquals(17, ctx.session.java().orElseThrow().major());
        assertTrue(ctx.sessionErrors.isEmpty());
    }

    @Test
    void toastsAreCappedAndDismissable() {
        final ToastModel toasts = new ToastModel();
        for (int i = 0; i < 6; i++) {
            toasts.info("t" + i, "");
        }
        assertEquals(4, toasts.toasts().size());
        assertEquals("t2", toasts.toasts().get(0).title());
        final ToastModel.Toast error = toasts.error("boom", "details");
        assertEquals(12, error.ttl().getSeconds());
        toasts.dismiss(error.id());
        assertFalse(toasts.toasts().contains(error));
        toasts.clear();
        assertTrue(toasts.toasts().isEmpty());
        final NavigationModel nav = new NavigationModel();
        assertEquals(NavigationModel.Page.HOME, nav.current());
        nav.navigate(NavigationModel.Page.LOGS);
        assertEquals(NavigationModel.Page.LOGS, nav.current());
        assertEquals("nav.logs", NavigationModel.Page.LOGS.titleKey());
    }
}

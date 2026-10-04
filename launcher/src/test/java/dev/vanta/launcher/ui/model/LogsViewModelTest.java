package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.ui.testutil.FakeBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogsViewModelTest {

    @Test
    void filtersByTextAndLevelPerSource() {
        final LogBuffer launcher = new LogBuffer(100, FakeBackend.CLOCK);
        final LogBuffer game = new LogBuffer(100, FakeBackend.CLOCK);
        launcher.append(LogLevel.INFO, "Launcher started");
        final LogsViewModel vm = new LogsViewModel(UiExecutors.direct(), launcher, game);
        assertEquals(1, vm.totalCountProperty().get(), "existing lines are seeded");
        launcher.append(LogLevel.WARN, "Retrying fabric-api.jar");
        launcher.append(LogLevel.ERROR, "Checksum mismatch for vanta-client.jar");
        game.append("[12:00:01] [main/INFO]: Loading Minecraft");
        assertEquals(3, vm.totalCountProperty().get());
        assertEquals(3, vm.visibleCountProperty().get());

        vm.filterProperty().set("JAR");
        assertEquals(2, vm.visibleCountProperty().get());
        vm.levelEnabledProperty(LogLevel.WARN).set(false);
        assertEquals(1, vm.visibleCountProperty().get());
        assertEquals("Checksum mismatch for vanta-client.jar", vm.copyText());

        vm.sourceProperty().set(LogsViewModel.Source.GAME);
        assertEquals(1, vm.totalCountProperty().get());
        assertEquals(0, vm.visibleCountProperty().get(), "filter text applies to both sources");
        vm.filterProperty().set("");
        assertEquals(1, vm.visibleCountProperty().get());
        assertFalse(vm.currentIsEmpty());

        vm.clearView();
        assertTrue(vm.currentIsEmpty());
        assertEquals(0, vm.totalCountProperty().get());
        game.append("new line");
        assertEquals(1, vm.totalCountProperty().get(), "lines after Clear view still arrive");
        assertEquals(2, vm.lines(LogsViewModel.Source.LAUNCHER).size(), "launcher view keeps INFO + ERROR (WARN is still hidden)");
    }

    @Test
    void viewRespectsBufferCapacity() {
        final LogBuffer launcher = new LogBuffer(5, FakeBackend.CLOCK);
        final LogBuffer game = new LogBuffer(5, FakeBackend.CLOCK);
        final LogsViewModel vm = new LogsViewModel(UiExecutors.direct(), launcher, game);
        for (int i = 0; i < 12; i++) {
            launcher.append("line " + i);
        }
        assertEquals(5, vm.totalCountProperty().get());
        assertEquals("line 11", vm.currentLines().get(4).text());
    }
}

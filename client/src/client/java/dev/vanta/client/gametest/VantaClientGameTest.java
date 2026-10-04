package dev.vanta.client.gametest;

import dev.vanta.client.VantaClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Automated client game test. Only runs when Minecraft is started with {@code -Dfabric.client.gametest};
 * it is inert in normal play. CI launches the real built jar with this flag and keeps the screenshots.
 */
public final class VantaClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(20);
        context.takeScreenshot("01_title_screen");
        VantaClient.LOGGER.info("VANTA client game test: title screen reached");
    }
}

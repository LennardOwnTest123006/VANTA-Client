package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.pollFor;
import static dev.vanta.client.gametest.VantaClientGameTest.step;
import static dev.vanta.client.gametest.VantaClientGameTest.warn;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.screen.settings.SettingsScreen;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.widget.Select;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;

/**
 * The reported bug: "I set Graphics from Fancy to Fast, close the screen with Escape, play, open it again and it is
 * Fancy again". This step drives the real game through that flow and fails with the exact step that loses the choice.
 * <ol>
 *   <li>Vanilla: the vanilla Video Settings screen is open and the graphics preset is set to Fast exactly as its
 *       button does ({@code OptionInstance.set} then {@code Options.save()}); the screen is closed with Escape
 *       ({@code onClose} → parent, {@code removed} saves options.txt).</li>
 *   <li>VANTA's main menu, Settings (Video category) and Performance Center are opened, ticked and closed; Fast must
 *       still be set, shown by the VANTA graphics row and written in options.txt.</li>
 *   <li>Options are read back from disk as a restart does ({@code Options.load()}, then VANTA's settings load).</li>
 *   <li>With Sodium loaded: one bundled option is changed through the vanilla API Sodium's Apply uses
 *       ({@code OptionInstance.set} + {@code Options.save()}), the game reports its preset (Minecraft 1.21.11:
 *       Custom) and VANTA must show exactly that, before and after the same screens and the reload; Sodium's own
 *       video settings screen opens once and VANTA's Apply/Escape hint must be posted.</li>
 *   <li>VANTA: Fast is chosen in VANTA's own Settings row; the screen is closed; the same screens and the reload
 *       follow.</li>
 * </ol>
 * Nothing here relies on the network. The step starts and ends on the VANTA main menu.
 */
final class OptionsPersistenceStep {
    private static final int SCREEN_TICKS = 10;
    private static final int TIMEOUT_TICKS = 200;

    private OptionsPersistenceStep() {
    }

    static void run(ClientGameTestContext context, VantaServices services) {
        Path optionsFile = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        String before = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
        step("options persistence: graphics preset before the step: " + before + ", file " + optionsFile);

        // ---- 1. the vanilla Video Settings screen ----------------------------------------------------------------
        context.setScreen(() -> new VideoSettingsScreen(VantaScreens.create(ScreenId.MAIN_MENU, null),
                Minecraft.getInstance(), Minecraft.getInstance().options));
        waitFor(context, client -> client.screen instanceof VideoSettingsScreen, "the vanilla Video Settings screen");
        context.waitTicks(SCREEN_TICKS);
        context.runOnClient(client -> {
            // The preset button runs OptionInstance.set (whose callback applies the preset bundle) and then saves.
            // Start from Fancy so the change to Fast is a real change even in a reused run directory.
            if (client.options.graphicsPreset().get() == GraphicsPreset.FAST) {
                client.options.graphicsPreset().set(GraphicsPreset.FANCY);
                client.options.save();
            }
            client.options.graphicsPreset().set(GraphicsPreset.FAST);
            client.options.save();
        });
        expectGame(context, "FAST", "vanilla Video Settings: Fast selected");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after Escape on Video Settings");
        expectEverywhere(context, services, optionsFile, "FAST", "vanilla Video Settings → Escape");
        visitVantaScreens(context, services, "FAST");
        expectEverywhere(context, services, optionsFile, "FAST", "VANTA main menu, Settings (Video), Performance");
        reloadFromDisk(context, services);
        expectEverywhere(context, services, optionsFile, "FAST", "options reloaded from disk (restart)");

        // ---- 2. with Sodium: its Apply path, its screen and VANTA's hint -----------------------------------------
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            context.runOnClient(client -> {
                CloudStatus clouds = client.options.cloudStatus().get();
                client.options.cloudStatus().set(clouds == CloudStatus.OFF ? CloudStatus.FAST : CloudStatus.OFF);
                client.options.save();
            });
            String afterSodium = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
            step("options persistence: after a bundled option changed the Sodium way the game reports "
                    + afterSodium);
            if (!"CUSTOM".equals(afterSodium)) {
                warn("options persistence: Minecraft did not switch the graphics preset to Custom after a bundled "
                        + "option changed (it reports " + afterSodium + ")");
            }
            expectEverywhere(context, services, optionsFile, afterSodium, "bundled option changed (Sodium Apply)");
            sodiumScreenHint(context, services);
            expectEverywhere(context, services, optionsFile, afterSodium, "Sodium video settings → Escape");
            visitVantaScreens(context, services, afterSodium);
            expectEverywhere(context, services, optionsFile, afterSodium, "VANTA screens after the Sodium change");
            reloadFromDisk(context, services);
            expectEverywhere(context, services, optionsFile, afterSodium, "reloaded after the Sodium change");
        }

        // ---- 3. VANTA's own Settings row -------------------------------------------------------------------------
        String current = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
        if ("FAST".equals(current)) {
            chooseInVantaRow(context, "FANCY");
            expectGame(context, "FANCY", "VANTA Settings row: Fancy selected");
        }
        chooseInVantaRow(context, "FAST");
        expectGame(context, "FAST", "VANTA Settings row: Fast selected");
        context.setScreen(() -> null); // closing the VANTA screen saves (VantaUiScreen.onClose → saveAll)
        waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after closing Settings");
        expectEverywhere(context, services, optionsFile, "FAST", "VANTA Settings row → screen closed");
        visitVantaScreens(context, services, "FAST");
        expectEverywhere(context, services, optionsFile, "FAST", "VANTA screens after the VANTA row change");
        reloadFromDisk(context, services);
        expectEverywhere(context, services, optionsFile, "FAST", "reloaded after the VANTA row change");

        step("options persistence: ok");
    }

    // ---- flow pieces -----------------------------------------------------------------------------------------------

    /** Opens, ticks and closes the VANTA main menu, Settings on the Video category and the Performance Center. */
    private static void visitVantaScreens(ClientGameTestContext context, VantaServices services, String expected) {
        for (ScreenId id : List.of(ScreenId.MAIN_MENU, ScreenId.SETTINGS, ScreenId.PERFORMANCE)) {
            context.setScreen(() -> VantaScreens.create(id, null));
            waitFor(context, client -> client.screen instanceof VantaScreen vanta && vanta.screenId() == id,
                    "VANTA " + id.id());
            if (id == ScreenId.SETTINGS) {
                Optional<String> shown = context.computeOnClient(client -> graphicsRowValue(client));
                check(shown.isPresent(), "options persistence: the VANTA Settings screen has no graphics row");
                check(expected.equals(shown.get()), "options persistence: VANTA Settings (Video) shows "
                        + shown.get() + " but the game has " + expected);
            }
            context.waitTicks(SCREEN_TICKS);
            context.setScreen(() -> null);
            waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after closing " + id.id());
        }
    }

    /** Value shown by the graphics row of the open VANTA Settings screen (switched to the Video category). */
    private static Optional<String> graphicsRowValue(Minecraft client) {
        if (!(client.screen instanceof VantaScreen vanta) || !(vanta.ui() instanceof SettingsScreen settings)) {
            return Optional.empty();
        }
        settings.selectCategory(SettingCategory.VIDEO);
        return settings.rowFor(VantaSettings.VIDEO_GRAPHICS_MODE.id())
                .map(SettingRow::editor)
                .filter(editor -> editor instanceof Select<?>)
                .map(editor -> String.valueOf(((Select<?>) editor).value()));
    }

    /** Opens VANTA Settings (Video) and picks {@code value} in the graphics row the way a click on the list does. */
    private static void chooseInVantaRow(ClientGameTestContext context, String value) {
        context.setScreen(() -> VantaScreens.create(ScreenId.SETTINGS, null));
        waitFor(context, client -> client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.SETTINGS,
                "VANTA Settings");
        context.waitTicks(SCREEN_TICKS);
        boolean chosen = context.computeOnClient(client -> {
            if (!(client.screen instanceof VantaScreen vanta) || !(vanta.ui() instanceof SettingsScreen settings)) {
                return false;
            }
            settings.selectCategory(SettingCategory.VIDEO);
            Optional<SettingRow> row = settings.rowFor(VantaSettings.VIDEO_GRAPHICS_MODE.id());
            if (row.isEmpty() || !(row.get().editor() instanceof Select<?> select)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Select<Object> typed = (Select<Object>) select;
            int index = typed.options().indexOf(value);
            if (index < 0) {
                return false;
            }
            typed.select(vanta.ui().context(), index);
            return true;
        });
        check(chosen, "options persistence: could not pick " + value + " in the VANTA graphics row");
        context.waitTicks(2);
    }

    /** Opens Sodium's video settings screen as its Options button does, checks the VANTA hint, leaves with Escape. */
    private static void sodiumScreenHint(ClientGameTestContext context, VantaServices services) {
        String title = Lang.tr("vanta.notification.sodium_apply.title");
        boolean opened = context.computeOnClient(client -> {
            try {
                Class<?> type = Class.forName(NotificationCenter.SODIUM_VIDEO_SETTINGS_SCREEN);
                Object screen = type.getMethod("createScreen", Screen.class)
                        .invoke(null, VantaScreens.create(ScreenId.MAIN_MENU, null));
                if (!(screen instanceof Screen sodium)) {
                    return false;
                }
                client.setScreen(sodium);
                return true;
            } catch (ReflectiveOperationException | LinkageError e) {
                warn("options persistence: Sodium's video settings screen could not be created: " + e);
                return false;
            }
        });
        if (!opened) {
            warn("options persistence: Sodium is loaded but its video settings screen did not open; hint not checked");
            return;
        }
        waitFor(context, client -> client.screen != null
                && !(client.screen instanceof VantaScreen), "Sodium's video settings screen");
        context.waitTicks(SCREEN_TICKS);
        String shown = context.computeOnClient(client -> client.screen == null ? "no screen"
                : client.screen.getClass().getName());
        if (NotificationCenter.SODIUM_VIDEO_SETTINGS_SCREEN.equals(shown)) {
            boolean hinted = context.computeOnClient(client -> services.notifications().history().stream()
                    .anyMatch(n -> n.title().equals(title)));
            check(hinted, "options persistence: Sodium's video settings opened without VANTA's Apply/Escape hint");
            step("options persistence: Sodium video settings opened, hint '" + title + "' shown");
        } else {
            warn("options persistence: Sodium's createScreen opened " + shown + "; hint not checked");
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after Escape on Sodium");
    }

    /** What a restart does with the files: Minecraft reads options.txt, VANTA reads settings.json. */
    private static void reloadFromDisk(ClientGameTestContext context, VantaServices services) {
        context.runOnClient(client -> {
            client.options.load();
            services.settings().load();
        });
        context.waitTicks(2);
    }

    // ---- checks ----------------------------------------------------------------------------------------------------

    private static boolean onVantaMainMenu(Minecraft client) {
        return client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.MAIN_MENU;
    }

    private static void waitFor(ClientGameTestContext context, java.util.function.Predicate<Minecraft> predicate,
                                String what) {
        boolean shown = pollFor(context, predicate, TIMEOUT_TICKS);
        check(shown, "options persistence: expected " + what + " but the screen is " + context.computeOnClient(
                client -> client.screen == null ? "no screen" : client.screen.getClass().getName()));
    }

    private static void expectGame(ClientGameTestContext context, String expected, String after) {
        String game = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
        check(expected.equals(game), "options persistence: after " + after + " the game's graphics preset is "
                + game + ", expected " + expected);
    }

    /** The game, VANTA's settings store and options.txt all hold {@code expected}. */
    private static void expectEverywhere(ClientGameTestContext context, VantaServices services, Path optionsFile,
                                         String expected, String after) {
        expectGame(context, expected, after);
        Object vanta = context.computeOnClient(
                client -> (Object) services.settings().get(VantaSettings.VIDEO_GRAPHICS_MODE));
        check(expected.equals(vanta), "options persistence: after " + after + " VANTA shows the graphics preset as "
                + vanta + ", expected " + expected);
        Optional<String> stored = graphicsPresetInFile(optionsFile);
        check(stored.isPresent(), "options persistence: after " + after + " " + optionsFile.getFileName()
                + " has no graphicsPreset line");
        check(expected.equalsIgnoreCase(stored.get()), "options persistence: after " + after + " "
                + optionsFile.getFileName() + " says graphicsPreset:" + stored.get() + ", expected "
                + expected.toLowerCase(Locale.ROOT));
        step("options persistence: " + after + " → " + expected + " (game, VANTA, options.txt)");
    }

    /** The graphics preset value written in options.txt ({@code graphicsPreset:"fast"} → {@code fast}). */
    private static Optional<String> graphicsPresetInFile(Path file) {
        try {
            for (String line : Files.readAllLines(file)) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                if (key.equals("graphicsPreset") || key.equals("graphicsMode")) {
                    return Optional.of(line.substring(colon + 1).trim().replace("\"", ""));
                }
            }
        } catch (IOException e) {
            warn("options persistence: could not read " + file + ": " + e);
        }
        return Optional.empty();
    }
}

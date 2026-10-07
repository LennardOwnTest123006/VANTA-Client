package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.pollFor;
import static dev.vanta.client.gametest.VantaClientGameTest.step;
import static dev.vanta.client.gametest.VantaClientGameTest.warn;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

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
 *   <li>One bundled option (clouds) is changed through the vanilla API that a vanilla slider and Sodium's Apply use
 *       ({@code OptionInstance.set} + {@code Options.save()}); the game must report Custom and VANTA must show
 *       exactly that, before and after the same screens and the reload. With Sodium loaded, Sodium's own video
 *       settings screen opens once and VANTA's Apply/Escape hint must be posted.</li>
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
        // Click the screen's own Graphics preset button with the real mouse path, as a player does, until it shows
        // Fast. Setting the option in code while the screen is open would leave the screen's sliders holding the old
        // bundle values, which the screen may apply again when it closes.
        clickPresetButtonUntil(context, GraphicsPreset.FAST);
        String bundleBeforeEscape = context.computeOnClient(OptionsPersistenceStep::bundle);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after Escape on Video Settings");
        String afterEscape = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
        if (!"FAST".equals(afterEscape)) {
            step("options persistence: bundled options before Escape: " + bundleBeforeEscape);
            step("options persistence: bundled options after Escape:  " + context.computeOnClient(OptionsPersistenceStep::bundle));
        }
        expectEverywhere(context, services, optionsFile, "FAST", "vanilla Video Settings → Escape");
        visitVantaScreens(context, services, "FAST");
        expectEverywhere(context, services, optionsFile, "FAST", "VANTA main menu, Settings (Video), Performance");
        reloadFromDisk(context, services);
        expectEverywhere(context, services, optionsFile, "FAST", "options reloaded from disk (restart)");

        // ---- 2. a bundled option changed outside VANTA (vanilla slider, Sodium's Apply) --------------------------
        // Runs in both CI jobs: showing "Custom" is VANTA's job, not Sodium's. Sodium's Apply uses the same vanilla API
        // (OptionInstance.set, then Options.save()); only its screen and VANTA's hint need Sodium.
        context.runOnClient(client -> {
            CloudStatus clouds = client.options.cloudStatus().get();
            client.options.cloudStatus().set(clouds == CloudStatus.OFF ? CloudStatus.FAST : CloudStatus.OFF);
            client.options.save();
        });
        String afterBundled = context.computeOnClient(client -> client.options.graphicsPreset().get().name());
        step("options persistence: after a bundled option changed outside VANTA the game reports " + afterBundled);
        // Minecraft 1.21.11 has Options.setGraphicsPresetToCustom (javap of the real jar), called by the clouds
        // callback. Without Custom here the checks below could never see VANTA show a Custom preset.
        check("CUSTOM".equals(afterBundled), "options persistence: Minecraft did not switch the graphics preset to "
                + "Custom after the clouds option changed (it reports " + afterBundled + ")");
        expectEverywhere(context, services, optionsFile, afterBundled, "bundled option changed outside VANTA");
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            sodiumScreenHint(context, services);
            expectEverywhere(context, services, optionsFile, afterBundled, "Sodium video settings → Escape");
        }
        visitVantaScreens(context, services, afterBundled);
        expectEverywhere(context, services, optionsFile, afterBundled, "VANTA screens after the bundled change");
        reloadFromDisk(context, services);
        expectEverywhere(context, services, optionsFile, afterBundled, "reloaded after the bundled change");

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

        // Give the following in-world steps the preset the earlier steps left (Custom itself cannot be selected).
        if (!"FAST".equals(before) && !"CUSTOM".equals(before)) {
            context.runOnClient(client -> {
                client.options.graphicsPreset().set(GraphicsPreset.valueOf(before));
                client.options.save();
            });
        }
        step("options persistence: ok");
    }

    // ---- the vanilla Graphics preset button ---------------------------------------------------------------------

    /**
     * Clicks the left end of the Video Settings screen's Graphics preset slider (Fast) once with the real cursor and
     * left button, as a player does. The slider may apply its value at once or when the screen closes
     * (OptionsList.applyUnsavedChanges in OptionsSubScreen.onClose), so the caller checks after Escape.
     */
    private static void clickPresetButtonUntil(ClientGameTestContext context, GraphicsPreset target) {
        double[] point = context.computeOnClient(client -> {
            AbstractWidget button = presetButton(client);
            if (button == null || !button.visible) {
                return null;
            }
            Window w = client.getWindow();
            // The preset control is a slider over Fast, Fancy, Fabulous and Custom: its left end selects Fast.
            double gx = button.getX() + 5.0;
            double gy = button.getY() + button.getHeight() / 2.0;
            return new double[]{gx * w.getScreenWidth() / w.getGuiScaledWidth(),
                    gy * w.getScreenHeight() / w.getGuiScaledHeight()};
        });
        if (point == null) {
            String labels = context.computeOnClient(OptionsPersistenceStep::widgetLabels);
            check(false, "options persistence: no visible Graphics preset control (caption \""
                    + context.computeOnClient(client -> client.options.graphicsPreset().toString())
                    + "\") on the Video Settings screen; widgets: " + labels);
        }
        context.getInput().setCursorPos(point[0], point[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        step("options persistence: clicked the " + target + " end of the Graphics preset slider; the game reports "
                + context.computeOnClient(client -> client.options.graphicsPreset().get().name()));
    }

    /** The button whose message starts with the graphics preset's caption, searched through every nested container. */
    private static AbstractWidget presetButton(Minecraft client) {
        if (client.screen == null) {
            return null;
        }
        String caption = client.options.graphicsPreset().toString();
        List<GuiEventListener> queue = new ArrayList<>(client.screen.children());
        while (!queue.isEmpty()) {
            GuiEventListener next = queue.remove(0);
            if (next instanceof AbstractWidget widget && widget.getMessage().getString().contains(caption)) {
                return widget;
            }
            if (next instanceof ContainerEventHandler container) {
                queue.addAll(container.children());
            }
        }
        return null;
    }

    /** Every widget label on the open screen, for a failure message. */
    private static String widgetLabels(Minecraft client) {
        List<String> labels = new ArrayList<>();
        List<GuiEventListener> queue = new ArrayList<>(client.screen == null ? List.of() : client.screen.children());
        while (!queue.isEmpty() && labels.size() < 40) {
            GuiEventListener next = queue.remove(0);
            if (next instanceof AbstractWidget widget) {
                labels.add(widget.getClass().getSimpleName() + "\"" + widget.getMessage().getString() + "\"");
            }
            if (next instanceof ContainerEventHandler container) {
                queue.addAll(container.children());
            }
        }
        return String.join(", ", labels);
    }

    /** The options inside 1.21.11's graphics preset bundle, for a failure message. */
    private static String bundle(Minecraft client) {
        Options o = client.options;
        return "preset=" + o.graphicsPreset().get() + " renderDistance=" + o.renderDistance().get()
                + " simulationDistance=" + o.simulationDistance().get() + " clouds=" + o.cloudStatus().get()
                + " particles=" + o.particles().get() + " ao=" + o.ambientOcclusion().get()
                + " entityShadows=" + o.entityShadows().get() + " entityDistance=" + o.entityDistanceScaling().get()
                + " biomeBlend=" + o.biomeBlendRadius().get() + " mipmaps=" + o.mipmapLevels().get()
                + " menuBlur=" + o.menuBackgroundBlurriness().get();
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
            // A toast reaches the history when it is shown; with a full toast column it waits a few seconds.
            boolean hinted = pollFor(context, client -> services.notifications().history().stream()
                    .anyMatch(n -> n.title().equals(title)), TIMEOUT_TICKS);
            check(hinted, "options persistence: Sodium's video settings opened without VANTA's Apply/Escape hint");
            step("options persistence: Sodium video settings opened, hint '" + title + "' shown");
        } else {
            warn("options persistence: Sodium's createScreen opened " + shown + "; hint not checked");
        }
        // A real pending change in Sodium's own config (its GUI scale option, which Sodium's screen itself changes this
        // way on Ctrl+scroll), then Escape: VANTA must apply it instead of letting Sodium discard it.
        int originalScale = context.computeOnClient(client -> client.options.guiScale().get());
        int pendingScale = originalScale == 2 ? 3 : 2;
        boolean pending = context.computeOnClient(client -> sodiumModifyGuiScale(pendingScale));
        if (pending) {
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after Escape on Sodium");
            int applied = context.computeOnClient(client -> client.options.guiScale().get());
            context.runOnClient(client -> {
                client.options.guiScale().set(originalScale);
                client.resizeDisplay();
                client.options.save();
            });
            check(applied == pendingScale, "options persistence: Escape in Sodium's video settings discarded a pending "
                    + "change (GUI scale " + applied + ", expected " + pendingScale + ")");
            step("options persistence: Escape in Sodium's video settings applied the pending change");
        } else {
            warn("options persistence: no pending Sodium change could be made; Escape-applies not checked");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            waitFor(context, OptionsPersistenceStep::onVantaMainMenu, "the VANTA main menu after Escape on Sodium");
        }
    }

    /** Sets Sodium's GUI scale option to a pending (not yet applied) value; true when Sodium reports pending changes. */
    private static boolean sodiumModifyGuiScale(int value) {
        try {
            Object config = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager").getField("CONFIG").get(null);
            Object option = config.getClass().getMethod("getOption", Identifier.class)
                    .invoke(config, Identifier.parse("sodium:general.gui_scale"));
            if (option == null) {
                return false;
            }
            for (java.lang.reflect.Method m : option.getClass().getMethods()) {
                if (m.getName().equals("modifyValue") && m.getParameterCount() == 1) {
                    m.invoke(option, value);
                    return Boolean.TRUE.equals(config.getClass().getMethod("anyOptionChanged").invoke(config));
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn("options persistence: Sodium's GUI scale option could not be changed: " + e);
            return false;
        }
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

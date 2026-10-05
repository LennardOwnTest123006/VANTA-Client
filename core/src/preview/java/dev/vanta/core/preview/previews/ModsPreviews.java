package dev.vanta.core.preview.previews;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.InstallRequest;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.screen.mods.ModsTab;
import dev.vanta.core.ui.UiScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Previews of the Mods &amp; Shaders screen on the in-memory Modrinth API (no network): the Mods tab with the
 * Performance pack and a selected project, the Shaders tab without Iris, the Installed tab after the pack was
 * installed (restart banner) and the compact layout.
 */
public final class ModsPreviews implements PreviewProvider {
    private static ScreenPreview.Options desktop() {
        return ScreenPreview.Options.standard().size(854, 480).scale(2).lang(Lang::tr);
    }

    private static ScreenPreview.Options small() {
        return ScreenPreview.Options.standard().size(427, 240).scale(2).lang(Lang::tr);
    }

    /** A Mods &amp; Shaders screen on fresh preview services; {@code setup} runs once the screen is initialised. */
    private static UiScreen mods(Consumer<ModrinthService> before, Consumer<ModsScreen> setup) {
        PreviewServices p = PreviewServices.create().onTitleScreen();
        Path gameDir;
        try {
            gameDir = Files.createTempDirectory("vanta-mods-preview");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ModrinthService service = new FakeModPlatform(gameDir).installInto(p.services, FakeModrinthApi.standard());
        before.accept(service);
        ModsScreen screen = (ModsScreen) p.services.screens().create(ScreenId.MODS).orElseThrow();
        screen.onceInitialised(() -> setup.accept(screen));
        return screen;
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> out = new ArrayList<>();
        out.add(ScreenPreviews.Entry.of("mods", () -> mods(s -> { }, screen -> screen.selectHit(
                screen.results().hits().get(0))), desktop().mouseOver("mods.pack.install")));
        out.add(ScreenPreviews.Entry.of("mods-search", () -> mods(s -> { }, screen -> screen.setQuery("iris")),
                desktop()));
        out.add(ScreenPreviews.Entry.of("mods-shaders", () -> mods(s -> { }, screen -> {
            screen.selectTab(ModsTab.SHADERS);
            screen.selectHit(screen.results().hits().get(0));
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("mods-installed", () -> mods(service -> {
            service.install(List.of(InstallRequest.of("sodium"), InstallRequest.of("lithium"),
                    InstallRequest.of("iris")), "Installing", null);
            try {
                Files.writeString(service.library().gameDir().resolve("mods/handmade-mod-2.1.jar"), "x",
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, screen -> {
            screen.selectTab(ModsTab.INSTALLED);
            screen.row("project:YL57xq9U").ifPresent(r -> r.select(screen.context()));
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("mods-small", () -> mods(s -> { }, screen -> { }), small()));
        return out;
    }
}

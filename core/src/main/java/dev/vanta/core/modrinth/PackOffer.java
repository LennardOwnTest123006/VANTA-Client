package dev.vanta.core.modrinth;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The one-time "Boost your FPS?" offer of the Performance pack, shown when the main menu first appears in a game that
 * was not started by the VANTA launcher (the launcher installs the pack itself).
 * <p>
 * It is offered when the Modrinth integration is present, the VANTA launcher did not start the game, at least one
 * {@link PerformancePack} member is neither loaded nor in {@code modrinth.json}, the setting
 * {@link VantaSettings#MODS_PACK_OFFER} is on, and it was not shown before in this session. <em>Install</em> first
 * adopts the pack jars of the mods bundle ({@link ModrinthService#adoptKnownFiles}) so the Installed tab manages them,
 * then installs every member with the usual progress and result toasts. <em>Not now</em> turns the setting off, so
 * the offer never nags; Settings &gt; Performance turns it back on. Nothing is downloaded without that click.
 * <p>
 * The game test runs with {@code -Dvanta.gametest=true} (client/build.gradle) and never sees the dialog; hosts that
 * must not prompt either (previews, screen test fixtures) call {@link #suppress()}.
 */
public final class PackOffer {
    /**
     * System property the client game test sets ({@code vanta.gametest}); joined from parts so the translation-key
     * scan of the domain sources does not take it for a lang key.
     */
    public static final String GAMETEST_PROPERTY = String.join(".", "vanta", "gametest");

    private final VantaServices services;
    private final BooleanSupplier gameTest;
    private boolean shown;
    private boolean suppressed;

    /** Offer for a composition root; the game test is detected through {@link #GAMETEST_PROPERTY}. */
    public PackOffer(VantaServices services) {
        this(services, PackOffer::inGameTest);
    }

    PackOffer(VantaServices services, BooleanSupplier gameTest) {
        this.services = Objects.requireNonNull(services, "services");
        this.gameTest = Objects.requireNonNull(gameTest, "gameTest");
    }

    /** True when the client game test started this game. */
    public static boolean inGameTest() {
        return Boolean.parseBoolean(System.getProperty(GAMETEST_PROPERTY, "false"));
    }

    /** Never offer in this session (hosts that must not prompt). */
    public void suppress() {
        setSuppressed(true);
    }

    /** Switches the host-side suppression; {@code false} lets the usual conditions decide again. */
    public void setSuppressed(boolean value) {
        suppressed = value;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    /** True once the dialog was shown in this session. */
    public boolean wasShown() {
        return shown;
    }

    /** Whether the offer should appear now (see the class comment). */
    public boolean shouldOffer() {
        if (shown || suppressed || gameTest.getAsBoolean()) {
            return false;
        }
        Optional<ModrinthService> service = services.modrinth();
        if (service.isEmpty() || service.get().launcherRestartable()) {
            return false;
        }
        if (!services.settings().get(VantaSettings.MODS_PACK_OFFER)) {
            return false;
        }
        return !missing(service.get()).isEmpty();
    }

    /** Pack members that are neither loaded in this game nor recorded in {@code modrinth.json}, in pack order. */
    public static List<PerformancePack.Item> missing(ModrinthService service) {
        Set<String> installedSlugs = new HashSet<>();
        for (InstalledEntry entry : service.library().index().entries()) {
            installedSlugs.add(entry.slug());
        }
        List<PerformancePack.Item> out = new ArrayList<>();
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            if (!service.platform().isModLoaded(item.modId()) && !installedSlugs.contains(item.slug())) {
                out.add(item);
            }
        }
        return out;
    }

    /**
     * Opens the dialog when {@link #shouldOffer()}; afterwards the offer counts as shown for this session.
     *
     * @return the dialog, or empty when nothing was shown
     */
    public Optional<Dialog> showIfWanted(UiContext ctx) {
        if (!shouldOffer()) {
            return Optional.empty();
        }
        shown = true;
        ModrinthService service = services.modrinth().orElseThrow();
        String names = missing(service).stream().map(PerformancePack.Item::name).collect(Collectors.joining(", "));
        Dialog dialog = Dialog.confirm(ctx, Lang.tr("vanta.mods.offer.title"), Lang.tr("vanta.mods.offer.body", names),
                Lang.tr("vanta.mods.offer.install"), Lang.tr("vanta.mods.offer.not_now"), false, () -> install(null));
        // Dialog.confirm runs nothing on cancel; "Not now" must switch the offer off, so the cancel button gets that.
        List<UiNode> buttons = dialog.buttonRow().children();
        if (!buttons.isEmpty() && buttons.get(0) instanceof Button notNow) {
            notNow.onClick(() -> {
                dialog.close(ctx);
                decline();
            });
        }
        CoreLog.info("Offering the Performance pack: {} missing", names);
        return Optional.of(dialog);
    }

    /** "Install": adopt bundled jars, then install every member with the usual toasts. */
    public void install(Consumer<InstallResult> onDone) {
        Optional<ModrinthService> service = services.modrinth();
        if (service.isEmpty()) {
            return;
        }
        service.get().adoptKnownFiles(adopted ->
                service.get().installPerformancePack(PerformancePack.slugs(), onDone));
    }

    /** "Not now": never offer again until the setting is switched back on. */
    public void decline() {
        services.settings().set(VantaSettings.MODS_PACK_OFFER, false);
        CoreLog.info("Performance pack offer declined; {} is now off", VantaSettings.MODS_PACK_OFFER.id());
    }
}

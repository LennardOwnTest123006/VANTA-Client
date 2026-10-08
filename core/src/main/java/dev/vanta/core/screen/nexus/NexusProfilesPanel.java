package dev.vanta.core.screen.nexus;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.InfoBanner;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.cosmetics.CardGrid;
import dev.vanta.core.screen.profiles.NameDialog;
import dev.vanta.core.screen.profiles.ProfileActions;
import dev.vanta.core.screen.profiles.ProfileCard;
import dev.vanta.core.screen.profiles.ProfileSummary;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Profiles section: the same {@link ProfileCard}s and actions as the Profiles screen (activate, duplicate,
 * rename, export, reset built-ins, delete with confirmation), "Save current as profile", the unsaved-changes banner
 * and a link to the full Profiles screen. Everything goes through {@link ProfileManager} and {@link ProfileActions}.
 */
public final class NexusProfilesPanel extends NexusPanel implements ProfileCard.Listener {
    private final ProfileManager profiles;
    private final ProfileActions actions;
    private final InfoBanner unsaved;
    private final CardGrid grid;
    private final List<ProfileCard> cards = new ArrayList<>();
    private final Button create;
    private final Button openScreen;
    private UiContext lastContext;

    public NexusProfilesPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.PROFILES);
        this.profiles = services.profiles();
        this.actions = new ProfileActions(services);

        unsaved = new InfoBanner(InfoBanner.Tone.WARNING, Lang.tr("vanta.profiles.unsaved.title"), "");
        Button save = Button.primary(Lang.tr("vanta.profiles.unsaved.save"), this::saveToActive).compact(true);
        save.setId("nexus.profiles.saveActive");
        unsaved.action(save);
        unsaved.setId("nexus.profiles.unsaved");
        content().add(unsaved);

        ChipFlow top = new ChipFlow();
        create = Button.primary(Lang.tr("vanta.profiles.create_from_current"), this::openCreateDialog).compact(true);
        create.icon(Icons.PLUS);
        create.setId("nexus.profiles.create");
        top.add(create);
        openScreen = Button.ghost(Lang.tr("vanta.nexus.profiles.open_screen"),
                () -> navigator.openScreen(lastContext, ScreenId.PROFILES)).compact(true);
        openScreen.icon(Icons.EXTERNAL_LINK);
        openScreen.setId("nexus.profiles.open");
        top.add(openScreen);
        content().add(top);

        grid = new CardGrid(236, 3, Theme.SPACE_4).rowHeight(ProfileCard.HEIGHT);
        grid.setId("nexus.profiles.grid");
        content().add(grid);
        refresh();
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public List<ProfileCard> cards() {
        return List.copyOf(cards);
    }

    public Optional<ProfileCard> card(String profileId) {
        return cards.stream().filter(c -> c.profile().id().equals(profileId)).findFirst();
    }

    public Button createButton() {
        return create;
    }

    public Button openScreenButton() {
        return openScreen;
    }

    public InfoBanner unsavedBanner() {
        return unsaved;
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Rebuilds the cards and the banner from the stores. */
    public void refresh() {
        List<String> changes = ProfileSummary.unsavedChanges(services());
        Optional<Profile> active = profiles.active();
        unsaved.setVisible(!changes.isEmpty() && active.isPresent());
        if (unsaved.isVisible()) {
            unsaved.setBody(Lang.tr("vanta.profiles.unsaved.body", String.join(", ", changes), active.get().name()));
        }
        List<Profile> all = profiles.list();
        grid.clearChildren();
        cards.clear();
        boolean deletable = all.size() > 1;
        long now = services().clock().millis();
        for (Profile profile : all) {
            boolean isActive = active.map(a -> a.id().equals(profile.id())).orElse(false);
            ProfileCard card = new ProfileCard(ProfileSummary.of(profile), isActive, deletable,
                    name -> actions.validateName(name, profile.id()), services().clock().getZone(), now, this);
            cards.add(card);
            grid.add(card);
        }
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    @Override
    public void onShown(UiContext ctx) {
        lastContext = ctx;
        refresh();
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        super.layout(ctx);
    }

    // ---- card actions --------------------------------------------------------------------------------------------

    @Override
    public void activate(String profileId) {
        if (services().activateProfile(profileId)) {
            refresh();
        }
    }

    @Override
    public void duplicate(String profileId) {
        Optional<Profile> source = profiles.find(profileId);
        if (source.isEmpty()) {
            return;
        }
        NameDialog.open(lastContext, Lang.tr("vanta.profiles.duplicate.title", source.get().name()),
                Lang.tr("vanta.profiles.duplicate.hint"), actions.copyName(source.get().name()),
                Lang.tr("vanta.profiles.duplicate"), name -> actions.validateName(name, null), name -> {
                    Optional<Profile> copy = profiles.duplicate(profileId, name);
                    copy.ifPresent(c -> notify(NotificationKind.SUCCESS, "vanta.profiles.notification.duplicated.",
                            c.name()));
                    refresh();
                });
    }

    @Override
    public void rename(String profileId, String newName) {
        if (profiles.rename(profileId, newName)) {
            refresh();
        }
    }

    @Override
    public void export(String profileId) {
        Optional<ProfileActions.Export> export = actions.export(profileId);
        if (export.isEmpty()) {
            return;
        }
        services().clipboard().ifPresentOrElse(c -> c.set(export.get().json()),
                () -> lastContext.host().setClipboard(export.get().json()));
        services().notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.profile_exported.title"),
                Lang.tr("vanta.profiles.exported", actions.displayPath(export.get().file())) + " · "
                        + Lang.tr("vanta.profiles.copied_json"));
    }

    @Override
    public void reset(String profileId) {
        Optional<Profile> profile = profiles.find(profileId);
        if (profile.isEmpty() || !ProfileActions.isBuiltIn(profileId)) {
            return;
        }
        Dialog.confirm(lastContext, Lang.tr("vanta.profiles.confirm_reset.title", profile.get().name()),
                Lang.tr("vanta.profiles.confirm_reset.body"), Lang.tr("vanta.common.reset"),
                Lang.tr("vanta.common.cancel"), true, () -> {
                    if (actions.resetBuiltIn(profileId)) {
                        notify(NotificationKind.INFO, "vanta.profiles.notification.reset.", profile.get().name());
                        refresh();
                    }
                });
    }

    @Override
    public void delete(String profileId) {
        Optional<Profile> profile = profiles.find(profileId);
        if (profile.isEmpty() || profiles.size() <= 1) {
            return;
        }
        boolean isActive = profiles.activeId().map(profileId::equals).orElse(false);
        String body;
        if (isActive) {
            Profile next = profiles.list().stream().filter(p -> !p.id().equals(profileId)).findFirst().orElseThrow();
            body = Lang.tr("vanta.profiles.confirm_delete_active.body", next.name());
        } else {
            body = Lang.tr("vanta.profiles.confirm_delete.body");
        }
        Dialog.confirm(lastContext, Lang.tr("vanta.profiles.confirm_delete.title", profile.get().name()), body,
                Lang.tr("vanta.common.delete"), Lang.tr("vanta.common.cancel"), true, () -> {
                    if (profiles.delete(profileId)) {
                        services().keybinds().refresh();
                        notify(NotificationKind.INFO, "vanta.profiles.notification.deleted.", profile.get().name());
                        refresh();
                    }
                });
    }

    /** Opens the "save current as profile" name prompt. */
    public NameDialog openCreateDialog() {
        return NameDialog.open(lastContext, Lang.tr("vanta.profiles.create.title"), Lang.tr("vanta.profiles.create.hint"),
                "", Lang.tr("vanta.profiles.create.button"), name -> actions.validateName(name, null), name -> {
                    Profile created = profiles.createFromCurrent(name, Profile.DEFAULT_ICON);
                    notify(NotificationKind.SUCCESS, "vanta.profiles.notification.created.", created.name());
                    refresh();
                });
    }

    /** Stores the live configuration into the active profile. */
    public void saveToActive() {
        Optional<Profile> updated = profiles.updateActiveFromCurrent();
        updated.ifPresent(p -> notify(NotificationKind.SUCCESS, "vanta.profiles.notification.saved.", p.name()));
        refresh();
    }

    private void notify(NotificationKind kind, String keyPrefix, Object... args) {
        services().notifications().post(kind, Lang.tr(keyPrefix + "title"), Lang.tr(keyPrefix + "body", args));
    }
}

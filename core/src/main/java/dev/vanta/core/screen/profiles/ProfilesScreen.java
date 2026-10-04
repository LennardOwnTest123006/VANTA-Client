package dev.vanta.core.screen.profiles;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.CardGrid;
import dev.vanta.core.screen.cosmetics.InfoBanner;
import dev.vanta.core.screen.cosmetics.Plurals;
import dev.vanta.core.screen.cosmetics.SectionHeader;
import dev.vanta.core.screen.cosmetics.ThemedScreen;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Profiles screen: a card grid of every profile with Activate, Duplicate, Rename (inline), Export (file plus
 * clipboard), Reset (built-ins) and Delete; "Save current as profile" and Import in the top bar; and a banner
 * offering to store the live configuration into the active profile when it drifted away from it. All mutations go
 * through {@link ProfileManager} and {@link ProfileActions}; the grid is rebuilt after each one.
 */
public final class ProfilesScreen extends ThemedScreen implements ProfileCard.Listener {
    private final ProfileActions actions;
    private final ProfileManager profiles;
    private VantaShell shell;
    private Column content;
    private InfoBanner unsavedBanner;
    private SectionHeader header;
    private CardGrid grid;
    private ScrollPanel scroll;
    private final List<ProfileCard> cards = new ArrayList<>();
    private Button importButton;
    private Button createButton;

    public ProfilesScreen(VantaServices services) {
        super(services, ScreenId.PROFILES);
        this.actions = new ProfileActions(services);
        this.profiles = services.profiles();
    }

    @Override
    protected UiNode build(UiContext ctx) {
        shell = newShell();
        Row top = new Row(Theme.SPACE_3);
        importButton = Button.secondary(Lang.tr("vanta.profiles.import"), this::openImportDialog).icon(Icons.UPLOAD)
                .compact(true);
        importButton.setId("profiles.import");
        createButton = Button.primary(Lang.tr("vanta.profiles.create_from_current"), this::openCreateDialog)
                .icon(Icons.PLUS).compact(true);
        createButton.setId("profiles.create");
        top.add(importButton);
        top.add(createButton);
        shell.rightSlot(top);

        content = new Column(Theme.SPACE_4);
        unsavedBanner = new InfoBanner(InfoBanner.Tone.WARNING, Lang.tr("vanta.profiles.unsaved.title"), "");
        Button save = Button.primary(Lang.tr("vanta.profiles.unsaved.save"), this::saveToActive).compact(true);
        save.setId("profiles.save-active");
        unsavedBanner.action(save);
        content.add(unsavedBanner);
        header = new SectionHeader(Lang.tr("vanta.profiles.title"));
        content.add(header);
        grid = new CardGrid(236, 3, Theme.SPACE_4).rowHeight(ProfileCard.HEIGHT);
        scroll = new ScrollPanel(grid).edgeFade(ctx.theme().bgBase());
        scroll.flex(1f);
        content.add(scroll);
        shell.content(content);
        refresh();
        return shell;
    }

    /** Rebuilds the cards and the banner from the stores. */
    public void refresh() {
        UiContext ctx = context();
        List<String> changes = ProfileSummary.unsavedChanges(services());
        Optional<Profile> active = profiles.active();
        unsavedBanner.setVisible(!changes.isEmpty() && active.isPresent());
        if (unsavedBanner.isVisible()) {
            unsavedBanner.setBody(Lang.tr("vanta.profiles.unsaved.body", String.join(", ", changes),
                    active.get().name()));
        }
        List<Profile> all = profiles.list();
        header.trailing(Plurals.count(all.size(), "vanta.profiles.count"));
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
        if (ctx != null) {
            ctx.requestLayout();
        }
    }

    /** The cards in display order. */
    public List<ProfileCard> cards() {
        return List.copyOf(cards);
    }

    /** The card of a profile, if shown. */
    public Optional<ProfileCard> card(String profileId) {
        return cards.stream().filter(c -> c.profile().id().equals(profileId)).findFirst();
    }

    /** The "unsaved changes" banner (visible only when the live configuration drifted from the active profile). */
    public InfoBanner unsavedBanner() {
        return unsavedBanner;
    }

    public Button importButton() {
        return importButton;
    }

    public Button createButton() {
        return createButton;
    }

    /** The file actions helper. */
    public ProfileActions actions() {
        return actions;
    }

    // ---- card actions -------------------------------------------------------------------------------------------

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
        NameDialog.open(context(), Lang.tr("vanta.profiles.duplicate.title", source.get().name()),
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
        copyToClipboard(export.get().json());
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
        Dialog.confirm(context(), Lang.tr("vanta.profiles.confirm_reset.title", profile.get().name()),
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
        Dialog.confirm(context(), Lang.tr("vanta.profiles.confirm_delete.title", profile.get().name()), body,
                Lang.tr("vanta.common.delete"), Lang.tr("vanta.common.cancel"), true, () -> {
                    if (profiles.delete(profileId)) {
                        services().keybinds().refresh();
                        notify(NotificationKind.INFO, "vanta.profiles.notification.deleted.", profile.get().name());
                        refresh();
                    }
                });
    }

    // ---- top bar actions ----------------------------------------------------------------------------------------

    /** Opens the "save current as profile" name prompt. */
    public NameDialog openCreateDialog() {
        return NameDialog.open(context(), Lang.tr("vanta.profiles.create.title"), Lang.tr("vanta.profiles.create.hint"),
                "", Lang.tr("vanta.profiles.create.button"), name -> actions.validateName(name, null), name -> {
                    Profile created = profiles.createFromCurrent(name, Profile.DEFAULT_ICON);
                    notify(NotificationKind.SUCCESS, "vanta.profiles.notification.created.", created.name());
                    refresh();
                });
    }

    /** Opens the import dialog. */
    public ImportDialog openImportDialog() {
        return ImportDialog.open(context(), actions, this::readClipboard, imported -> {
            services().notifications().profileImported(imported.name());
            refresh();
        }, () -> services().game().openUrl(actions.ensureImportsDir().toUri().toString()));
    }

    /** Stores the live configuration into the active profile. */
    public void saveToActive() {
        Optional<Profile> updated = profiles.updateActiveFromCurrent();
        updated.ifPresent(p -> notify(NotificationKind.SUCCESS, "vanta.profiles.notification.saved.", p.name()));
        refresh();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Posts a toast from a key prefix ending in a dot ({@code <prefix>title} and {@code <prefix>body}). */
    private void notify(NotificationKind kind, String keyPrefix, Object... args) {
        services().notifications().post(kind, Lang.tr(keyPrefix + "title"), Lang.tr(keyPrefix + "body", args));
    }

    private void copyToClipboard(String text) {
        services().clipboard().ifPresentOrElse(c -> c.set(text), () -> context().host().setClipboard(text));
    }

    private String readClipboard() {
        return services().clipboard().map(c -> c.get()).orElseGet(() -> context().host().getClipboard());
    }

    @Override
    protected void onThemeChanged(Theme theme) {
        scroll.edgeFade(theme.bgBase());
    }
}

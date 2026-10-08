package dev.vanta.core.screen.nexus;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.EmptyState;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.SectionHeader;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.SearchField;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Toggle;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WaypointCategories;
import dev.vanta.core.waypoints.WaypointColors;
import dev.vanta.core.waypoints.WaypointMarkers;
import dev.vanta.core.waypoints.WaypointSort;
import dev.vanta.core.waypoints.WaypointStore;
import dev.vanta.core.waypoints.WorldKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Waypoints section: the waypoints of the current world (name, category, distance to the player, enabled
 * toggle), search, sort, add / edit through {@link WaypointDialog}, delete with confirmation and an "All worlds" view
 * grouped by world. Outside a world the section says so honestly: nothing can be added to a world that is not open.
 */
public final class WaypointsPanel extends NexusPanel {
    private final WaypointStore store;
    private final GameBridge game;
    private final SearchField search;
    private final Select<WaypointSort> sort;
    private final Toggle allWorlds;
    private final Button add;
    private final Column list = new Column(Theme.SPACE_1);
    private final List<WaypointRow> rows = new ArrayList<>();
    private final Runnable unsubscribe;
    private String query = "";
    private WaypointSort sorting = WaypointSort.NAME;
    private boolean everyWorld;
    private UiContext lastContext;

    public WaypointsPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.WAYPOINTS);
        this.store = services.waypoints();
        this.game = services.game();

        Row top = new Row(Theme.SPACE_3).align(Align.CENTER);
        search = new SearchField(Lang.tr("vanta.waypoints.search_placeholder"));
        search.flex(1f);
        search.setId("nexus.waypoints.search");
        search.onChange(text -> {
            query = text == null ? "" : text.trim();
            refresh();
        });
        top.add(search);
        add = Button.primary(Lang.tr("vanta.waypoints.add"), this::openAddDialog).compact(true);
        add.icon(Icons.PLUS);
        add.setId("nexus.waypoints.add");
        top.add(add);
        content().add(top);

        Row filters = new Row(Theme.SPACE_4).align(Align.CENTER);
        sort = new Select<>(List.of(WaypointSort.values()), WaypointSort.NAME, s -> Lang.tr(s.langKey()));
        sort.setId("nexus.waypoints.sort");
        sort.onChange(s -> {
            sorting = s;
            refresh();
        });
        filters.add(sort);
        allWorlds = new Toggle(Lang.tr("vanta.waypoints.all_worlds"), false, on -> {
            everyWorld = on;
            refresh();
        });
        allWorlds.setId("nexus.waypoints.allWorlds");
        filters.add(allWorlds);
        content().add(filters);

        list.setId("nexus.waypoints.list");
        content().add(list);
        unsubscribe = store.onChange(this::refresh);
        refresh();
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public SearchField searchField() {
        return search;
    }

    public Select<WaypointSort> sortSelect() {
        return sort;
    }

    public Toggle allWorldsToggle() {
        return allWorlds;
    }

    public Button addButton() {
        return add;
    }

    /** The rows currently listed, top to bottom. */
    public List<WaypointRow> rows() {
        return List.copyOf(rows);
    }

    /** The row of a waypoint id, if listed. */
    public Optional<WaypointRow> row(String waypointId) {
        return rows.stream().filter(r -> r.waypoint.id().equals(waypointId)).findFirst();
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private String worldKey() {
        return game.worldKey();
    }

    private Optional<Vec3d> player() {
        return game.playerPosition();
    }

    /** Rebuilds the list from the store, the search text, the sort order and the world switch. */
    public void refresh() {
        list.clearChildren();
        rows.clear();
        String world = worldKey();
        boolean inWorld = !WorldKeys.isNone(world);
        add.setEnabled(inWorld && !store.isFull());
        add.setTooltip(inWorld ? null : Lang.tr("vanta.waypoints.empty.no_world.hint"));
        if (everyWorld) {
            Map<String, List<Waypoint>> grouped = new LinkedHashMap<>();
            List<Waypoint> matching = query.isEmpty() ? store.all() : store.searchAll(query);
            for (String key : store.worldKeys()) {
                List<Waypoint> inKey = new ArrayList<>();
                for (Waypoint waypoint : matching) {
                    if (waypoint.worldKey().equals(key)) {
                        inKey.add(waypoint);
                    }
                }
                if (!inKey.isEmpty()) {
                    grouped.put(key, inKey);
                }
            }
            if (grouped.isEmpty()) {
                list.add(empty(query.isEmpty() ? "vanta.waypoints.empty.none" : "vanta.waypoints.empty.no_match"));
            }
            for (Map.Entry<String, List<Waypoint>> group : grouped.entrySet()) {
                boolean current = group.getKey().equals(world);
                SectionHeader header = new SectionHeader(WorldKeys.displayName(group.getKey()))
                        .trailing(current ? Lang.tr("vanta.waypoints.current_world") : "");
                header.height(SectionHeader.HEIGHT + Theme.SPACE_3);
                list.add(header);
                Optional<Vec3d> origin = current ? player() : Optional.empty();
                for (Waypoint waypoint : WaypointStore.sort(group.getValue(), sorting, origin)) {
                    addRow(waypoint, current);
                }
            }
        } else if (!inWorld) {
            list.add(empty("vanta.waypoints.empty.no_world"));
        } else {
            List<Waypoint> matching = query.isEmpty() ? store.forWorld(world) : store.search(world, query);
            if (matching.isEmpty()) {
                list.add(empty(query.isEmpty() ? "vanta.waypoints.empty.none" : "vanta.waypoints.empty.no_match"));
            }
            for (Waypoint waypoint : WaypointStore.sort(matching, sorting, player())) {
                addRow(waypoint, true);
            }
        }
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    private UiNode empty(String key) {
        EmptyState state = new EmptyState(Icons.PIN, Lang.tr(key), Lang.tr(key + ".hint"));
        state.setId("nexus.waypoints.empty");
        state.height(96);
        return state;
    }

    private void addRow(Waypoint waypoint, boolean currentWorld) {
        WaypointRow row = new WaypointRow(waypoint, currentWorld);
        rows.add(row);
        list.add(row);
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Opens the add dialog with the player's position prefilled. */
    public Optional<WaypointDialog> openAddDialog() {
        String world = worldKey();
        if (WorldKeys.isNone(world) || lastContext == null) {
            return Optional.empty();
        }
        Vec3d here = player().orElse(Vec3d.ZERO);
        int color = WaypointColors.forIndex(store.forWorld(world).size());
        return Optional.of(WaypointDialog.open(lastContext, Lang.tr("vanta.waypoints.dialog.add_title"), "", here,
                WaypointCategories.DEFAULT, color, WaypointCategories.suggestions(store.all()),
                name -> validateName(world, name, null), values -> {
                    Optional<Waypoint> added = store.add(world, game.dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION),
                            values.name(), new Vec3d(values.x(), values.y(), values.z()), values.category(),
                            values.color());
                    if (added.isPresent()) {
                        services().notifications().post(NotificationKind.SUCCESS,
                                Lang.tr("vanta.waypoints.notification.added.title"),
                                Lang.tr("vanta.waypoints.notification.added.body", added.get().name()));
                    }
                    refresh();
                }));
    }

    /** Opens the edit dialog for a waypoint. */
    public Optional<WaypointDialog> openEditDialog(Waypoint waypoint) {
        if (lastContext == null) {
            return Optional.empty();
        }
        return Optional.of(WaypointDialog.open(lastContext, Lang.tr("vanta.waypoints.dialog.edit_title", waypoint.name()),
                waypoint.name(), waypoint.position(), waypoint.category(), waypoint.color(),
                WaypointCategories.suggestions(store.all()), name -> validateName(waypoint.worldKey(), name, waypoint.id()),
                values -> {
                    store.update(waypoint.withName(values.name()).withCoordinates(values.x(), values.y(), values.z())
                            .withCategory(values.category()).withColor(values.color()));
                    refresh();
                }));
    }

    /** Asks before deleting a waypoint. */
    public Dialog confirmDelete(Waypoint waypoint) {
        return Dialog.confirm(lastContext, Lang.tr("vanta.waypoints.delete.title", waypoint.name()),
                Lang.tr("vanta.waypoints.delete.body"), Lang.tr("vanta.common.delete"), Lang.tr("vanta.common.cancel"),
                true, () -> {
                    if (store.remove(waypoint.id())) {
                        services().notifications().post(NotificationKind.INFO,
                                Lang.tr("vanta.waypoints.notification.removed.title"),
                                Lang.tr("vanta.waypoints.notification.removed.body", waypoint.name()));
                    }
                    refresh();
                });
    }

    /** The translation key of a problem with a name in a world, or empty when fine. */
    public Optional<String> validateName(String world, String name, String excludingId) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            return Optional.of("vanta.waypoints.dialog.name_blank");
        }
        Optional<Waypoint> existing = store.find(world, Waypoint.sanitizeName(cleaned));
        if (existing.isPresent() && !existing.get().id().equals(excludingId)) {
            return Optional.of("vanta.waypoints.dialog.name_taken");
        }
        return Optional.empty();
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

    @Override
    public void dispose() {
        unsubscribe.run();
    }

    /** One waypoint: colour dot, name, category, live distance, enabled switch, edit and delete. */
    public final class WaypointRow extends UiNode {
        /** Row height. */
        public static final int HEIGHT = 22;
        private final Waypoint waypoint;
        private final boolean currentWorld;
        private final Toggle enabled;
        private final IconButton edit;
        private final IconButton delete;

        WaypointRow(Waypoint waypoint, boolean currentWorld) {
            this.waypoint = waypoint;
            this.currentWorld = currentWorld;
            setFocusable(true);
            setId("nexus.waypoints.row." + waypoint.id());
            setTooltip(Lang.tr("vanta.waypoints.row.tooltip", waypoint.x() < 0 ? (int) Math.floor(waypoint.x())
                    : (int) waypoint.x(), (int) Math.floor(waypoint.y()), (int) Math.floor(waypoint.z()),
                    waypoint.dimension()));
            enabled = new Toggle(waypoint.enabled(), on -> store.setEnabled(waypoint.id(), on));
            enabled.setId("nexus.waypoints.toggle." + waypoint.id());
            add(enabled);
            edit = new IconButton(Icons.EDIT, () -> openEditDialog(waypoint)).sizes(18, 9);
            edit.setTooltip(Lang.tr("vanta.common.edit"));
            edit.setId("nexus.waypoints.edit." + waypoint.id());
            add(edit);
            delete = new IconButton(Icons.TRASH, () -> confirmDelete(waypoint)).sizes(18, 9);
            delete.setTooltip(Lang.tr("vanta.common.delete"));
            delete.setId("nexus.waypoints.delete." + waypoint.id());
            add(delete);
        }

        public Waypoint waypoint() {
            return waypoint;
        }

        public Toggle enabledToggle() {
            return enabled;
        }

        public IconButton editButton() {
            return edit;
        }

        public IconButton deleteButton() {
            return delete;
        }

        /** The distance text shown right now ("842 m", or "n/a" outside the waypoint's world). */
        public String distanceText() {
            Optional<Vec3d> origin = currentWorld ? player() : Optional.empty();
            return origin.map(o -> WaypointMarkers.formatDistance(waypoint.distanceTo(o)))
                    .orElse(Lang.tr("vanta.common.not_available"));
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(200, HEIGHT);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            int x = b.right() - Theme.SPACE_2;
            delete.setBounds(x - 18, b.y() + (b.h() - 18) / 2, 18, 18);
            delete.layout(ctx);
            x -= 18 + Theme.SPACE_2;
            edit.setBounds(x - 18, b.y() + (b.h() - 18) / 2, 18, 18);
            edit.layout(ctx);
            x -= 18 + Theme.SPACE_3;
            enabled.setBounds(x - Toggle.TRACK_W, b.y() + (b.h() - Toggle.TRACK_H) / 2, Toggle.TRACK_W, Toggle.TRACK_H);
            enabled.layout(ctx);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            if (isHovered() || isFocused()) {
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.surface3(), 0.9f));
            }
            if (isFocused()) {
                canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
            int dot = 8;
            int x = b.x() + Theme.SPACE_2;
            canvas.fillRounded(x, b.y() + (b.h() - dot) / 2, dot, dot, Theme.RADIUS_MD, waypoint.color());
            x += dot + Theme.SPACE_3;
            int lh = canvas.lineHeight(FontKind.UI);
            int textY = b.y() + (b.h() - lh) / 2;
            int controlsLeft = enabled.bounds().x() - Theme.SPACE_3;
            String distance = distanceText();
            int distanceW = canvas.textWidth(distance, FontKind.UI);
            int distanceX = controlsLeft - distanceW;
            canvas.text(distance, distanceX, textY, theme.textSecondary(), FontKind.UI, false);
            int textRight = distanceX - Theme.SPACE_3;
            int nameColor = waypoint.enabled() ? theme.textPrimary() : theme.textMuted();
            String name = canvas.textClipped(waypoint.name(), Math.max(0, textRight - x), FontKind.UI_BOLD);
            canvas.text(name, x, textY, nameColor, FontKind.UI_BOLD, false);
            int nameW = canvas.textWidth(name, FontKind.UI_BOLD);
            int categoryX = x + nameW + Theme.SPACE_3;
            int categoryW = textRight - categoryX;
            if (categoryW > 20) {
                canvas.text(canvas.textClipped(waypoint.category(), categoryW, FontKind.UI), categoryX, textY,
                        theme.textMuted(), FontKind.UI, false);
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                openEditDialog(waypoint);
                return true;
            }
            if (key == Keys.DELETE) {
                confirmDelete(waypoint);
                return true;
            }
            return false;
        }
    }
}

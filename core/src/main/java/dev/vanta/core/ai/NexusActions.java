package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudRect;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.SettingsStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The only things the assistant can do. Every action the model returns is validated against the live registries (HUD
 * layout and widget types, allowed settings, profiles, presets, waypoints, lab features) before it runs; anything else
 * is reported as a {@link RejectedAction}. The same methods also build the JSON schema sent to llama-server, so the
 * model can only name things that exist, and the live lists the system prompt shows.
 * <p>
 * Render-thread only, like every core service.
 */
public final class NexusActions {
    /** Setting categories the assistant may change. Keybinds, privacy, network and the rest are never touched. */
    public static final Set<SettingCategory> ALLOWED_CATEGORIES = Collections.unmodifiableSet(EnumSet.of(
            SettingCategory.VIDEO, SettingCategory.HUD, SettingCategory.PERFORMANCE, SettingCategory.ACCESSIBILITY));
    /** Reference screen (GUI pixels) used to turn x/y fractions into anchor offsets. */
    public static final int REFERENCE_WIDTH = 854;
    /** Reference screen height. */
    public static final int REFERENCE_HEIGHT = 480;
    /** Margin kept from the screen edge when placing by fraction. */
    public static final int MARGIN = 4;
    /** Smallest {@code hud.set} opacity. */
    public static final double MIN_OPACITY = 0.1;

    /** Every action type, as the model must spell it. */
    public static final List<String> TYPES = List.of("hud.set", "hud.only", "hud.layout.save", "hud.layout.load",
            "hud.preset", "profile.switch", "profile.create", "perf.preset", "perf.smartBoost", "setting.set",
            "waypoint.add", "waypoint.remove", "waypoint.toggle", "lab.set");

    /** Result of a turn's actions. */
    public record Outcome(List<AppliedAction> applied, List<RejectedAction> rejected) {
        public Outcome {
            applied = List.copyOf(applied);
            rejected = List.copyOf(rejected);
        }
    }

    /** Internal: validation refused an action. */
    private static final class Rejection extends Exception {
        private static final long serialVersionUID = 1L;
        private final transient RejectedAction rejected;

        Rejection(String type, RejectedAction.Reason reason, String detail) {
            super(reason + ": " + detail, null, false, false);
            this.rejected = new RejectedAction(type, reason, detail);
        }
    }

    private final HudStore hud;
    private final SettingsStore settings;
    private final ProfileManager profiles;
    private final Predicate<String> profileActivator;
    private final PerformanceCenter performance;
    private final Runnable smartBoostRun;
    private volatile WaypointLookup waypoints = WaypointLookup.none();
    private volatile NexusWaypointActions waypointActions = NexusWaypointActions.none();
    private volatile LabToggle lab = LabToggle.none();

    /**
     * @param profileActivator activates a profile by id with the usual side effects ({@code VantaServices.activateProfile})
     * @param smartBoostRun    runs Smart Boost's measurement ({@code SmartBoostTuner.retune})
     */
    public NexusActions(HudStore hud, SettingsStore settings, ProfileManager profiles,
                        Predicate<String> profileActivator, PerformanceCenter performance, Runnable smartBoostRun) {
        this.hud = Objects.requireNonNull(hud, "hud");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.profileActivator = Objects.requireNonNull(profileActivator, "profileActivator");
        this.performance = Objects.requireNonNull(performance, "performance");
        this.smartBoostRun = Objects.requireNonNull(smartBoostRun, "smartBoostRun");
    }

    /** Wires the waypoints store (the integrator calls this; until then waypoint actions are refused). */
    public void setWaypoints(WaypointLookup lookup, NexusWaypointActions actions) {
        this.waypoints = Objects.requireNonNull(lookup, "lookup");
        this.waypointActions = Objects.requireNonNull(actions, "actions");
    }

    /** Wires the lab features (the integrator calls this; until then lab actions are refused). */
    public void setLab(LabToggle toggle) {
        this.lab = Objects.requireNonNull(toggle, "toggle");
    }

    public WaypointLookup waypoints() {
        return waypoints;
    }

    public LabToggle lab() {
        return lab;
    }

    // ---- live registries ----------------------------------------------------------------------------------------

    /** Widget ids of the live layout followed by the widget types not on the HUD (usable to add them). */
    public List<String> hudElementIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        HudLayout layout = hud.layout();
        for (HudWidgetState widget : layout.widgets()) {
            ids.add(widget.id());
        }
        for (HudWidgetType type : HudWidgetType.values()) {
            if (type != HudWidgetType.CROSSHAIR && layout.byType(type).isEmpty()) {
                ids.add(type.id());
            }
        }
        return List.copyOf(ids);
    }

    /** Widget types not on the HUD yet. */
    public List<String> addableWidgetTypes() {
        List<String> out = new ArrayList<>();
        HudLayout layout = hud.layout();
        for (HudWidgetType type : HudWidgetType.values()) {
            if (type != HudWidgetType.CROSSHAIR && layout.byType(type).isEmpty()) {
                out.add(type.id());
            }
        }
        return out;
    }

    /** Settings in the allowed categories that are values (no buttons, no key captures). */
    public List<Setting<?>> allowedSettings() {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> setting : settings.registry().all()) {
            if (isAllowed(setting)) {
                out.add(setting);
            }
        }
        return out;
    }

    /** Ids of {@link #allowedSettings()}. */
    public List<String> allowedSettingIds() {
        List<String> out = new ArrayList<>();
        for (Setting<?> setting : allowedSettings()) {
            out.add(setting.id());
        }
        return out;
    }

    /** True when the assistant may change this setting. */
    public static boolean isAllowed(Setting<?> setting) {
        return ALLOWED_CATEGORIES.contains(setting.category()) && setting.kind() != SettingKind.ACTION
                && setting.kind() != SettingKind.KEY && !setting.needsRestart();
    }

    /** Names of every profile. */
    public List<String> profileNames() {
        List<String> out = new ArrayList<>();
        for (Profile profile : profiles.list()) {
            out.add(profile.name());
        }
        return out;
    }

    /** Display names of every saved HUD layout (built-in presets translated, user presets as named). */
    public List<String> layoutNames() {
        List<String> out = new ArrayList<>();
        for (HudPreset preset : hud.allPresets()) {
            out.add(Lang.tr(preset.langKey()));
        }
        return out;
    }

    /** {@link PerformancePreset} ids. */
    public List<String> perfPresetIds() {
        List<String> out = new ArrayList<>();
        for (PerformancePreset preset : PerformancePreset.values()) {
            out.add(preset.id());
        }
        return out;
    }

    /** {@link NexusHudPreset} ids. */
    public List<String> hudPresetIds() {
        return NexusHudPreset.ids();
    }

    /** Waypoint names of the current world. */
    public List<String> waypointNames() {
        return waypoints.names();
    }

    /** Lab feature ids. */
    public List<String> labFeatures() {
        return lab.features();
    }

    // ---- schema --------------------------------------------------------------------------------------------------

    /**
     * The JSON schema of a reply: {@code {"message": string, "actions": [action...]}} where every action variant
     * enumerates the live ids it may name. Variants whose registry is empty (no waypoints, no lab features) are left
     * out, so the model cannot propose them.
     */
    public JsonObject schema() {
        JsonArray variants = new JsonArray();
        List<String> elements = hudElementIds();
        variants.add(action("hud.set", List.of("element"),
                prop("element", enumOf(elements)), prop("visible", type("boolean")),
                prop("anchor", enumOf(anchorIds())), prop("x", type("number")), prop("y", type("number")),
                prop("scale", type("number")), prop("opacity", type("number"))));
        JsonObject elementsArray = type("array");
        elementsArray.add("items", enumOf(elements));
        variants.add(action("hud.only", List.of("elements"), prop("elements", elementsArray)));
        variants.add(action("hud.layout.save", List.of("name"), prop("name", type("string"))));
        List<String> layouts = layoutNames();
        variants.add(action("hud.layout.load", List.of("name"),
                prop("name", layouts.isEmpty() ? type("string") : enumOf(layouts))));
        variants.add(action("hud.preset", List.of("preset"), prop("preset", enumOf(hudPresetIds()))));
        List<String> profileNames = profileNames();
        if (!profileNames.isEmpty()) {
            variants.add(action("profile.switch", List.of("name"), prop("name", enumOf(profileNames))));
        }
        variants.add(action("profile.create", List.of("name"), prop("name", type("string")),
                prop("fromCurrent", type("boolean"))));
        variants.add(action("perf.preset", List.of("preset"), prop("preset", enumOf(perfPresetIds()))));
        variants.add(action("perf.smartBoost", List.of("run"), prop("run", type("boolean"))));
        List<String> settingIds = allowedSettingIds();
        JsonObject value = new JsonObject();
        JsonArray anyOf = new JsonArray();
        anyOf.add(type("boolean"));
        anyOf.add(type("number"));
        anyOf.add(type("string"));
        value.add("anyOf", anyOf);
        variants.add(action("setting.set", List.of("id", "value"), prop("id", enumOf(settingIds)),
                prop("value", value)));
        variants.add(action("waypoint.add", List.of("name", "x", "y", "z"), prop("name", type("string")),
                prop("x", type("number")), prop("y", type("number")), prop("z", type("number")),
                prop("category", type("string"))));
        List<String> waypointNames = waypointNames();
        if (!waypointNames.isEmpty()) {
            variants.add(action("waypoint.remove", List.of("name"), prop("name", enumOf(waypointNames))));
            variants.add(action("waypoint.toggle", List.of("name", "enabled"), prop("name", enumOf(waypointNames)),
                    prop("enabled", type("boolean"))));
        }
        List<String> features = labFeatures();
        if (!features.isEmpty()) {
            variants.add(action("lab.set", List.of("feature", "enabled"), prop("feature", enumOf(features)),
                    prop("enabled", type("boolean"))));
        }
        JsonObject actions = type("array");
        JsonObject items = new JsonObject();
        items.add("anyOf", variants);
        actions.add("items", items);

        JsonObject root = type("object");
        JsonObject properties = new JsonObject();
        properties.add("message", type("string"));
        properties.add("actions", actions);
        root.add("properties", properties);
        root.add("required", strings(List.of("message", "actions")));
        root.addProperty("additionalProperties", false);
        return root;
    }

    private static List<String> anchorIds() {
        List<String> out = new ArrayList<>();
        for (HudAnchor anchor : HudAnchor.values()) {
            out.add(anchor.id());
        }
        return out;
    }

    private record Prop(String name, JsonObject schema) {
    }

    private static Prop prop(String name, JsonObject schema) {
        return new Prop(name, schema);
    }

    private static JsonObject action(String type, List<String> required, Prop... props) {
        JsonObject o = type("object");
        JsonObject properties = new JsonObject();
        JsonObject typeConst = new JsonObject();
        typeConst.addProperty("const", type);
        properties.add("type", typeConst);
        for (Prop prop : props) {
            properties.add(prop.name(), prop.schema());
        }
        o.add("properties", properties);
        List<String> req = new ArrayList<>();
        req.add("type");
        req.addAll(required);
        o.add("required", strings(req));
        o.addProperty("additionalProperties", false);
        return o;
    }

    private static JsonObject type(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    private static JsonObject enumOf(List<String> values) {
        JsonObject o = new JsonObject();
        o.add("enum", strings(values));
        return o;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    // ---- execution -----------------------------------------------------------------------------------------------

    /** Validates and applies every action in order; rejected ones are reported, the rest still run. */
    public Outcome execute(JsonArray actions, NexusUndo.Turn turn) {
        List<AppliedAction> applied = new ArrayList<>();
        List<RejectedAction> rejected = new ArrayList<>();
        if (actions == null) {
            return new Outcome(applied, rejected);
        }
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) {
                rejected.add(new RejectedAction("?", RejectedAction.Reason.UNKNOWN_TYPE, element.toString()));
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            String type = str(action, "type").orElse("?");
            try {
                applied.add(apply(type, action, turn));
            } catch (Rejection r) {
                rejected.add(r.rejected);
            } catch (RuntimeException e) {
                rejected.add(new RejectedAction(type, RejectedAction.Reason.FAILED, String.valueOf(e.getMessage())));
            }
        }
        return new Outcome(applied, rejected);
    }

    /** Validates and applies one action. */
    public Optional<AppliedAction> applyOne(JsonObject action, NexusUndo.Turn turn, List<RejectedAction> rejected) {
        String type = str(action, "type").orElse("?");
        try {
            return Optional.of(apply(type, action, turn));
        } catch (Rejection r) {
            rejected.add(r.rejected);
            return Optional.empty();
        }
    }

    private AppliedAction apply(String type, JsonObject a, NexusUndo.Turn turn) throws Rejection {
        return switch (type) {
            case "hud.set" -> hudSet(a);
            case "hud.only" -> hudOnly(a);
            case "hud.layout.save" -> layoutSave(a);
            case "hud.layout.load" -> layoutLoad(a);
            case "hud.preset" -> hudPreset(a);
            case "profile.switch" -> profileSwitch(a);
            case "profile.create" -> profileCreate(a, turn);
            case "perf.preset" -> perfPreset(a);
            case "perf.smartBoost" -> perfSmartBoost(a);
            case "setting.set" -> settingSet(a);
            case "waypoint.add" -> waypointAdd(a, turn);
            case "waypoint.remove" -> waypointRemove(a);
            case "waypoint.toggle" -> waypointToggle(a, turn);
            case "lab.set" -> labSet(a, turn);
            default -> throw new Rejection(type, RejectedAction.Reason.UNKNOWN_TYPE, type);
        };
    }

    // ---- hud -----------------------------------------------------------------------------------------------------

    /** A resolved element reference: an existing widget or a new one of a known type. */
    private record ElementRef(HudWidgetState state, boolean isNew) {
    }

    private ElementRef resolveElement(HudLayout layout, String name, String type) throws Rejection {
        String wanted = name.trim();
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.id().equalsIgnoreCase(wanted)) {
                return new ElementRef(widget, false);
            }
        }
        Optional<HudWidgetType> widgetType = HudWidgetType.fromId(wanted);
        if (widgetType.isPresent()) {
            List<HudWidgetState> ofType = layout.byType(widgetType.get());
            if (!ofType.isEmpty()) {
                return new ElementRef(ofType.get(0), false);
            }
            return new ElementRef(HudWidgetState.defaults(layout.nextId(widgetType.get()), widgetType.get()), true);
        }
        throw new Rejection(type, RejectedAction.Reason.UNKNOWN_ELEMENT, wanted);
    }

    private AppliedAction hudSet(JsonObject a) throws Rejection {
        String type = "hud.set";
        String element = require(a, "element", type);
        HudLayout layout = hud.layout();
        ElementRef ref = resolveElement(layout, element, type);
        HudWidgetState state = ref.state();
        List<String> changes = new ArrayList<>();
        Optional<Boolean> visible = bool(a, "visible", type);
        if (visible.isPresent()) {
            state = state.withEnabled(visible.get());
            changes.add("visible=" + visible.get());
        }
        Optional<HudAnchor> anchor = Optional.empty();
        if (a.has("anchor")) {
            String anchorId = require(a, "anchor", type);
            anchor = HudAnchor.fromId(anchorId);
            if (anchor.isEmpty()) {
                throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, "anchor " + anchorId);
            }
        }
        OptionalDouble x = number(a, "x", type);
        OptionalDouble y = number(a, "y", type);
        if (state.type().movable() && (anchor.isPresent() || x.isPresent() || y.isPresent())) {
            int w = state.scaledWidth();
            int h = state.scaledHeight();
            HudRect current = layout.resolveRect(state, REFERENCE_WIDTH, REFERENCE_HEIGHT);
            int px = x.isPresent() ? fractionToPixel(x.getAsDouble(), REFERENCE_WIDTH, w) : current.x();
            int py = y.isPresent() ? fractionToPixel(y.getAsDouble(), REFERENCE_HEIGHT, h) : current.y();
            HudAnchor target;
            if (anchor.isPresent()) {
                target = anchor.get();
                if (x.isEmpty() && y.isEmpty()) {
                    state = state.withPosition(target, target.column() == 1 ? 0 : MARGIN,
                            target.row() == 1 ? 0 : MARGIN);
                    changes.add("anchor=" + target.id());
                } else {
                    state = HudLayout.placed(state.withPosition(target, 0, 0), px, py, REFERENCE_WIDTH,
                            REFERENCE_HEIGHT);
                    changes.add("anchor=" + target.id() + " position=" + px + "," + py);
                }
            } else {
                target = HudAnchor.nearest(new HudRect(px, py, w, h), REFERENCE_WIDTH, REFERENCE_HEIGHT);
                state = HudLayout.placed(state.withPosition(target, 0, 0), px, py, REFERENCE_WIDTH, REFERENCE_HEIGHT);
                changes.add("position=" + px + "," + py + " (" + target.id() + ")");
            }
        }
        OptionalDouble scale = number(a, "scale", type);
        if (scale.isPresent() && state.type().supportsScale()) {
            double clamped = clamp(scale.getAsDouble(), HudWidgetState.MIN_SCALE, HudWidgetState.MAX_SCALE);
            state = state.withScale(clamped);
            changes.add("scale=" + format(clamped));
        }
        OptionalDouble opacity = number(a, "opacity", type);
        if (opacity.isPresent()) {
            double clamped = clamp(opacity.getAsDouble(), MIN_OPACITY, 1.0);
            state = state.withOpacity(clamped);
            changes.add("opacity=" + format(clamped));
        }
        if (changes.isEmpty() && !ref.isNew()) {
            throw new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "visible, anchor, x, y, scale or opacity");
        }
        if (ref.isNew()) {
            if (x.isEmpty() && y.isEmpty() && anchor.isEmpty()) {
                state = layout.placedClear(state, REFERENCE_WIDTH, REFERENCE_HEIGHT);
            }
            changes.add(0, "added");
        }
        hud.setLayout(layout.with(state));
        return new AppliedAction(type, state.id(), String.join(", ", changes));
    }

    private AppliedAction hudOnly(JsonObject a) throws Rejection {
        String type = "hud.only";
        JsonElement elements = a.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            throw new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "elements");
        }
        HudLayout layout = hud.layout();
        Set<String> keep = new LinkedHashSet<>();
        List<HudWidgetState> added = new ArrayList<>();
        for (JsonElement e : elements.getAsJsonArray()) {
            if (!e.isJsonPrimitive()) {
                throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, e.toString());
            }
            ElementRef ref = resolveElement(layout, e.getAsString(), type);
            keep.add(ref.state().id());
            if (ref.isNew()) {
                added.add(ref.state());
            }
        }
        HudLayout result = layout;
        for (HudWidgetState widget : layout.widgets()) {
            boolean enabled = keep.contains(widget.id()) || widget.type() == HudWidgetType.CROSSHAIR;
            if (widget.enabled() != enabled) {
                result = result.with(widget.withEnabled(enabled));
            }
        }
        for (HudWidgetState widget : added) {
            result = result.with(result.placedClear(widget, REFERENCE_WIDTH, REFERENCE_HEIGHT));
        }
        hud.setLayout(result);
        return new AppliedAction(type, String.join(", ", keep), keep.size() + " shown");
    }

    private AppliedAction layoutSave(JsonObject a) throws Rejection {
        String type = "hud.layout.save";
        String name = require(a, "name", type);
        if (name.length() > HudStore.MAX_PRESET_NAME) {
            name = name.substring(0, HudStore.MAX_PRESET_NAME);
        }
        for (HudPreset preset : hud.userPresets()) {
            if (preset.name().equalsIgnoreCase(name)) {
                hud.updateUserPreset(preset.id());
                return new AppliedAction(type, preset.name(), "updated");
            }
        }
        HudPreset saved = hud.saveUserPreset(name);
        return new AppliedAction(type, saved.name(), "saved");
    }

    private AppliedAction layoutLoad(JsonObject a) throws Rejection {
        String type = "hud.layout.load";
        String name = require(a, "name", type);
        for (HudPreset preset : hud.allPresets()) {
            if (preset.id().equalsIgnoreCase(name) || Lang.tr(preset.langKey()).equalsIgnoreCase(name)) {
                hud.applyPreset(preset);
                return new AppliedAction(type, Lang.tr(preset.langKey()), "loaded");
            }
        }
        throw new Rejection(type, RejectedAction.Reason.UNKNOWN_LAYOUT, name);
    }

    private AppliedAction hudPreset(JsonObject a) throws Rejection {
        String type = "hud.preset";
        String id = require(a, "preset", type);
        NexusHudPreset preset = NexusHudPreset.fromId(id).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.UNKNOWN_PRESET, id));
        hud.setLayout(preset.apply(hud.layout()));
        return new AppliedAction(type, preset.id(), Lang.tr(preset.langKey()));
    }

    // ---- profiles ------------------------------------------------------------------------------------------------

    private Optional<Profile> findProfile(String name) {
        String wanted = name.trim();
        for (Profile profile : profiles.list()) {
            if (profile.id().equalsIgnoreCase(wanted) || profile.name().equalsIgnoreCase(wanted)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    private AppliedAction profileSwitch(JsonObject a) throws Rejection {
        String type = "profile.switch";
        String name = require(a, "name", type);
        Profile profile = findProfile(name).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.UNKNOWN_PROFILE, name));
        if (!profileActivator.test(profile.id())) {
            throw new Rejection(type, RejectedAction.Reason.FAILED, profile.name());
        }
        return new AppliedAction(type, profile.name(), "active");
    }

    private AppliedAction profileCreate(JsonObject a, NexusUndo.Turn turn) throws Rejection {
        String type = "profile.create";
        String name = require(a, "name", type);
        if (name.length() > Profile.MAX_NAME) {
            name = name.substring(0, Profile.MAX_NAME);
        }
        if (findProfile(name).isPresent()) {
            throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, name + " exists already");
        }
        Profile created = profiles.createFromCurrent(name, Profile.DEFAULT_ICON);
        String id = created.id();
        profileActivator.test(id);
        turn.addRestore(() -> profiles.delete(id));
        return new AppliedAction(type, created.name(), "created from the current setup");
    }

    // ---- performance ---------------------------------------------------------------------------------------------

    private AppliedAction perfPreset(JsonObject a) throws Rejection {
        String type = "perf.preset";
        String id = require(a, "preset", type);
        PerformancePreset preset = PerformancePreset.fromId(id.toLowerCase(Locale.ROOT)).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.UNKNOWN_PRESET, id));
        performance.applyPreset(preset);
        return new AppliedAction(type, preset.id(), Lang.tr(preset.langKey()));
    }

    private AppliedAction perfSmartBoost(JsonObject a) throws Rejection {
        String type = "perf.smartBoost";
        boolean run = bool(a, "run", type).orElse(true);
        if (!run) {
            throw new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "run: true");
        }
        smartBoostRun.run();
        return new AppliedAction(type, "smart boost", "measuring");
    }

    // ---- settings ------------------------------------------------------------------------------------------------

    private AppliedAction settingSet(JsonObject a) throws Rejection {
        String type = "setting.set";
        String id = require(a, "id", type);
        Setting<?> setting = settings.registry().find(id).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.UNKNOWN_SETTING, id));
        if (!isAllowed(setting)) {
            throw new Rejection(type, RejectedAction.Reason.FORBIDDEN_SETTING, id);
        }
        JsonElement value = a.get("value");
        if (value == null || value.isJsonNull()) {
            throw new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "value");
        }
        if (!value.isJsonPrimitive()) {
            throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, value.toString());
        }
        Optional<?> decoded = setting.decode(value);
        if (decoded.isEmpty()) {
            throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, id + " = " + value.getAsString()
                    + " (" + allowedValues(setting) + ")");
        }
        settings.setRaw(setting, decoded.get());
        Object now = settings.get(setting);
        return new AppliedAction(type, id, String.valueOf(now instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                : now));
    }

    /** Readable allowed values of a setting for the prompt and for rejection hints. */
    public static String allowedValues(Setting<?> setting) {
        return switch (setting.kind()) {
            case BOOL -> "true or false";
            case INT_RANGE -> "integer " + (long) setting.min() + ".." + (long) setting.max();
            case DOUBLE_RANGE -> "number " + format(setting.min()) + ".." + format(setting.max());
            case ENUM -> {
                List<String> names = new ArrayList<>();
                for (Object option : setting.options()) {
                    names.add(option instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                            : String.valueOf(option).toLowerCase(Locale.ROOT));
                }
                yield "one of " + String.join(", ", names);
            }
            case COLOR -> "colour #AARRGGBB";
            case STRING -> "text";
            case KEY -> "key name";
            case ACTION -> "button";
        };
    }

    /** The current value of a setting, lower-case for enums. */
    public String currentValue(Setting<?> setting) {
        Object value = settings.get(setting);
        if (value instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        if (value instanceof String s && setting.kind() == SettingKind.ENUM) {
            return s.toLowerCase(Locale.ROOT);
        }
        if (value instanceof Double d) {
            return format(d);
        }
        return String.valueOf(value);
    }

    // ---- waypoints -----------------------------------------------------------------------------------------------

    private AppliedAction waypointAdd(JsonObject a, NexusUndo.Turn turn) throws Rejection {
        String type = "waypoint.add";
        String name = require(a, "name", type);
        double x = number(a, "x", type).orElseThrow(() -> new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "x"));
        double y = number(a, "y", type).orElseThrow(() -> new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "y"));
        double z = number(a, "z", type).orElseThrow(() -> new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "z"));
        Optional<String> category = str(a, "category").filter(s -> !s.isBlank());
        NexusWaypointActions actions = waypointActions;
        if (!actions.add(name, x, y, z, category)) {
            throw new Rejection(type, RejectedAction.Reason.NOT_AVAILABLE, name);
        }
        turn.addRestore(() -> actions.remove(name));
        return new AppliedAction(type, name, (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", "
                + (int) Math.floor(z));
    }

    private AppliedAction waypointRemove(JsonObject a) throws Rejection {
        String type = "waypoint.remove";
        String name = require(a, "name", type);
        if (!waypoints.has(name)) {
            throw new Rejection(type, RejectedAction.Reason.UNKNOWN_WAYPOINT, name);
        }
        if (!waypointActions.remove(name)) {
            throw new Rejection(type, RejectedAction.Reason.FAILED, name);
        }
        return new AppliedAction(type, name, "removed");
    }

    private AppliedAction waypointToggle(JsonObject a, NexusUndo.Turn turn) throws Rejection {
        String type = "waypoint.toggle";
        String name = require(a, "name", type);
        boolean enabled = bool(a, "enabled", type).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "enabled"));
        if (!waypoints.has(name)) {
            throw new Rejection(type, RejectedAction.Reason.UNKNOWN_WAYPOINT, name);
        }
        NexusWaypointActions actions = waypointActions;
        if (!actions.toggle(name, enabled)) {
            throw new Rejection(type, RejectedAction.Reason.FAILED, name);
        }
        turn.addRestore(() -> actions.toggle(name, !enabled));
        return new AppliedAction(type, name, enabled ? "enabled" : "disabled");
    }

    // ---- lab -----------------------------------------------------------------------------------------------------

    private AppliedAction labSet(JsonObject a, NexusUndo.Turn turn) throws Rejection {
        String type = "lab.set";
        String feature = require(a, "feature", type);
        boolean enabled = bool(a, "enabled", type).orElseThrow(() ->
                new Rejection(type, RejectedAction.Reason.MISSING_FIELD, "enabled"));
        LabToggle toggle = lab;
        String id = null;
        for (String candidate : toggle.features()) {
            if (candidate.equalsIgnoreCase(feature.trim())) {
                id = candidate;
            }
        }
        if (id == null) {
            throw new Rejection(type, RejectedAction.Reason.UNKNOWN_FEATURE, feature);
        }
        boolean before = toggle.isEnabled(id);
        if (!toggle.set(id, enabled)) {
            throw new Rejection(type, RejectedAction.Reason.FAILED, id);
        }
        String featureId = id;
        turn.addRestore(() -> toggle.set(featureId, before));
        return new AppliedAction(type, id, enabled ? "on" : "off");
    }

    // ---- field helpers -------------------------------------------------------------------------------------------

    private static String require(JsonObject a, String key, String type) throws Rejection {
        Optional<String> value = str(a, key);
        if (value.isEmpty() || value.get().isBlank()) {
            throw new Rejection(type, RejectedAction.Reason.MISSING_FIELD, key);
        }
        return value.get().trim();
    }

    static Optional<String> str(JsonObject a, String key) {
        JsonElement e = a.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(e.getAsString());
    }

    private static Optional<Boolean> bool(JsonObject a, String key, String type) throws Rejection {
        JsonElement e = a.get(key);
        if (e == null || e.isJsonNull()) {
            return Optional.empty();
        }
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return Optional.of(p.getAsBoolean());
            }
            if (p.isString()) {
                String s = p.getAsString().trim().toLowerCase(Locale.ROOT);
                if (s.equals("true") || s.equals("on") || s.equals("yes")) {
                    return Optional.of(true);
                }
                if (s.equals("false") || s.equals("off") || s.equals("no")) {
                    return Optional.of(false);
                }
            }
        }
        throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, key + " = " + e);
    }

    private static OptionalDouble number(JsonObject a, String key, String type) throws Rejection {
        JsonElement e = a.get(key);
        if (e == null || e.isJsonNull()) {
            return OptionalDouble.empty();
        }
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                    return OptionalDouble.of(d);
                }
            } else if (p.isString()) {
                try {
                    return OptionalDouble.of(Double.parseDouble(p.getAsString().trim()));
                } catch (NumberFormatException ignored) {
                    // falls through to the rejection
                }
            }
        }
        throw new Rejection(type, RejectedAction.Reason.INVALID_VALUE, key + " = " + e);
    }

    private static int fractionToPixel(double fraction, int screen, int widget) {
        double f = clamp(fraction, 0.0, 1.0);
        int centre = (int) Math.round(f * screen);
        int position = centre - widget / 2;
        int max = Math.max(MARGIN, screen - widget - MARGIN);
        return Math.max(MARGIN, Math.min(max, position));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static String format(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }
}

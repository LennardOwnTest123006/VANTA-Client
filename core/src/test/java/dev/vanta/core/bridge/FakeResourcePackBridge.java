package dev.vanta.core.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link ResourcePackBridge} with a few realistic packs.
 */
public final class FakeResourcePackBridge implements ResourcePackBridge {
    private final List<PackInfo> available = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();
    private final List<String> applied = new ArrayList<>();
    /** Recorded actions. */
    public final List<String> actions = new ArrayList<>();

    public FakeResourcePackBridge() {
        available.add(new PackInfo("vanilla", "Default", "The default look and feel of Minecraft", true, "Compatible",
                true, true, 0, "built-in", false));
        available.add(new PackInfo("programmer_art", "Programmer Art", "The classic look of Minecraft", true,
                "Compatible", false, false, -1, "built-in", false));
        available.add(new PackInfo("high_contrast", "High Contrast", "Enhances UI contrast", true, "Compatible",
                false, false, -1, "built-in", false));
        available.add(new PackInfo("file/Faithful.zip", "Faithful 32x", "Double resolution textures", false,
                "Made for an older version of Minecraft", false, false, -1, "file", true));
        selected.add("vanilla");
        applied.add("vanilla");
    }

    @Override
    public List<PackInfo> packs() {
        List<PackInfo> out = new ArrayList<>();
        for (PackInfo info : available) {
            int position = selected.indexOf(info.id());
            out.add(new PackInfo(info.id(), info.title(), info.description(), info.compatible(),
                    info.compatibilityText(), position >= 0, info.required(), position, info.source(), info.hasIcon()));
        }
        return out;
    }

    @Override
    public boolean setEnabled(String id, boolean enabled) {
        PackInfo info = available.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        if (info == null || info.required()) {
            return false;
        }
        if (enabled && !selected.contains(id)) {
            selected.add(id);
            return true;
        }
        if (!enabled && selected.contains(id)) {
            selected.remove(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean move(String id, int delta) {
        int index = selected.indexOf(id);
        if (index < 0) {
            return false;
        }
        int target = Math.max(0, Math.min(selected.size() - 1, index + delta));
        if (target == index) {
            return false;
        }
        selected.remove(index);
        selected.add(target, id);
        return true;
    }

    @Override
    public boolean hasPendingChanges() {
        return !selected.equals(applied);
    }

    @Override
    public void apply() {
        applied.clear();
        applied.addAll(selected);
        actions.add("apply");
    }

    @Override
    public void discard() {
        selected.clear();
        selected.addAll(applied);
    }

    @Override
    public void openPackFolder() {
        actions.add("openPackFolder");
    }

    @Override
    public void openVanillaScreen() {
        actions.add("openVanillaScreen");
    }
}

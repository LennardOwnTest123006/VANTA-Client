package dev.vanta.core.preview.previews;

import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.bridge.ResourcePackBridge;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory resource pack bridge for previews with a varied pack list (built-ins, files, a world pack and an
 * incompatible one). No pack claims an icon, so rows show the generated monogram tiles instead of a missing
 * texture placeholder; the behaviour mirrors the test fake.
 */
public final class PreviewResourcePackBridge implements ResourcePackBridge {
    private final List<PackInfo> available = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();
    private final List<String> applied = new ArrayList<>();

    public PreviewResourcePackBridge() {
        available.add(pack("vanilla", "Default", "The default look and feel of Minecraft", true, "Compatible", true,
                "built-in"));
        available.add(pack("programmer_art", "Programmer Art", "The classic look of Minecraft", true, "Compatible",
                false, "built-in"));
        available.add(pack("high_contrast", "High Contrast", "Enhances UI contrast", true, "Compatible", false,
                "built-in"));
        available.add(pack("file/Faithful 32x.zip", "Faithful 32x", "Double resolution textures that stay true to the "
                + "original art", true, "Compatible", false, "file"));
        available.add(pack("file/Clear Glass.zip", "Clear Glass", "Removes the streaks from glass blocks", true,
                "Compatible", false, "file"));
        available.add(pack("file/Old Lighting.zip", "Old Lighting", "Pre-1.20 lighting and sky colours", false,
                "Made for an older version of Minecraft", false, "file"));
        available.add(pack("world", "World Resources", "Resource pack bundled with the current world", true, "Compatible",
                false, "world"));
        selected.add("vanilla");
        applied.add("vanilla");
    }

    private static PackInfo pack(String id, String title, String description, boolean compatible, String compat,
                                 boolean required, String source) {
        return new PackInfo(id, title, description, compatible, compat, false, required, -1, source, false);
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
    }

    @Override
    public void discard() {
        selected.clear();
        selected.addAll(applied);
    }

    @Override
    public void openPackFolder() {
    }

    @Override
    public void openVanillaScreen() {
    }
}

package dev.vanta.client.bridge;

import dev.vanta.client.screen.VanillaScreenOpener;
import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.bridge.ResourcePackBridge;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.ui.TextureRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.Util;

/**
 * {@link ResourcePackBridge} over the game's {@link PackRepository}.
 * <p>
 * Changes are staged in a local selection list (lowest priority first, like {@code getSelectedIds()}) until
 * {@link #apply()} hands it to the repository and to {@code Options.updateResourcePacks}, which saves
 * {@code options.txt} and reloads resources when the selection changed — the exact path the vanilla pack screen uses.
 * If the live selection changes elsewhere (vanilla screen, server packs) the staged list is rebuilt from it.
 */
public final class MinecraftResourcePackBridge implements ResourcePackBridge {
    private final PackIcons icons = new PackIcons();
    private List<String> staged;
    private List<String> stagedBase;

    private static PackRepository repository() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getResourcePackRepository();
    }

    private List<String> staged(PackRepository repository) {
        List<String> live = new ArrayList<>(repository.getSelectedIds());
        if (staged == null || !live.equals(stagedBase)) {
            staged = new ArrayList<>(live);
            stagedBase = live;
        }
        return staged;
    }

    @Override
    public List<PackInfo> packs() {
        PackRepository repository = repository();
        if (repository == null) {
            return List.of();
        }
        List<String> selected = staged(repository);
        List<PackInfo> out = new ArrayList<>();
        for (Pack pack : repository.getAvailablePacks()) {
            int position = selected.indexOf(pack.getId());
            out.add(new PackInfo(pack.getId(), pack.getTitle().getString(), pack.getDescription().getString(),
                    pack.getCompatibility().isCompatible(), pack.getCompatibility().getDescription().getString(),
                    position >= 0, pack.isRequired(), position, sourceLabel(pack.getPackSource()),
                    icons.hasIcon(pack)));
        }
        return out;
    }

    /** Label of a pack source in the core's vocabulary (built-in, world, server, feature, file). */
    static String sourceLabel(PackSource source) {
        if (source == PackSource.BUILT_IN) {
            return "built-in";
        }
        if (source == PackSource.WORLD) {
            return "world";
        }
        if (source == PackSource.SERVER) {
            return "server";
        }
        if (source == PackSource.FEATURE) {
            return "feature";
        }
        return "file";
    }

    /** Icon texture of a pack when it has one (registered on first request). */
    public Optional<TextureRef> icon(String id) {
        PackRepository repository = repository();
        if (repository == null) {
            return Optional.empty();
        }
        Pack pack = repository.getPack(id);
        return pack == null ? Optional.empty() : icons.texture(pack);
    }

    @Override
    public boolean setEnabled(String id, boolean enabled) {
        PackRepository repository = repository();
        if (repository == null) {
            return false;
        }
        Pack pack = repository.getPack(id);
        if (pack == null || pack.isRequired()) {
            return false;
        }
        List<String> selected = staged(repository);
        if (enabled) {
            if (selected.contains(id)) {
                return false;
            }
            selected.add(id);
            return true;
        }
        return selected.remove(id);
    }

    @Override
    public boolean move(String id, int delta) {
        PackRepository repository = repository();
        if (repository == null || delta == 0) {
            return false;
        }
        Pack pack = repository.getPack(id);
        if (pack == null || pack.isFixedPosition()) {
            return false;
        }
        List<String> selected = staged(repository);
        int index = selected.indexOf(id);
        if (index < 0) {
            return false;
        }
        int target = Math.max(0, Math.min(selected.size() - 1, index + delta));
        if (target == index) {
            return false;
        }
        Pack other = repository.getPack(selected.get(target));
        if (other != null && other.isFixedPosition()) {
            return false;
        }
        selected.remove(index);
        selected.add(target, id);
        return true;
    }

    @Override
    public boolean hasPendingChanges() {
        PackRepository repository = repository();
        return repository != null && !staged(repository).equals(new ArrayList<>(repository.getSelectedIds()));
    }

    @Override
    public void apply() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repository = repository();
        if (minecraft == null || repository == null || !hasPendingChanges()) {
            return;
        }
        repository.setSelected(new ArrayList<>(staged(repository)));
        minecraft.options.updateResourcePacks(repository);
        staged = null;
    }

    @Override
    public void discard() {
        staged = null;
    }

    @Override
    public void openPackFolder() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            Util.getPlatform().openPath(minecraft.getResourcePackDirectory());
        }
    }

    @Override
    public void openVanillaScreen() {
        VanillaScreenOpener.open(VanillaScreen.RESOURCE_PACKS);
    }
}

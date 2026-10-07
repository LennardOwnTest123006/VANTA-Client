package dev.vanta.client.mixin;

import dev.vanta.client.WorldsFolderHook;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Singleplayer worlds from the official Minecraft folder ("Singleplayer worlds" setting, core {@code WorldsFolder}).
 * <p>
 * Target: the only {@code new LevelStorageSource(...)} in the {@code Minecraft} constructor, which in 1.21.11 reads
 * {@code this.levelSource = new LevelStorageSource(gameDirPath.resolve("saves"), gameDirPath.resolve("backups"),
 * this.directoryValidator, this.fixerUpper);} against the constructor
 * {@code LevelStorageSource(Path baseDir, Path backupDir, DirectoryValidator worldDirValidator, DataFixer fixerUpper)}.
 * Argument 0 (saves) and argument 1 (backups) are replaced with the folders {@link WorldsFolderHook} resolved; without
 * a resolved redirect they come back unchanged. Everything Singleplayer does goes through this one level storage
 * ({@code getLevelSource()}, {@code createWorldOpenFlows()}): the world list, Create New World, loading, quick play,
 * backups and VANTA's "Continue".
 * <p>
 * Only an existing {@code saves/} is ever passed (the constructor creates its base folder), so nothing is created in
 * the official Minecraft folder. The same {@code @ModifyArg} on an {@code INVOKE} of a constructor is used by Fabric
 * API (for example {@code ServerConfigurationPacketListenerImplMixin} on {@code SynchronizeRegistriesTask.<init>}).
 */
@Mixin(Minecraft.class)
abstract class MinecraftLevelSourceMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource;<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;Lcom/mojang/datafixers/DataFixer;)V"),
            index = 0)
    private Path vanta$savesDir(Path vanillaSaves) {
        return WorldsFolderHook.savesDir(vanillaSaves);
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource;<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;Lcom/mojang/datafixers/DataFixer;)V"),
            index = 1)
    private Path vanta$backupsDir(Path vanillaBackups) {
        return WorldsFolderHook.backupsDir(vanillaBackups);
    }
}

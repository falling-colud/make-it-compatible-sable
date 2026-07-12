package dev.micsable.immersive_portals_sable.mixin;

import java.util.List;
import java.util.UUID;

import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;

import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

/**
 * Makes Sable's sub-level tracking portal-aware, so vehicles sync to players watching them <em>through</em> an
 * Immersive Portals portal - including from another dimension (ported from the upstream bridge, extended for
 * Sable 2.0's tracking system).
 *
 * <p>{@link SubLevelTrackingSystem} decides which players receive a sub-level's spawn/movement/removal packets.
 * Stock Sable only considers players <em>in the same level</em> ({@code ServerLevel#players()} /
 * {@code getPlayerByUUID}) and <em>within a distance</em> of the sub-level ({@code shouldLoad}). Both assumptions
 * break with portals: a player can stand in another dimension yet see the vehicle perfectly well through a
 * portal, and Immersive Portals keeps the chunks in view loaded for them. Three seams fix that:</p>
 *
 * <ul>
 *   <li><b>{@code shouldLoad}</b> - additionally true when the player is watching the sub-level's chunk through
 *       Immersive Portals' chunk tracking (dimension-aware, so cross-dimension portal views count);</li>
 *   <li><b>player iteration</b> - the {@code players()} reads in {@code collectPlayers} <em>and</em> {@code tick}
 *       consider every player on the server, not just this level's (the {@code shouldLoad} gate still decides
 *       who actually gets packets);</li>
 *   <li><b>player lookup</b> - every {@code getPlayerByUUID} falls back to the server-wide player list, since a
 *       tracked player may be in a different dimension (Sable 2.0 already does this in one code path; portals
 *       need it everywhere).</li>
 * </ul>
 */
@Mixin(value = SubLevelTrackingSystem.class, priority = 900)
public abstract class SubLevelTrackingSystemMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"))
    private Player mic$lookUpPlayersServerWide(final ServerLevel serverLevel, final UUID uuid, final Operation<Player> original) {
        final Player inLevel = original.call(serverLevel, uuid);
        if (inLevel != null)
            return inLevel;
        return serverLevel.getServer().getPlayerList().getPlayer(uuid);
    }

    @WrapMethod(method = "shouldLoad")
    private boolean mic$loadForPortalWatchers(final Player player, final Vector3dc entityPosition, final Operation<Boolean> original) {
        if (original.call(player, entityPosition))
            return true;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        final ChunkPos chunkPos = new ChunkPos(BlockPos.containing(entityPosition.x(), entityPosition.y(), entityPosition.z()));
        return ImmPtlChunkTracking.isPlayerWatchingChunk(serverPlayer, this.level.dimension(), chunkPos.x, chunkPos.z);
    }

    @WrapOperation(
        method = {"collectPlayers", "tick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<? extends Player> mic$considerAllServerPlayers(final ServerLevel serverLevel, final Operation<List<? extends Player>> original) {
        original.call(serverLevel);
        return serverLevel.getServer().getPlayerList().getPlayers();
    }
}

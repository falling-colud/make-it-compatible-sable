package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;

/**
 * Fixes entity tracking for plot-grid entities inside Immersive Portals' rewrite of
 * {@code ChunkMap.TrackedEntity} (via MixinSquared, since the logic lives in Immersive Portals' merged mixin
 * methods):
 *
 * <ul>
 *   <li><b>Projected tracking position</b> - Immersive Portals overwrites vanilla's {@code updatePlayer} and
 *       decides who sees an entity in its own {@code ip_updateEntityTrackingStatus}, keyed on the entity's
 *       {@code chunkPosition()}. For an entity on a Sable sub-level that is a plot-grid chunk nobody watches, so
 *       the entity is never paired with players (Sable's own fix for this targets the vanilla method Immersive
 *       Portals emptied - our annotation adjuster cancels it). Wrapping the position reads to return the chunk of
 *       the entity's position <em>projected out of the sub-level</em> restores tracking, portal-watch semantics
 *       included. (Ported from the upstream bridge.)</li>
 *   <li><b>{@code broadcastToPlayer} honoured again</b> - vanilla's pairing check ANDs
 *       {@code entity.broadcastToPlayer(player)}, which mods use to keep self-networked entities out of vanilla
 *       tracking (this hub's LittleTiles &times; Sable patch does exactly that for plot-resident tile animations;
 *       vanilla itself uses it for spectator-mode rules). Immersive Portals' replacement drops the check, which
 *       re-introduces the duplicate-pairing/ghost-remove bugs for such entities. Gating Immersive Portals'
 *       watch predicate ({@code recWatches}) on it restores vanilla semantics for both the pair and unpair
 *       paths.</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", priority = 2000)
public abstract class TrackedEntityMixin {

    @TargetHandler(
        mixin = "qouteall.imm_ptl.core.mixin.common.entity_sync.MixinTrackedEntity",
        name = "ip_updateEntityTrackingStatus")
    @WrapOperation(
        method = "@MixinSquared:Handler",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos mic$projectedTrackingChunkPos(final Entity entity, final Operation<ChunkPos> original) {
        final Vec3 pos = entity.position();
        final SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(entity.level(), pos);
        if (subLevel != null)
            return new ChunkPos(BlockPos.containing(subLevel.logicalPose().transformPosition(pos)));
        return original.call(entity);
    }

    @TargetHandler(
        mixin = "qouteall.imm_ptl.core.mixin.common.entity_sync.MixinTrackedEntity",
        name = "recWatches")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At("RETURN"))
    private static boolean mic$honourBroadcastToPlayer(final boolean original,
                                                       @Local(argsOnly = true) final Entity entity,
                                                       @Local(argsOnly = true) final ServerPlayer player) {
        return original && (entity == null || player == null || entity.broadcastToPlayer(player));
    }
}

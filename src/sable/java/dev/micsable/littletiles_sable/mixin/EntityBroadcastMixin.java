package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import dev.micsable.littletiles_sable.SableAnimationSync;

/**
 * Makes {@code SableAnimationSync} the single networking owner of plot-resident LittleTiles animation entities.
 * Because {@code LittleEntity.setPos} uses {@code setPosRaw}, such an entity's {@code chunkPosition} drifts from
 * plot coordinates (at spawn) to world coordinates (after its first tick) while it stays filed in the plot entity
 * section; vanilla's tracking gate ({@code ChunkMap.TrackedEntity.updatePlayer}, which ANDs
 * {@code entity.broadcastToPlayer(player)}) would then start pairing nearby players once {@code chunkPosition} is
 * world-space - duplicating this patch's sub-level-anchored pairing and, worse, sending a vanilla remove when the
 * player leaves vanilla range even though Sable still shows the vehicle. Returning {@code false} keeps vanilla out
 * of these entities entirely.
 *
 * <p>{@code broadcastToPlayer} is declared only on {@link Entity} (LittleTiles does not override it), so the mixin
 * targets {@code Entity}; the guard is a cheap {@code instanceof} that leaves every ordinary entity - and
 * LittleTiles animations not on a Sable plot - reporting the vanilla default.</p>
 *
 * <p>Also suppressed while the entity is an <em>orphaned</em> plot animation (anchored in plot coordinates but not
 * yet re-parented to its sub-level, the state right after world load): letting vanilla pair players in that gap
 * means vanilla sends its remove as soon as the origin re-parents and this mixin starts returning {@code false} -
 * wiping the client copy {@code SableAnimationSync} just spawned, so the door stayed invisible until relog.</p>
 */
@Mixin(Entity.class)
public class EntityBroadcastMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void mic$suppressVanillaTrackingForPlotAnimations(final ServerPlayer player, final CallbackInfoReturnable<Boolean> cir) {
        final Entity self = (Entity) (Object) this;
        if (SableAnimationSync.subLevelOf(self) != null || SableAnimationSync.isOrphanedPlotAnimation(self))
            cir.setReturnValue(false);
    }
}

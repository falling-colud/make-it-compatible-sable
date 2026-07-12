package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import dev.micsable.littletiles_sable.SableAnimationSync;

import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.entity.animation.LittleAnimationEntity;

/**
 * Fix (a door on a Sable vehicle vanishes / renders only its outline when it returns to block form). The reverse of
 * {@code StructureBlockToEntityPacketMixin}: when an animation ends ({@code changeToBlockForm}) LittleTiles places
 * the blocks back on the plot and then sends {@code StructureEntityToBlockPacket} to hand the animation's already
 * built mesh straight into the plot's render section - via {@code RenderUploader.queue} &rarr;
 * {@code RenderAdditional.getOrCreateSection}, which calls {@code target.getRenderChunk(plotLevel, section)}. A
 * Sable plot is rendered by Sable's own dispatcher, so LittleTiles' render-chunk lookup (the main level renderer's
 * view area, indexed near the player) has no section at the plot's coordinates &mdash; it returns {@code null} and
 * the packet dies with an NPE ({@code RenderChunkExtender.backToRAM()} on null, a client FATAL). The blocks were
 * already placed and their outline shows, but their tile mesh was never built: the structure appears to vanish
 * (or, on a vehicle that still has other blocks, shows only its selection outline).
 *
 * <p>The transfer is only an optimization. For plot animations we skip it and instead mark the returned tile blocks
 * dirty so they rebuild through the Sable render bridge - the same path every statically placed tile on a vehicle
 * already uses (the block updates that carried the tiles here arrived first, so the block entities exist).</p>
 */
@Mixin(targets = "team.creative.littletiles.common.packet.structure.StructureEntityToBlockPacket")
public class StructureEntityToBlockPacketMixin {

    @Inject(method = "execute(Lnet/minecraft/world/entity/player/Player;Lteam/creative/littletiles/common/entity/animation/LittleAnimationEntity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$skipBufferTransferOnPlot(final Player player, final LittleAnimationEntity entity, final CallbackInfo ci) {
        if (SableAnimationSync.subLevelOf(entity) == null)
            return; // not a plot animation - keep LittleTiles' fast buffer transfer
        ci.cancel();
        // the blocks were placed back on the plot (their outline already shows); force their tile mesh to build
        // through the Sable bridge, the path a statically placed vehicle tile uses. The animation's sub-level still
        // holds the same block positions until the entity is removed, so it is the position source.
        final Level level = entity.level();
        try {
            for (final BETiles animBE : entity.getSubLevel()) {
                final BlockPos pos = animBE.getBlockPos();
                if (level.getBlockEntity(pos) instanceof BETiles plotBE && plotBE.render != null)
                    plotBE.render.tilesChanged();
            }
        } catch (final Throwable t) {
            dev.micsable.littletiles_sable.LittleTilesSablePatch.LOGGER.error(
                "Failed to rebuild plot tile meshes after a Sable animation returned to block form", t);
        }
    }
}

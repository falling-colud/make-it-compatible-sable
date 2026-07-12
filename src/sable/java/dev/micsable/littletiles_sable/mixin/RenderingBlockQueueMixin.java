package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import dev.micsable.littletiles_sable.client.SableTiles;

import team.creative.littletiles.client.render.cache.build.RenderingBlockQueue;
import team.creative.littletiles.client.render.cache.build.RenderingLevelHandler;
import team.creative.littletiles.common.block.entity.BETiles;

/**
 * The one hook that makes LittleTiles render on Sable vehicles.
 *
 * <p>When a tile-block ({@link BETiles}) is queued for a render rebuild, LittleTiles chooses where and how
 * to build its mesh via {@code RenderingLevelHandler.of(level)}. For a block sitting inside a Sable plot the
 * level is the ordinary {@code ClientLevel}, so that call returns the main-world handler &mdash; under Sodium
 * that means the global Sodium section storage and the Sodium vertex format, neither of which matches the
 * vanilla sections Sable actually draws the vehicle from.</p>
 *
 * <p>We redirect that single choice: if the block lives in a Sable sub-level we hand back
 * {@link SableTiles#SABLE_HANDLER}, which builds in vanilla format and uploads into Sable's own render
 * section. Everything downstream ({@code RenderingBlockContext}, the async build thread, the upload, the
 * dirty/recompile cycle) then targets the correct section automatically. Blocks outside a plot are untouched.</p>
 */
@Mixin(RenderingBlockQueue.class)
public class RenderingBlockQueueMixin {

    @Redirect(
        method = "queue(Lteam/creative/littletiles/common/block/entity/BETiles;ZJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lteam/creative/littletiles/client/render/cache/build/RenderingLevelHandler;"
                   + "of(Lnet/minecraft/world/level/Level;)"
                   + "Lteam/creative/littletiles/client/render/cache/build/RenderingLevelHandler;"
        )
    )
    private RenderingLevelHandler mic$routeSableSubLevels(final Level level,
            final BETiles tiles, final boolean hasPos, final long pos) {
        final long section = SectionPos.asLong(tiles.getBlockPos());
        if (SableTiles.isBridgeable(level, section)) {
            SableTiles.noteBridged();
            return SableTiles.SABLE_HANDLER;
        }
        return RenderingLevelHandler.of(level);
    }
}

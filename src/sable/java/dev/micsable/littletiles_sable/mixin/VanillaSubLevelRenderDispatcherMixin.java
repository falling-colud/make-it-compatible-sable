package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;

import dev.micsable.littletiles_sable.SablePlots;
import dev.micsable.littletiles_sable.client.SableTiles;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;

import team.creative.littletiles.common.block.mc.BlockTile;

/**
 * Render-data selection fixes for LittleTiles content on Sable sub-levels. Three related problems, one target:
 *
 * <ol>
 * <li><b>Single-block plots made of tiles.</b> Sable renders a 1x1x1 sub-level through a lightweight single-block
 * path that only tessellates the block's <em>vanilla</em> model - LittleTiles bakes its tile geometry into vanilla
 * render <em>sections</em>, which the single path never creates, so a one-block LittleTiles sub-level would show as
 * an empty/vanilla block. We force any single-block sub-level whose block is a LittleTiles tile-block down Sable's
 * <em>chunked</em> renderer instead (a degenerate 1-section grid), where the existing tile bridge already works.
 * Non-tile single blocks are untouched and keep Sable's cheaper single-block path.</li>
 *
 * <li><b>Empty plots are not "a single block at world (0,0,0)".</b> {@code BoundingBox3i.EMPTY} is the literal box
 * (0,0,0)-(0,0,0), so {@code isSingleBlock} on an empty plot passes the min==max test and then samples the block at
 * <em>world origin</em>. Sable never hits this (it removes empty sub-levels), but this patch keeps a sub-level alive
 * while its entire LittleTiles content animates as an entity ({@code ServerSubLevelMixin}) - and the moment that plot
 * emptied, {@code resize} would tear down the chunked render data (with LittleTiles build threads still targeting its
 * sections) and swap in a single-block renderer pointed at (0,0,0). That churn window froze the render thread the
 * instant an LT-only structure was activated. Empty bounds now (a) never count as single-block and (b) skip
 * {@code resize} entirely - the existing sections stay alive and simply recompile to empty through the ordinary
 * block updates, the same stable path as mining blocks off a vehicle. When the animation puts the blocks back, the
 * next (non-empty) resize finds the same sections and reuses them.</li>
 *
 * <li><b>Tiles that returned before the render data was ready re-bake cleanly.</b> When a door closes, the returning
 * tile-block updates race the plot-bounds packet: tiles queued while the render data is still single (or the plot
 * still empty) are not bridgeable and bake through the wrong pipeline/vertex format. The later splice into a Sable
 * section then trips {@code BufferHolderMixin}'s stride guard, which invalidates the stale cache - but nothing
 * re-queued the block entity, so the structure stayed invisible (outline only). After any resize that lands on
 * chunked data following an empty phase or a single&rarr;chunked swap, every {@code BETiles} on the plot is
 * re-queued so it re-bakes through the bridge at the correct stride.</li>
 * </ol>
 */
@Mixin(VanillaSubLevelRenderDispatcher.class)
public class VanillaSubLevelRenderDispatcherMixin {

    @Inject(method = "isSingleBlock", at = @At("HEAD"), cancellable = true)
    private static void mic$forceChunkedForTiles(final ClientSubLevel subLevel, final CallbackInfoReturnable<Boolean> cir) {
        try {
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null)
                return;
            if (SablePlots.isEmptyPlotBounds(bounds)) {
                // an empty plot has no block to sample; EMPTY is (0,0,0)-(0,0,0), so Sable's own check would read
                // world (0,0,0) and pick the single-block path - keep empty plots on the chunked path instead
                // (createRenderData then builds a chunked instance whose resize() safely produces zero sections).
                // NOTE the helper, not a reference test: client bounds are network COPIES of EMPTY.
                cir.setReturnValue(false);
                return;
            }
            if (bounds.minX() != bounds.maxX() || bounds.minY() != bounds.maxY() || bounds.minZ() != bounds.maxZ())
                return; // multi-block plot -> Sable already uses the chunked renderer
            final var block = subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())).getBlock();
            final boolean tile = block instanceof BlockTile;
            SableTiles.diag("single:" + block,
                    "single-block sub-level of " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
                            + " -> " + (tile ? "FORCING chunked renderer (tiles can bridge)" : "left on Sable's single-block path"));
            if (tile)
                cir.setReturnValue(false); // not "single" -> Sable uses the chunked renderer, which the bridge supports
        } catch (final Throwable ignored) {
            // fall through to Sable's own single-block check
        }
    }

    @Inject(method = "resize", at = @At("HEAD"), cancellable = true)
    private void mic$skipResizeWhileEmpty(final ClientSubLevel subLevel, final SubLevelRenderData renderData,
                                          final CallbackInfoReturnable<SubLevelRenderData> cir) {
        try {
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null || !SablePlots.isEmptyPlotBounds(bounds))
                return;
            // plot is transiently empty (its content is animating as an entity): keep the current render data
            // untouched - no close/create churn against in-flight LittleTiles builds, no section buffer leaks.
            // Remember the empty phase so the next real resize re-queues the plot's tiles (see below).
            SableTiles.markPlotWasEmpty(subLevel);
            SableTiles.diag("resize-empty", "plot bounds emptied (content in entity form) -> render data kept as-is until blocks return");
            cir.setReturnValue(renderData);
        } catch (final Throwable ignored) {
            // fall through to Sable's own resize
        }
    }

    @Inject(method = "resize", at = @At("RETURN"))
    private void mic$rebakeTilesAfterResize(final ClientSubLevel subLevel, final SubLevelRenderData renderData,
                                            final CallbackInfoReturnable<SubLevelRenderData> cir) {
        try {
            final SubLevelRenderData result = cir.getReturnValue();
            if (!(result instanceof VanillaChunkedSubLevelRenderData))
                return;
            final boolean cameBackFromEmpty = SableTiles.consumePlotWasEmpty(subLevel);
            final boolean swappedToChunked = result != renderData;
            if (cameBackFromEmpty || swappedToChunked)
                SableTiles.requeueTiles(subLevel);
        } catch (final Throwable ignored) {
            // re-queue is best-effort; worst case the tiles stay stale until the next block update
        }
    }
}

package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;

/**
 * Fix (an animated LittleTiles structure renders only its outline after returning to block form). Sable renders a
 * 1x1x1 sub-level through {@link VanillaSingleSubLevelRenderData}, which only tessellates the block's <em>vanilla</em>
 * model - LittleTiles bakes tile geometry into render <em>sections</em>, which the single path never has, so tiles
 * on it show nothing (the block's selection outline is all that remains). The sub-level's render-data type is only
 * re-decided on a plot <em>bounds</em> change ({@code ClientSubLevel.onPlotBoundsChanged} → dispatcher {@code
 * resize}); when a door closes back into the exact 1x1x1 footprint the plot briefly held as air (single render data
 * was chosen then, block = air), the tile arrives without changing the bounds, so the resize never fires and the
 * sub-level stays stuck on the single path.
 *
 * <p>{@code setDirty} <em>does</em> fire for that block change. If by then the sub-level should no longer be single
 * (our {@code VanillaSubLevelRenderDispatcherMixin} makes {@code isSingleBlock} return {@code false} for a tile
 * block), re-run {@code onPlotBoundsChanged} to swap in the chunked render data - the path the tile bridge needs -
 * exactly as a real bounds change would. This runs on the main thread during block-change handling, the same
 * context Sable resizes in, so it is safe.</p>
 */
@Mixin(value = VanillaSingleSubLevelRenderData.class, remap = false)
public class VanillaSingleSubLevelRenderDataMixin {

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void mic$switchToChunkedForTiles(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final ClientSubLevel sub = ((VanillaSingleSubLevelRenderData) (Object) this).getSubLevel();
        if (sub == null || sub.isRemoved())
            return;
        try {
            // An empty plot has no tile content to switch a renderer for. During a LittleTiles-only vehicle's
            // animation the plot momentarily empties (every block moves into the animation entity); kicking off an
            // onPlotBoundsChanged render-data swap for that transient empty state is pointless and only churns render
            // data while the sub-level is mid-teardown - the window in which the render thread was seen to hang. Leave
            // Sable's own setDirty handling to run untouched; the swap is only meant for a real 1x1x1 -> tile change.
            final dev.ryanhcode.sable.companion.math.BoundingBox3ic emptyCheck = sub.getPlot().getBoundingBox();
            if (dev.micsable.littletiles_sable.SablePlots.isEmptyPlotBounds(emptyCheck))
                return;
            if (!VanillaSubLevelRenderDispatcher.isSingleBlock(sub)) {
                // deferred: setDirty runs mid block-update handling, and the swap closes + replaces the render data
                // object we are executing inside of. Next render-thread task is a clean point; the re-check makes
                // multiple queued swaps for the same burst of block changes collapse into one.
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (!sub.isRemoved() && sub.getRenderData() instanceof VanillaSingleSubLevelRenderData
                        && !VanillaSubLevelRenderDispatcher.isSingleBlock(sub)) {
                        dev.micsable.littletiles_sable.client.SableTiles.diag("single-to-chunked",
                            "single-block sub-level received tile content -> switching to the chunked renderer");
                        sub.onPlotBoundsChanged(); // re-pick render data: single -> chunked, so tiles can bridge
                    }
                });
                ci.cancel();
            }
        } catch (final Throwable ignored) {
            // never break Sable's block-dirty handling; worst case the tile stays on the single path this tick
        }
    }
}

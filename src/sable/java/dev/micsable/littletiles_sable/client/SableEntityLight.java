package dev.micsable.littletiles_sable.client;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import dev.micsable.littletiles_sable.SablePlots;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Bounded replacement for Sable's {@code EntityRendererMixin.sable$getSubLevelAccountedSkyLight} (installed by
 * {@code EntityRendererSableLightMixin}).
 *
 * <p><b>Why (the LittleTiles-door freeze).</b> Sable darkens entities that stand "under" a vehicle: for every
 * sub-level intersecting the entity's light probe it transforms the probe into plot space and then walks
 * <em>straight down, one block at a time</em>, until it finds a non-air block ("above ground") or drops below
 * {@code plot.getBoundingBox().minY()}. That walk has no other bound. Two states this patch creates make it
 * pathological (thread dumps of frozen sessions land exactly in this loop, spinning in {@code getBlockState}):
 * an <em>empty</em> plot (its whole LittleTiles content is animating as an entity) has no non-air block to stop
 * at, and a mid-transition animation entity can present a light-probe position far outside the plot, making
 * "walk down to the plot floor" an astronomically long air scan on the render thread - the game freezes the
 * instant an LT-only structure is activated.</p>
 *
 * <p>The reimplementation is semantically identical for every sane input, with three guards: empty plots are
 * skipped outright (no blocks - nothing can shade the entity), non-finite transformed probes are skipped, and
 * the walk starts no higher than {@code bounds.maxY()} (everything above the plot's block bounds is air by
 * definition), which caps the loop at the plot's height regardless of where the probe landed.</p>
 */
public final class SableEntityLight {

    /** Refuse to walk plots taller than this - real vehicles are nowhere near it; corrupt bounds can be. */
    private static final int MAX_PLOT_HEIGHT = 4096;

    private SableEntityLight() {}

    public static int subLevelAccountedSkyLight(final int original, final Level level, final LightLayer lightLayer,
                                                final BlockPos blockPos, final Vector3dc probePosition) {
        int brightness = original == -1 ? level.getBrightness(lightLayer, blockPos) : LightTexture.sky(original);
        try {
            final Vector3d plotProbe = new Vector3d();
            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(blockPos))) {
                if (!(subLevel instanceof ClientSubLevel client))
                    continue;
                final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
                if (SablePlots.isEmptyPlotBounds(bounds) || bounds.maxY() - bounds.minY() > MAX_PLOT_HEIGHT)
                    continue;
                client.renderPose().transformPositionInverse(probePosition, plotProbe);
                if (!plotProbe.isFinite())
                    continue;
                final int x = Mth.floor(plotProbe.x);
                final int probeY = Mth.floor(plotProbe.y);
                final int z = Mth.floor(plotProbe.z);
                final Level plotLevel = subLevel.getLevel();
                boolean aboveGround = false;
                for (int y = Math.min(probeY + 1, bounds.maxY()); y >= bounds.minY(); y--) {
                    if (!plotLevel.getBlockState(cursor.set(x, y, z)).isAir()) {
                        aboveGround = true;
                        break;
                    }
                }
                if (aboveGround) {
                    cursor.set(x, probeY, z);
                    if (lightLayer == LightLayer.BLOCK)
                        brightness = Math.max(brightness, plotLevel.getBrightness(lightLayer, cursor));
                    else if (lightLayer == LightLayer.SKY)
                        brightness = Math.min(brightness, client.scaleSkyLight(plotLevel.getBrightness(lightLayer, cursor)));
                }
            }
        } catch (final Throwable ignored) {
            // entity lighting must never take the frame down; fall back to what we have
        }
        return brightness;
    }
}

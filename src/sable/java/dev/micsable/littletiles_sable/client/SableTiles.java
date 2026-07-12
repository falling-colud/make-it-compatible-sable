package dev.micsable.littletiles_sable.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import dev.ryanhcode.sable.companion.math.Pose3dc;

import team.creative.littletiles.client.tool.shaper.ShapePosition;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;

import team.creative.littletiles.client.render.cache.build.RenderingLevelHandler;
import team.creative.littletiles.client.render.mc.RenderChunkExtender;

/**
 * Bridge utilities between LittleTiles' render pipeline and Sable's sub-levels.
 *
 * <p>Everything here is client-only; it is never touched on a dedicated server because the only
 * caller is a {@code "client"} mixin.</p>
 */
public final class SableTiles {

    private SableTiles() {}

    /**
     * The single, stateless {@link RenderingLevelHandler} that routes LittleTiles geometry into Sable
     * sub-level render sections. It is shared for every bridged tile-block; the per-call work happens in
     * {@link SableSubLevelHandler#getRenderChunk(Level, long)}.
     */
    public static final RenderingLevelHandler SABLE_HANDLER = new SableSubLevelHandler();

    /**
     * Resolves the Sable client sub-level whose plot owns the chunk at {@code sectionPos}.
     *
     * @param level       the level the tile-block reports (the main {@code ClientLevel}; Sable plots live in it)
     * @param sectionPos  a packed {@link SectionPos} long
     * @return the owning {@link ClientSubLevel}, or {@code null} if the chunk is ordinary world (not a plot)
     */
    @Nullable
    public static ClientSubLevel subLevelAt(@Nullable final Level level, final long sectionPos) {
        // Sable plots are regions of the *real* client world, so only tiles that report that level may bridge into a
        // plot's render sections. When a structure animates, LittleTiles moves its blocks into a fake
        // LittleAnimationLevel (at plot coordinates) that renders through its own animation render manager - if we let
        // those tiles resolve onto the sub-level they came from, LittleTiles uploads the animation's geometry into the
        // plot section every rebuild while Sable clears it as empty (the plot's content is in the entity now), a
        // render-thread rebuild thrash that never settles and freezes the client the instant an LT-only structure is
        // animated. Restricting the bridge to the main client level keeps animating tiles on their own render path.
        if (level == null || level != Minecraft.getInstance().level)
            return null;
        final SubLevel sub;
        try {
            sub = Sable.HELPER.getContaining(level, SectionPos.x(sectionPos), SectionPos.z(sectionPos));
        } catch (final Throwable ignored) {
            // Sable not ready for this level yet, or this is not a client level - treat as "not a plot".
            return null;
        }
        return sub instanceof ClientSubLevel client ? client : null;
    }

    /**
     * Resolves the vanilla {@code RenderSection} that Sable builds &amp; draws for a plot section, exposed as a
     * LittleTiles {@link RenderChunkExtender} (LittleTiles mixes that interface into every vanilla
     * {@code SectionRenderDispatcher$RenderSection} at runtime).
     *
     * @return the render section as a {@link RenderChunkExtender}, or {@code null} if the sub-level is not a
     *         chunked plot (e.g. a single-block sub-level) or the section is outside the current plot grid
     */
    @Nullable
    public static RenderChunkExtender renderChunkAt(@Nullable final ClientSubLevel sub, final long sectionPos) {
        if (sub == null)
            return null;
        try {
            // An empty plot (e.g. its whole content just animated away - the LittleTiles-only-vehicle case, where
            // right-clicking the sole structure moves every block into a LittleAnimationEntity) has no tiles to
            // bridge. Resolving/uploading into its render sections while the main thread is tearing them down for the
            // now-empty bounds is exactly the resize-vs-build-thread race the catch below guards against, and has been
            // observed to freeze the render thread the instant such a structure animates. Skip it - a section with no
            // tiles renders nothing whether we bridge it or fall back to vanilla.
            final dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            if (dev.micsable.littletiles_sable.SablePlots.isEmptyPlotBounds(bounds))
                return null;
            final SubLevelRenderData data = sub.getRenderData();
            if (!(data instanceof VanillaChunkedSubLevelRenderData chunked)) {
                diag("rd:" + data.getClass().getName(),
                        "sub-level render data is " + data.getClass().getSimpleName()
                                + " (not chunked) -> tiles cannot bridge through it");
                return null;
            }
            final SectionPos requested = SectionPos.of(SectionPos.x(sectionPos), SectionPos.y(sectionPos), SectionPos.z(sectionPos));
            final SectionRenderDispatcher.RenderSection section = chunked.getRenderSection(requested);
            if (!(section instanceof RenderChunkExtender extender)) {
                diag("nosection", "chunked sub-level returned no render section for a plot chunk (section="
                        + (section == null ? "null" : section.getClass().getSimpleName()) + ")");
                return null;
            }
            // getRenderSection only bounds-checks the flat index, not each axis: a Y outside the plot's vertical
            // range (but inside its X/Z column) can resolve to a different section. Confirm we got the exact one.
            if (!SectionPos.of(section.getOrigin()).equals(requested)) {
                diag("wrongsection", "chunked sub-level resolved a different section than requested "
                        + "(req " + requested + " got " + SectionPos.of(section.getOrigin()) + ")");
                return null;
            }
            return extender;
        } catch (final Throwable ignored) {
            // This runs on LittleTiles' async render-build thread while the main thread may be resizing/closing the
            // sub-level (which nulls its render sections). Fall back to vanilla instead of killing the build thread.
            return null;
        }
    }

    /**
     * @return {@code true} only if {@code sectionPos} belongs to a Sable plot <em>and</em> we have a concrete
     *         render section to upload into. When this is false the caller falls back to vanilla behaviour.
     */
    public static boolean isBridgeable(@Nullable final Level level, final long sectionPos) {
        return renderChunkAt(subLevelAt(level, sectionPos), sectionPos) != null;
    }

    /**
     * The render data (transform provider) for the Sable sub-level whose plot contains {@code pos}, or
     * {@code null} if {@code pos} is in the open world. Works for both chunked and single-block sub-levels,
     * since {@code getTransformation}/{@code getChunkOffset} are defined on the {@link SubLevelRenderData}
     * interface itself.
     */
    @Nullable
    public static SubLevelRenderData renderDataAt(final BlockPos pos) {
        final ClientSubLevel sub = subLevelAt(Minecraft.getInstance().level, SectionPos.asLong(pos));
        return sub == null ? null : sub.getRenderData();
    }

    /**
     * Builds a model-view matrix that places a block at {@code (posX,posY,posZ)} in a sub-level's plot onto the
     * visible vehicle, ready to draw block-local geometry (0..1) directly (no large offset needed).
     *
     * <p>This is the same mapping as {@code SubLevelRenderData.getTransformation}, but the block's
     * <b>camera-relative</b> position is resolved in {@code double} first. Sable plots live at coordinates in the
     * millions, where a {@code float} matrix loses ~2 blocks of precision and the geometry jitters as the camera
     * moves. Because the vehicle is drawn near the player, the camera-relative result is small and stays precise.</p>
     */
    public static Matrix4f subLevelModelMatrix(final SubLevelRenderData data,
            final double posX, final double posY, final double posZ,
            final double camX, final double camY, final double camZ) {
        final Pose3dc pose = data.getSubLevel().renderPose();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Vector3dc scale = pose.scale();
        final Quaterniondc orientation = pose.orientation();
        final Vector3dc position = pose.position();

        // delta = orientation * (scale ⊙ (pos - rotationPoint)), all in double
        final Vector3d delta = new Vector3d(posX - rotationPoint.x(), posY - rotationPoint.y(), posZ - rotationPoint.z());
        delta.mul(scale.x(), scale.y(), scale.z());
        orientation.transform(delta);

        // camera-relative position of the block origin (small -> float-safe)
        final Vector3d blockRelCam = new Vector3d(position.x() - camX, position.y() - camY, position.z() - camZ).add(delta);

        return new Matrix4f()
            .translate((float) blockRelCam.x, (float) blockRelCam.y, (float) blockRelCam.z)
            .rotate(new Quaternionf(orientation))
            .scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }

    /** The Sable sub-level containing a shape-tool position's block (or {@code null} for the open world). */
    @Nullable
    private static ClientSubLevel spaceOf(final ShapePosition position) {
        return subLevelAt(Minecraft.getInstance().level, SectionPos.asLong(position.getPos()));
    }

    /**
     * Drops shape-tool positions that aren't in the same coordinate space as the first one. A drag started on a
     * vehicle and finished in the world would otherwise build a box spanning from the vehicle's far-away plot
     * coordinates across to the world - astronomically large, which crashes the game. Keeping only the same-space
     * points means the drag simply ignores the out-of-space end instead.
     */
    public static List<ShapePosition> filterSameSpace(final List<ShapePosition> positions) {
        if (positions == null || positions.size() < 2)
            return positions;
        final ClientSubLevel first = spaceOf(positions.get(0));
        final List<ShapePosition> kept = new ArrayList<>(positions.size());
        for (final ShapePosition p : positions)
            if (spaceOf(p) == first)
                kept.add(p);
        return kept.size() == positions.size() || kept.isEmpty() ? positions : kept;
    }

    /**
     * Sub-levels whose plot bounds were seen empty by {@code VanillaSubLevelRenderDispatcherMixin} (their entire
     * LittleTiles content is off animating as an entity). Consulted - and cleared - by the next non-empty resize to
     * decide whether the plot's tiles need a clean re-bake. Render-thread only; weak so removed sub-levels vanish.
     */
    private static final java.util.Set<ClientSubLevel> PLOT_WAS_EMPTY =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void markPlotWasEmpty(final ClientSubLevel sub) {
        PLOT_WAS_EMPTY.add(sub);
    }

    public static boolean consumePlotWasEmpty(final ClientSubLevel sub) {
        return PLOT_WAS_EMPTY.remove(sub);
    }

    /**
     * Force-queues a render re-bake for every {@link team.creative.littletiles.common.block.entity.BETiles} on the
     * sub-level's plot. Called after the plot's render data becomes (or returns to) the chunked type: tile blocks
     * that arrived <em>before</em> that moment (door-close block updates race the plot-bounds packet) were queued
     * while the plot was not bridgeable, so their caches were baked through the wrong pipeline/vertex format and
     * were - or will be - rejected by {@code BufferHolderMixin}'s stride guard instead of rendering. Re-queuing them
     * now routes the rebuild through {@link #SABLE_HANDLER} (the plot is bridgeable again), and the finished build
     * marks the Sable section dirty, so the geometry appears within a frame or two.
     */
    public static void requeueTiles(final ClientSubLevel sub) {
        try {
            final dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            if (dev.micsable.littletiles_sable.SablePlots.isEmptyPlotBounds(bounds))
                return;
            final Level level = sub.getLevel();
            int queued = 0;
            for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
                for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++)
                    for (final net.minecraft.world.level.block.entity.BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values())
                        if (be instanceof team.creative.littletiles.common.block.entity.BETiles tiles && !tiles.isRemoved()) {
                            tiles.render.queue(true, true, SectionPos.asLong(tiles.getBlockPos()));
                            queued++;
                        }
            if (queued > 0)
                dev.micsable.littletiles_sable.LittleTilesSablePatch.LOGGER.info(
                        "[mic/littletiles-sable] re-queued {} tile block(s) on {} for a clean re-bake after its render data became chunked",
                        queued, sub);
        } catch (final Throwable ignored) {
            // best-effort; a failed re-queue just leaves the tiles stale until their next block update
        }
    }

    private static final java.util.Set<String> DIAG_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Logs {@code msg} once per distinct {@code key} (diagnostic aid; safe to call from any thread / hot paths). */
    public static void diag(final String key, final String msg) {
        if (DIAG_SEEN.add(key))
            dev.micsable.littletiles_sable.LittleTilesSablePatch.LOGGER.info("[mic/littletiles-sable diag] " + msg);
    }

    private static volatile boolean loggedFirstBridge = false;

    /** Logs once, the first time a tile-block is actually routed onto a Sable sub-level (verification aid). */
    public static void noteBridged() {
        if (!loggedFirstBridge) {
            loggedFirstBridge = true;
            dev.micsable.littletiles_sable.LittleTilesSablePatch.LOGGER.info(
                    "[LittleTiles x Sable] first tile-block bridged onto a Sable sub-level - rendering hook is live.");
        }
    }
}

package dev.micsable.littletiles_sable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.micsable.littletiles_sable.mixin.MassTrackerAccessor;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;

/**
 * End-of-tick reconciliation for LittleTiles content on Sable sub-levels. Registered on the NeoForge event bus by
 * the mod entrypoint only when both mods are present.
 *
 * <p><b>Why mass is rebuilt instead of trusted incrementally.</b> Sable maintains each sub-level's mass tracker with
 * incremental add/remove pairs, which is exact for vanilla blocks (a state's mass and center never change). Tile
 * blocks break both assumptions: LittleTiles mutates tiles through paths that bypass the usual update funnel
 * ({@code updateTilesSecretly} during structure destruction and door transitions, block&harr;vanilla conversions
 * during placement), and even the covered paths change a block's center of mass in place - so the incrementally
 * maintained center/inertia drift with every edit, and the parallel-axis inertia updates degrade numerically under
 * repeated &plusmn; pairs (the "light, bouncy, rocking" vehicles after breaking tiles). Whenever tile content on a
 * sub-level changes, the whole self tracker is therefore rebuilt from the plot at the end of the tick -
 * {@code MassTracker.build} runs through this patch's position-aware mass/center mixins, so the rebuild is exact.</p>
 *
 * <p><b>Connectivity scans.</b> Tile geometry also changes what is <em>physically connected</em> without any block
 * state changing, which Sable's block-granular heat map cannot see; dirty sub-levels are re-scanned (throttled) by
 * {@link SableTileConnectivity}, which splits off parts only connected through empty space - including tile groups
 * inside a single block.</p>
 */
public final class SableTilePhysicsTicker {

    /** Ticks between connectivity scans of the same sub-level (edits arrive in bursts; scans are O(plot)). */
    private static final int SCAN_COOLDOWN_TICKS = 10;

    private static final Set<ServerSubLevel> MASS_DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerSubLevel> CONNECTIVITY_DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerSubLevel, Long> LAST_SCAN = new WeakHashMap<>();

    private SableTilePhysicsTicker() {}

    /** Tile content of this sub-level changed: rebuild its mass tracker and re-check connectivity. */
    public static void markTilesDirty(final ServerSubLevel subLevel) {
        MASS_DIRTY.add(subLevel);
        CONNECTIVITY_DIRTY.add(subLevel);
    }

    /** A block changed on this sub-level's plot: geometry-level connectivity may have changed. */
    public static void markConnectivityDirty(final ServerSubLevel subLevel) {
        CONNECTIVITY_DIRTY.add(subLevel);
    }

    @SubscribeEvent
    public static void onServerTickPost(final ServerTickEvent.Post event) {
        try {
            SableAnimationSync.serverTick(event.getServer());
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Animation entity sync failed", t);
        }

        if (!MASS_DIRTY.isEmpty()) {
            final List<ServerSubLevel> rebuild = new ArrayList<>(MASS_DIRTY);
            MASS_DIRTY.clear();
            for (final ServerSubLevel subLevel : rebuild)
                rebuildMass(subLevel);
        }

        if (!CONNECTIVITY_DIRTY.isEmpty()) {
            final List<ServerSubLevel> scan = new ArrayList<>(CONNECTIVITY_DIRTY);
            for (final ServerSubLevel subLevel : scan) {
                if (subLevel.isRemoved()) {
                    CONNECTIVITY_DIRTY.remove(subLevel);
                    continue;
                }
                final long gameTime = subLevel.getLevel().getGameTime();
                final Long last = LAST_SCAN.get(subLevel);
                if (last != null && gameTime - last < SCAN_COOLDOWN_TICKS)
                    continue; // stays dirty; retried on a later tick
                if (SableAnimationSync.isTopologyInFlux(subLevel, gameTime))
                    continue; // an animation is mid-transition: its structure's blocks would scan as disconnected
                              // and split into a doomed sub-level. Stay dirty; retried once the topology settles.
                CONNECTIVITY_DIRTY.remove(subLevel);
                LAST_SCAN.put(subLevel, gameTime);
                try {
                    SableTileConnectivity.scanAndSplit(subLevel);
                } catch (final Throwable t) {
                    LittleTilesSablePatch.LOGGER.error("Tile connectivity scan failed for sub-level {}", subLevel, t);
                }
            }
        }
    }

    /**
     * Rebuilds the sub-level's self mass tracker from the plot, <b>in place</b>. Replacing the tracker (as
     * {@code ServerSubLevel.buildMassTracker()} does) would discard the {@code MergedMassTracker}'s
     * {@code lastCenterOfMass} continuity - the very thing its {@code uploadData} uses to teleport-compensate the
     * body when the rotation point moves. Without that continuity the first update after a rebuild snapped the
     * rotation point with zero compensation, and the whole vehicle visibly jolted for an instant on every tile
     * edit. Copying the fresh values into the live tracker makes the follow-up {@code updateMergedMassData} apply
     * the center-of-mass correction as a smooth, compensated shift, exactly like Sable's own incremental updates.
     */
    private static void rebuildMass(final ServerSubLevel subLevel) {
        try {
            if (subLevel.isRemoved())
                return;
            final ServerLevel level = subLevel.getLevel();
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                return;
            final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
            final MassTracker self = subLevel.getSelfMassTracker();
            if (self == null)
                return;
            final MassTracker fresh = MassTracker.build(new LevelAccelerator(level), subLevel.getPlot().getBoundingBox());
            if (fresh.getCenterOfMass() == null && SableAnimationSync.hasLiveAnimation(subLevel))
                return; // plot temporarily empty while its blocks animate as an entity - keep the last valid mass
            final MassTrackerAccessor target = (MassTrackerAccessor) self;
            target.mic$setMass(fresh.getMass());
            target.mic$setInverseMass(fresh.getInverseMass());
            target.mic$setInertiaTensor(new Matrix3d(fresh.getInertiaTensor()));
            target.mic$setInverseInertiaTensor(new Matrix3d(fresh.getInverseInertiaTensor()));
            final Vector3dc centerOfMass = fresh.getCenterOfMass();
            target.mic$setCenterOfMass(centerOfMass == null ? null : new Vector3d(centerOfMass));
            // the merged update detects the change and (only when something actually moved) applies the
            // teleport-compensated rotation-point shift + re-uploads mass properties to the physics body
            subLevel.updateMergedMassData((float) physicsSystem.getPartialPhysicsTick());
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Mass tracker rebuild failed for sub-level {}", subLevel, t);
        }
    }
}

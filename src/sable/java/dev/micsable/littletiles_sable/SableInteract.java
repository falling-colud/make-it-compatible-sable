package dev.micsable.littletiles_sable;

import org.joml.Vector3d;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Common (both-sides) helpers for reasoning about which Sable coordinate space a position belongs to.
 *
 * <p>Used by the selection-area guards: a LittleTiles area selection stores two corners as raw block
 * coordinates with no level/sub-level context. If the first corner is captured inside a Sable plot (whose
 * chunks live at far-away "plot" coordinates) and the second in the open world, the min/max of those
 * coordinates spans an enormous box - the "fill from the world border to here" problem. We detect that the
 * two corners are in different spaces and refuse the selection.</p>
 */
public final class SableInteract {

    private SableInteract() {}

    /**
     * @return {@code true} if the two block columns belong to different coordinate spaces - i.e. different
     *         Sable sub-levels, or one inside a sub-level and the other in the open world. Returns
     *         {@code false} (allow normal behaviour) if they share a space or if Sable can't be queried.
     */
    public static boolean differentSpace(final Level level, final int x1, final int z1, final int x2, final int z2) {
        if (level == null)
            return false;
        try {
            final SubLevel a = Sable.HELPER.getContaining(level, x1 >> 4, z1 >> 4);
            final SubLevel b = Sable.HELPER.getContaining(level, x2 >> 4, z2 >> 4);
            return a != b; // identity: same plot -> same object; null vs sub, or two different subs -> differ
        } catch (final Throwable ignored) {
            return false;
        }
    }

    /**
     * Transforms a world-space point into a sub-level's plot coordinate space (the inverse of the pose that
     * places the vehicle in the world). LittleTiles raycasts its sub-tiles against tile boxes that live at the
     * plot coordinates, so the player's eye/look ray must be projected in here first to hit them.
     *
     * <p>Uses the sub-level's logical (gameplay) pose, matching how Sable transforms collision/interaction.</p>
     */
    public static Vec3 intoSubLevel(final SubLevel sub, final Vec3 world) {
        final Vector3d p = new Vector3d(world.x, world.y, world.z);
        sub.logicalPose().transformPositionInverse(p);
        return new Vec3(p.x, p.y, p.z);
    }
}

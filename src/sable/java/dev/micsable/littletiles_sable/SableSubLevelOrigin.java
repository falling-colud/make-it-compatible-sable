package dev.micsable.littletiles_sable;

import org.joml.Matrix3d;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;

import team.creative.creativecore.common.util.math.matrix.IVecOrigin;
import team.creative.creativecore.common.util.math.matrix.Matrix3;
import team.creative.creativecore.common.util.math.vec.Vec3d;

/**
 * A CreativeCore {@link IVecOrigin} representing a Sable sub-level's pose (plot space &rarr; world space). Used as
 * the <em>parent</em> of a LittleTiles animation entity's origin ({@link SableChildVecOrigin}) when the animation
 * lives on a Sable plot: LittleTiles' own nested-origin machinery then transforms everything - bounding boxes
 * (frustum culling, {@code find(AABB)}), interaction rays ({@code rayTrace}/{@code getHit}), collision OBBs and
 * sounds/particles - through the vehicle's pose, exactly like a door inside another LittleTiles level.
 *
 * <p>All point transforms delegate to Sable's own {@code Pose3d} math (including scale). The origin is read-only:
 * Sable owns the pose, so all setters are no-ops, {@code hasChanged()} reports false (the vehicle moving must not
 * re-dirty LittleTiles render caches - render interpolation reads the pose directly each frame), and the euler
 * getters are best-effort projections of the quaternion for the rare callers that want them.</p>
 */
public class SableSubLevelOrigin implements IVecOrigin {

    public final SubLevel subLevel;

    public SableSubLevelOrigin(final SubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public Pose3dc pose() {
        return this.subLevel.logicalPose();
    }

    // ------------------------------------------------------------------ transforms (authoritative)

    @Override
    public void transformPointToWorld(final Vec3d vec) {
        final Vector3d v = new Vector3d(vec.x, vec.y, vec.z);
        this.pose().transformPosition(v);
        vec.set(v.x, v.y, v.z);
    }

    @Override
    public void transformPointToFakeWorld(final Vec3d vec) {
        final Vector3d v = new Vector3d(vec.x, vec.y, vec.z);
        this.pose().transformPositionInverse(v);
        vec.set(v.x, v.y, v.z);
    }

    @Override
    public void onlyRotateWithoutCenter(final Vec3d vec) {
        final Vector3d v = new Vector3d(vec.x, vec.y, vec.z);
        this.pose().transformNormal(v);
        vec.set(v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ pose projections

    @Override
    public Vec3d center() {
        final Vector3dc rp = this.pose().rotationPoint();
        return new Vec3d(rp.x(), rp.y(), rp.z());
    }

    @Override
    public Vec3d translation() {
        final Vector3dc pos = this.pose().position();
        final Vector3dc rp = this.pose().rotationPoint();
        return new Vec3d(pos.x() - rp.x(), pos.y() - rp.y(), pos.z() - rp.z());
    }

    @Override
    public Matrix3 rotation() {
        final Pose3dc pose = this.pose();
        final Vector3dc s = pose.scale();
        final Matrix3d m = pose.orientation().get(new Matrix3d()).scale(s.x(), s.y(), s.z());
        return mic$toCreative(m);
    }

    @Override
    public Matrix3 rotationInv() {
        final Pose3dc pose = this.pose();
        final Vector3dc s = pose.scale();
        final Matrix3d rotationT = pose.orientation().get(new Matrix3d()).transpose();
        final Matrix3d m = new Matrix3d().scaling(1.0 / s.x(), 1.0 / s.y(), 1.0 / s.z()).mul(rotationT);
        return mic$toCreative(m);
    }

    /** CreativeCore's {@link Matrix3} is row-major; JOML accessors are (column, row). */
    private static Matrix3 mic$toCreative(final Matrix3d m) {
        return new Matrix3(
            m.get(0, 0), m.get(1, 0), m.get(2, 0),
            m.get(0, 1), m.get(1, 1), m.get(2, 1),
            m.get(0, 2), m.get(1, 2), m.get(2, 2));
    }

    private Vector3d mic$eulerDegrees() {
        final Vector3d angles = this.pose().orientation().getEulerAnglesXYZ(new Vector3d());
        return angles.set(Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z));
    }

    @Override
    public double offX() {
        return this.translation().x;
    }

    @Override
    public double offY() {
        return this.translation().y;
    }

    @Override
    public double offZ() {
        return this.translation().z;
    }

    @Override
    public double rotX() {
        return this.mic$eulerDegrees().x;
    }

    @Override
    public double rotY() {
        return this.mic$eulerDegrees().y;
    }

    @Override
    public double rotZ() {
        return this.mic$eulerDegrees().z;
    }

    @Override
    public double offXLast() {
        return this.offX();
    }

    @Override
    public double offYLast() {
        return this.offY();
    }

    @Override
    public double offZLast() {
        return this.offZ();
    }

    @Override
    public double rotXLast() {
        return this.rotX();
    }

    @Override
    public double rotYLast() {
        return this.rotY();
    }

    @Override
    public double rotZLast() {
        return this.rotZ();
    }

    @Override
    public boolean isRotated() {
        final Quaterniondc q = this.pose().orientation();
        return Math.abs(q.w()) < 0.9999999;
    }

    @Override
    public boolean hasChanged() {
        return false; // Sable moves every tick; reporting change would constantly re-dirty LT caches
    }

    // ------------------------------------------------------------------ read-only: Sable owns the pose

    @Override
    public void offX(final double value) {}

    @Override
    public void offY(final double value) {}

    @Override
    public void offZ(final double value) {}

    @Override
    public void off(final double x, final double y, final double z) {}

    @Override
    public void rotX(final double value) {}

    @Override
    public void rotY(final double value) {}

    @Override
    public void rotZ(final double value) {}

    @Override
    public void rot(final double x, final double y, final double z) {}

    @Override
    public Vec3d deltaMovement() {
        return new Vec3d();
    }

    @Override
    public void deltaMovement(final Vec3d vec) {}

    @Override
    public void setCenter(final Vec3d vec) {}

    @Override
    public void tick() {}

    @Override
    public IVecOrigin getParent() {
        return null;
    }

    @Override
    public IVecOrigin copy() {
        return new SableSubLevelOrigin(this.subLevel);
    }
}

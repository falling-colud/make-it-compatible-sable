package dev.micsable.littletiles_sable.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.SableChildVecOrigin;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import team.creative.creativecore.common.util.math.vec.Vec3d;

/**
 * Client-side rendering math for LittleTiles animations on Sable plots.
 *
 * <p><b>Coordinate convention.</b> Plot coordinates are millions of blocks out, and the render pipeline works in
 * float32, so nothing camera-relative may ever be computed against the <em>world</em> camera in plot space. For
 * Sable-parented animations the render path is fed the camera transformed <em>into plot space</em>
 * ({@link #plotCamera}); vertex data and chunk/region offsets then stay small. {@link #setupComposedRendering}
 * builds the matching model transform - (plot point - plot camera) &rarr; (world point - world camera) - by
 * composing the animation's own interpolated motion with the sub-level's interpolated render pose entirely in
 * doubles, pushing a single float matrix whose entries are all camera-local.</p>
 *
 * <p>The real camera is passed in explicitly by each render path (vanilla or Sodium supplies its own camera object)
 * so the composed matrix and the chunk/region offset are computed against the <em>same</em> camera and stay
 * consistent - a mismatch of even a fraction of a block between the two shears the geometry.</p>
 */
public final class SableAnimationRender {

    private SableAnimationRender() {}

    public static float partialTicks() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** The sub-level's interpolated render pose on the client, its logical pose otherwise. */
    public static Pose3dc renderPose(final SubLevel subLevel, final float partialTicks) {
        return subLevel instanceof ClientSubLevel client ? client.renderPose(partialTicks) : subLevel.logicalPose();
    }

    /** The given real (world) camera transformed into the sub-level's plot space. */
    public static Vec3 plotCamera(final SubLevel subLevel, final double camX, final double camY, final double camZ, final float partialTicks) {
        return renderPose(subLevel, partialTicks).transformPositionInverse(new Vec3(camX, camY, camZ));
    }

    public static void setupComposedRendering(final PoseStack matrixStack, final SableChildVecOrigin origin, final SubLevel subLevel,
                                              final double camX, final double camY, final double camZ, final float partialTicks) {
        final Pose3dc pose = renderPose(subLevel, partialTicks);
        final Vec3 plotCam = pose.transformPositionInverse(new Vec3(camX, camY, camZ));

        // the animation's own interpolated motion (plot space)
        final double offX = interpolate(origin.offXLast(), origin.offX(), partialTicks);
        final double offY = interpolate(origin.offYLast(), origin.offY(), partialTicks);
        final double offZ = interpolate(origin.offZLast(), origin.offZ(), partialTicks);
        final double rotX = Math.toRadians(interpolate(origin.rotXLast(), origin.rotX(), partialTicks));
        final double rotY = Math.toRadians(interpolate(origin.rotYLast(), origin.rotY(), partialTicks));
        final double rotZ = Math.toRadians(interpolate(origin.rotZLast(), origin.rotZ(), partialTicks));
        final Vec3d center = origin.center();

        final Vector3dc position = pose.position();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Vector3dc scale = pose.scale();

        // maps (plotPoint - plotCam) -> (worldPoint - realCam); all huge terms cancel in double precision
        final Matrix4d m = new Matrix4d();
        m.translate(-camX, -camY, -camZ);
        m.translate(position.x(), position.y(), position.z());
        m.rotate(pose.orientation());
        m.scale(scale.x(), scale.y(), scale.z());
        m.translate(-rotationPoint.x(), -rotationPoint.y(), -rotationPoint.z());
        m.translate(offX, offY, offZ);
        m.translate(center.x, center.y, center.z);
        m.rotateXYZ(rotX, rotY, rotZ);
        m.translate(-center.x, -center.y, -center.z);
        m.translate(plotCam.x, plotCam.y, plotCam.z);
        matrixStack.mulPose(new Matrix4f(m));
    }

    private static double interpolate(final double last, final double current, final float partialTicks) {
        return last + (current - last) * partialTicks;
    }
}

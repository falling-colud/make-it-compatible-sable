package dev.micsable.littletiles_sable.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.SableChildVecOrigin;
import dev.micsable.littletiles_sable.client.SableAnimationRender;
import com.mojang.blaze3d.vertex.VertexSorting;

import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;

import team.creative.littletiles.client.render.entity.LittleAnimationRenderManager;
import team.creative.littletiles.client.render.entity.LittleEntityRenderManager;

/**
 * Fix (float precision for animations on Sable plots) - the animation render manager works camera-relative:
 * chunk-offset uniforms, vertex sorting and compile-time translucency sorting all subtract the camera from block
 * positions. For an animation at plot coordinates against the <em>world</em> camera those differences are millions
 * of blocks, far beyond float32 precision. For Sable-parented animations, substitute the camera transformed into
 * the sub-level's plot space - offsets become small again, and {@code SableChildVecOrigin}'s render matrix (which
 * expects plot-camera-relative input) maps them into world space in double precision.
 */
@Mixin(LittleAnimationRenderManager.class)
public abstract class LittleAnimationRenderManagerMixin {

    @Unique
    private SableChildVecOrigin mic$sableOrigin() {
        final LittleEntityRenderManager<?> self = (LittleEntityRenderManager<?>) (Object) this;
        return self.entity != null && self.entity.getOrigin() instanceof SableChildVecOrigin origin ? origin : null;
    }

    @WrapMethod(method = "renderChunkLayer")
    private void mic$plotSpaceChunkOffset(final RenderType layer, final PoseStack pose, final double x, final double y, final double z,
                                          final Matrix4f projectionMatrix, final Uniform offset, final Operation<Void> original) {
        final SableChildVecOrigin origin = this.mic$sableOrigin();
        if (origin != null) {
            final Vec3 plotCam = SableAnimationRender.plotCamera(origin.sableParent.subLevel, x, y, z, SableAnimationRender.partialTicks());
            original.call(layer, pose, plotCam.x, plotCam.y, plotCam.z, projectionMatrix, offset);
        } else {
            original.call(layer, pose, x, y, z, projectionMatrix, offset);
        }
    }

    @WrapMethod(method = "createVertexSorting")
    private VertexSorting mic$plotSpaceSorting(final double x, final double y, final double z, final Operation<VertexSorting> original) {
        final SableChildVecOrigin origin = this.mic$sableOrigin();
        if (origin != null) {
            final Vec3 plotCam = SableAnimationRender.plotCamera(origin.sableParent.subLevel, x, y, z, SableAnimationRender.partialTicks());
            return original.call(plotCam.x, plotCam.y, plotCam.z);
        }
        return original.call(x, y, z);
    }

    @WrapMethod(method = "compileSections")
    private void mic$plotSpaceCompile(final Camera camera, final Operation<Void> original) {
        final SableChildVecOrigin origin = this.mic$sableOrigin();
        if (origin != null && !(camera instanceof SubLevelCamera)) {
            final SubLevelCamera plotCamera = new SubLevelCamera();
            plotCamera.setCamera(camera);
            plotCamera.setPose(SableAnimationRender.renderPose(origin.sableParent.subLevel, SableAnimationRender.partialTicks()));
            original.call(plotCamera);
        } else {
            original.call(camera);
        }
    }
}

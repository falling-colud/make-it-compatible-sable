package dev.micsable.littletiles_sable.mixin;

import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.client.SableTiles;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;

import team.creative.littletiles.client.render.overlay.PreviewRenderer;

/**
 * Fix #1 - placement previews on Sable sub-levels.
 *
 * <p>{@code PreviewRenderer.renderBoxes(cam, pos, ...)} draws the ghost preview by translating the model-view
 * stack by {@code (pos - cam)}. When you aim a LittleTiles item at a Sable vehicle, the raycast hit is in the
 * vehicle's hidden plot coordinates, so that plain translate draws the ghost at the plot's far-away location
 * instead of on the visible, moving vehicle.</p>
 *
 * <p>We intercept that draw: if {@code pos} is inside a Sable plot, we apply the sub-level's transform via
 * {@link SableTiles#subLevelModelMatrix} (the same mapping the vehicle's baked tiles use, with the block's
 * camera-relative origin resolved in double so it stays precise at million-block plot coordinates), so the
 * preview lands on the vehicle and rotates/scales with it.</p>
 */
@Mixin(PreviewRenderer.class)
public class PreviewRendererMixin {

    @Inject(
        method = "renderBoxes(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;ZLcom/mojang/blaze3d/vertex/MeshData;Ljava/lang/Runnable;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void mic$previewOnSubLevel(final Vec3 cam, final BlockPos pos, final boolean lines,
            final MeshData data, final Runnable adjustGL, final CallbackInfo ci) {
        final SubLevelRenderData rd = SableTiles.renderDataAt(pos);
        if (rd == null)
            return; // ordinary world - let LittleTiles render the preview normally

        final Matrix4fStack matrix = RenderSystem.getModelViewStack();
        matrix.pushMatrix();

        // Sable plots sit at coordinates in the millions; resolve the block's camera-relative origin in double
        // (small, precise) so the float model-view doesn't jitter. The mesh is block-local relative to pos.
        // Always resolve the REAL world camera here instead of trusting the cam argument: the drawHighlight path
        // now hands LittleTiles the plot-space event camera (for pose-based outlines), but this draw goes through
        // the raw model-view stack, which is world/camera-relative.
        final Vec3 worldCam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        matrix.mul(SableTiles.subLevelModelMatrix(rd, pos.getX(), pos.getY(), pos.getZ(), worldCam.x, worldCam.y, worldCam.z));
        RenderSystem.applyModelViewMatrix();

        // Mirror PreviewRenderer.renderBoxes' own draw, just with the sub-level transform applied above.
        ((PreviewRenderer) (Object) this).setupPreviewRenderer(lines);
        if (adjustGL != null)
            adjustGL.run();
        BufferUploader.drawWithShader(data);

        matrix.popMatrix();
        RenderSystem.applyModelViewMatrix();
        ci.cancel();
    }
}

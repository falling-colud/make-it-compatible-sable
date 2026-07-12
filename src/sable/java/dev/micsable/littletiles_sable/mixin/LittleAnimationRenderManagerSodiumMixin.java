package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.SableChildVecOrigin;
import dev.micsable.littletiles_sable.client.SableAnimationRender;

import team.creative.littletiles.client.mod.sodium.entity.LittleAnimationRenderManagerSodium;
import team.creative.littletiles.client.render.entity.LittleEntityRenderManager;

/**
 * Fix (LittleTiles animations invisible on Sable vehicles <em>under Sodium</em> - the actual "door disappears"
 * cause). With Sodium installed the animation is drawn by {@code LittleAnimationRenderManagerSodium}, not the
 * vanilla {@code LittleAnimationRenderManager} that {@code LittleAnimationRenderManagerMixin} patches - so that
 * mixin never runs. Sodium uploads each render region's shader offset as {@code regionOrigin - camera}
 * ({@code DefaultChunkRendererExtender.setRenderRegionOffset}). For a plot region at ~2e7 blocks against the world
 * camera that offset is ~2e7, which as a float has ~2-block precision: every vertex's sub-block detail is quantised
 * away and the whole door collapses to garbage far off screen.
 *
 * <p>Substitute the {@link CameraTransform} with the camera transformed into the sub-level's plot space, so the
 * region offset becomes {@code regionOrigin(plot) - camera(plot)} - small and precise - matching the plot-space
 * input that {@code SableChildVecOrigin}'s composed model-view matrix (set moments earlier via
 * {@code setupRendering}) already expects. Non-Sable animations are untouched.</p>
 */
@Mixin(LittleAnimationRenderManagerSodium.class)
public abstract class LittleAnimationRenderManagerSodiumMixin {

    @ModifyVariable(method = "renderChunkLayerSodium", at = @At("HEAD"), argsOnly = true, require = 0)
    private CameraTransform mic$plotSpaceRegionOffset(final CameraTransform camera) {
        final LittleEntityRenderManager<?> self = (LittleEntityRenderManager<?>) (Object) this;
        if (self.entity != null && self.entity.getOrigin() instanceof SableChildVecOrigin origin) {
            final Vec3 plotCam = SableAnimationRender.plotCamera(
                origin.sableParent.subLevel, camera.x, camera.y, camera.z, SableAnimationRender.partialTicks());
            return new CameraTransform(plotCam.x, plotCam.y, plotCam.z);
        }
        return camera;
    }
}

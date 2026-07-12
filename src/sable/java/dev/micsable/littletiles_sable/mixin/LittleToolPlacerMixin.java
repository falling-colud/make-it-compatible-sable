package dev.micsable.littletiles_sable.mixin;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.client.SableTiles;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;

import team.creative.littletiles.client.tool.LittleToolPlacer;
import team.creative.littletiles.common.placement.PlacementPosition;

/**
 * Fix (placement preview invisible on Sable vehicles). The main tile-placement ghost is drawn by
 * {@code LittleToolPlacer.renderInternal}, which positions its block-local preview mesh with a plain
 * {@code modelViewStack.translate(placedPosition - camera)}. When you aim a LittleTiles item at a vehicle, the
 * placement position is resolved in the plot's hidden coordinates (millions of blocks out), so that translate draws
 * the ghost at the far-away plot location instead of on the visible, moving vehicle - and it never picks up the
 * vehicle's rotation or scale.
 *
 * <p>When the placement lands on a Sable plot, replace that translate with the sub-level's full model transform
 * ({@link SableTiles#subLevelModelMatrix}, the same mapping the vehicle's baked tiles use, resolved camera-relative
 * in double so it stays precise at million-block coordinates), so the ghost appears on the vehicle exactly where the
 * tiles would be placed. This mirrors {@code PreviewRendererMixin}, which covers the shaper/measure tools' separate
 * {@code renderBoxes} draw path.</p>
 */
@Mixin(LittleToolPlacer.class)
public abstract class LittleToolPlacerMixin {

    @Shadow
    private PlacementPosition placedPosition;

    @WrapOperation(
        method = "renderInternal",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;translate(FFF)Lorg/joml/Matrix4f;"),
        require = 0)
    private Matrix4f mic$previewOnSubLevel(final Matrix4fStack matrix, final float dx, final float dy, final float dz,
            final Operation<Matrix4f> original, @Local(argsOnly = true) final Vec3 cam) {
        if (this.placedPosition != null) {
            final SubLevelRenderData rd = SableTiles.renderDataAt(this.placedPosition.getPos());
            if (rd != null)
                return matrix.mul(SableTiles.subLevelModelMatrix(rd,
                    this.placedPosition.getPosX(), this.placedPosition.getPosY(), this.placedPosition.getPosZ(),
                    cam.x, cam.y, cam.z));
        }
        return original.call(matrix, dx, dy, dz);
    }
}

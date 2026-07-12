package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.micsable.littletiles_sable.SableChildVecOrigin;

import team.creative.creativecore.common.util.math.collision.CollisionCoordinator;
import team.creative.creativecore.common.util.math.matrix.IVecOrigin;
import team.creative.littletiles.common.entity.LittleEntityPhysic;

/**
 * Fix (animation collision queries an astronomically large box on a Sable vehicle). LittleTiles' animation
 * collision ({@code LittleEntityPhysic.transform}) sweeps the door's box through its motion using
 * {@code CollisionCoordinator}, which reads the origin's <em>matrix decomposition</em>
 * ({@code rotation()}/{@code translation()}/{@code center()}). A {@link SableChildVecOrigin} composes the vehicle's
 * pose only through its point transforms, not that decomposition, so the coordinator produces a box mixing
 * plot-space and world-space corners - millions of blocks across - which Sable rejects ("Aborting entity get for
 * abnormally large AABB") every tick of every door on a vehicle.
 *
 * <p>Animation-vs-entity collision (a door physically shoving players) is skipped for plot animations; the door
 * still animates, renders, and is interactable. This is the same class of limitation already noted for
 * door-pushes-player while a vehicle is rotated, made explicit and crash-free.</p>
 */
@Mixin(LittleEntityPhysic.class)
public abstract class LittleEntityPhysicTransformMixin {

    @Shadow
    public abstract IVecOrigin getOrigin();

    @Inject(method = "transform", at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$skipCollisionOnPlot(final CollisionCoordinator coordinator, final CallbackInfo ci) {
        if (this.getOrigin() instanceof SableChildVecOrigin)
            ci.cancel();
    }
}

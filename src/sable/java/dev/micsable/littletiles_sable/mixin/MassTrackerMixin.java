package dev.micsable.littletiles_sable.mixin;

import java.util.function.BiFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;

import org.joml.Vector3dc;

/**
 * Fix (tile vehicles rocking between rest poses) - Sable memoizes each block state's local center of mass
 * ({@code MassTracker.BLOCK_CENTER_OF_MASS}, keyed by state, sampled at {@code BlockPos.ZERO}), which for a
 * LittleTiles block is always the block center (0.5, 0.5, 0.5): a thin floor panel "balanced" half a block above
 * its geometry, an L-shape around empty space - so contact impulses got huge lever arms and shapes kept rocking
 * from flat to upright. Both call sites of the memoized lookup ({@code build}'s two loops and
 * {@code addBlockMass}) have the position at hand: route tile blocks to their real volume-weighted tile centroid
 * ({@link SableTilePhysics#tileCenterOfMass}).
 */
@Mixin(MassTracker.class)
public class MassTrackerMixin {

    private static final String BIFUNCTION_APPLY = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

    @WrapOperation(method = "build", at = @At(value = "INVOKE", target = BIFUNCTION_APPLY))
    private static Object mic$tileCenterOfMassInBuild(final BiFunction<Object, Object, Object> centerOfMass, final Object getter,
            final Object state, final Operation<Object> original, @Local final BlockPos.MutableBlockPos blockPos) {
        final Object tile = mic$tileCenterOfMass(getter, state, blockPos);
        return tile != null ? tile : original.call(centerOfMass, getter, state);
    }

    @WrapOperation(method = "addBlockMass", at = @At(value = "INVOKE", target = BIFUNCTION_APPLY))
    private Object mic$tileCenterOfMassInAdd(final BiFunction<Object, Object, Object> centerOfMass, final Object getter,
            final Object state, final Operation<Object> original, @Local(argsOnly = true) final BlockPos blockPos) {
        final Object tile = mic$tileCenterOfMass(getter, state, blockPos);
        return tile != null ? tile : original.call(centerOfMass, getter, state);
    }

    @org.spongepowered.asm.mixin.Unique
    private static Object mic$tileCenterOfMass(final Object getter, final Object state, final BlockPos pos) {
        if (state instanceof BlockState blockState && getter instanceof BlockGetter blockGetter
            && SableTilePhysics.isTileBlock(blockState)) {
            final Vector3dc center = SableTilePhysics.tileCenterOfMass(blockGetter, pos, blockState);
            if (center != null)
                return center;
        }
        return null;
    }
}

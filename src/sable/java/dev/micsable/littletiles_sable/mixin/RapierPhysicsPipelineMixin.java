package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.PosAwareColliderBakery;
import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;

/**
 * Fix (vehicles phasing through the world, call sites) - routes the pipeline's per-block collider lookups through
 * the position-aware bakery entry point for LittleTiles blocks (see {@link RapierVoxelColliderBakeryMixin}); every
 * other block keeps the vanilla memoized per-state path. Covers full chunk-section uploads (plots and world
 * terrain) and incremental block changes (the changed block plus its six neighbors).
 */
@Mixin(RapierPhysicsPipeline.class)
public class RapierPhysicsPipelineMixin {

    private static final String GET_PHYSICS_DATA = "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery;getPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;";

    @WrapOperation(method = "handleChunkSectionAddition", at = @At(value = "INVOKE", target = GET_PHYSICS_DATA))
    private RapierVoxelColliderData mic$sectionColliderAt(final RapierVoxelColliderBakery bakery, final BlockState state,
            final Operation<RapierVoxelColliderData> original, @Local final BlockPos globalPos) {
        if (SableTilePhysics.isTileBlock(state))
            return ((PosAwareColliderBakery) (Object) bakery).mic$getPhysicsDataForBlockAt(state, globalPos);
        return original.call(bakery, state);
    }

    /** First lookup in {@code handleBlockChange}: the six neighbors (loop-local {@code pos}). */
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE", target = GET_PHYSICS_DATA, ordinal = 0))
    private RapierVoxelColliderData mic$neighborColliderAt(final RapierVoxelColliderBakery bakery, final BlockState state,
            final Operation<RapierVoxelColliderData> original, @Local(ordinal = 1) final BlockPos pos) {
        if (SableTilePhysics.isTileBlock(state))
            return ((PosAwareColliderBakery) (Object) bakery).mic$getPhysicsDataForBlockAt(state, pos);
        return original.call(bakery, state);
    }

    /** Second lookup in {@code handleBlockChange}: the changed block itself ({@code globalBlockPos}). */
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE", target = GET_PHYSICS_DATA, ordinal = 1))
    private RapierVoxelColliderData mic$changedColliderAt(final RapierVoxelColliderBakery bakery, final BlockState state,
            final Operation<RapierVoxelColliderData> original, @Local(ordinal = 0) final BlockPos globalBlockPos) {
        if (SableTilePhysics.isTileBlock(state))
            return ((PosAwareColliderBakery) (Object) bakery).mic$getPhysicsDataForBlockAt(state, globalBlockPos);
        return original.call(bakery, state);
    }
}

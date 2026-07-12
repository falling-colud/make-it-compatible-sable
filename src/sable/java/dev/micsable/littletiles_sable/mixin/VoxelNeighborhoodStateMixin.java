package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;

import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.mc.BlockTile;

/**
 * Fix (disappearing sub-levels, root) - Sable decides physics solidity per <em>block state</em>, memoized, by
 * sampling {@code getCollisionShape} at {@code BlockPos.ZERO}: a LittleTiles block (geometry in the BE) is
 * permanently "not solid", so it weighs nothing ({@code getMass} gates on this), uploads as an EMPTY physics voxel
 * and is skipped by {@code MassTracker.build}. Both methods actually receive the position and a BE-capable getter -
 * answer per position for tile blocks, before the state-memoized cache can get involved.
 */
@Mixin(VoxelNeighborhoodState.class)
public class VoxelNeighborhoodStateMixin {

    @Inject(method = "isSolid", at = @At("HEAD"), cancellable = true)
    private static void mic$tileSolidity(final BlockGetter blockGetter, final BlockPos pos, final BlockState state,
                                         final CallbackInfoReturnable<Boolean> cir) {
        if (SableTilePhysics.isTileBlock(state))
            cir.setReturnValue(SableTilePhysics.hasCollision(blockGetter, pos, state));
    }

    @Inject(method = "isFullBlock", at = @At("HEAD"), cancellable = true)
    private static void mic$tileFullBlock(final BlockGetter blockGetter, final BlockPos pos, final BlockState state,
                                          final CallbackInfoReturnable<Boolean> cir) {
        if (SableTilePhysics.isTileBlock(state)) {
            final BETiles be = BlockTile.loadBE(blockGetter, pos);
            boolean full = false;
            if (be != null) {
                try {
                    full = be.sideCache.isCollisionFullBlock();
                } catch (final Throwable ignored) {}
            }
            cir.setReturnValue(full);
        }
    }
}

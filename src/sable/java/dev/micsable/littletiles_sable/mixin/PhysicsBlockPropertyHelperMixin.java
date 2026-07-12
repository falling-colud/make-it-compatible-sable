package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;

/**
 * Fix (disappearing sub-levels, mass) - a LittleTiles block weighed 0 (its state-only solidity check failed, see
 * {@link VoxelNeighborhoodStateMixin}), so sub-levels made purely of tiles had an invalid mass tracker and Sable's
 * safety valves destroyed them. Tile blocks now weigh their real tile volume (clamped so a sliver still counts),
 * with the last contribution remembered per position: Sable "removes" mass by re-computing the old state's mass,
 * which is impossible once the BE is gone - the remembered value keeps add/remove symmetric.
 */
@Mixin(PhysicsBlockPropertyHelper.class)
public class PhysicsBlockPropertyHelperMixin {

    @Inject(method = "getMass", at = @At("HEAD"), cancellable = true)
    private static void mic$tileMass(final BlockGetter level, final BlockPos pos, final BlockState state,
                                     final CallbackInfoReturnable<Double> cir) {
        if (SableTilePhysics.isTileBlock(state))
            cir.setReturnValue(SableTilePhysics.massAt(level, pos, state));
    }
}

package dev.micsable.littletiles_sable;

import net.minecraft.core.BlockPos;

import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;

/**
 * Duck interface added onto Sable's {@code RapierVoxelColliderBakery} by {@code RapierVoxelColliderBakeryMixin}.
 *
 * <p>The vanilla bakery is a pure function of the {@code BlockState} (memoized), which can never describe a
 * LittleTiles block whose collision geometry lives in its block entity. This entry point carries the missing
 * position so tile blocks can be baked from their real boxes; every other block falls through to the vanilla
 * per-state path.</p>
 */
public interface PosAwareColliderBakery {

    /** Collider data for the block at {@code pos}, or {@code null} for "no collision" (Sable's convention). */
    RapierVoxelColliderData mic$getPhysicsDataForBlockAt(net.minecraft.world.level.block.state.BlockState state, BlockPos pos);
}

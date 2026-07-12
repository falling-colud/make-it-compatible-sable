package dev.micsable.littletiles_sable.mixin;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;

import dev.micsable.littletiles_sable.SableShapes;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;

import team.creative.creativecore.common.util.math.box.BoxesVoxelShape;

/**
 * THE collision fix - gives Sable the <b>exact</b> tile boxes of a CreativeCore {@link BoxesVoxelShape}.
 *
 * <p>Sable mixes {@code sable$allBoxes()} into the {@code VoxelShape} base class and consumes it from every
 * sub-level collision path ({@code SubLevelEntityCollision.collide}/{@code hasCollision}, step-up,
 * {@code CanFallAtleastHelper}). For LittleTiles' shapes that base implementation is doubly broken: the
 * Unsafe-allocated instance NPEs on Sable's uninitialised per-thread field, and any rebuild from the discrete
 * grid is wrong because every {@code BoxesVoxelShape} shares a static 1x1x1 full-cube grid (the real geometry
 * lives only in its {@code boxes} list). Walking on stairs, panels or any multi-tile block on a vehicle
 * therefore collided against a single garbage sliver.</p>
 *
 * <p>This mixin overrides {@code sable$allBoxes()} on the subclass to iterate the real {@code boxes} list
 * (see {@link SableShapes#exactBoxes}). Virtual dispatch makes every Sable call site - current and future -
 * pick it up automatically, on both client and server, for chunked plots and single-block sub-levels alike.</p>
 */
@Mixin(value = BoxesVoxelShape.class, remap = false)
public abstract class BoxesVoxelShapeMixin implements FastVoxelShapeIterable {

    @Override
    public Iterator<BoundingBox3dc> sable$allBoxes() {
        return SableShapes.exactBoxes((BoxesVoxelShape) (Object) this);
    }
}

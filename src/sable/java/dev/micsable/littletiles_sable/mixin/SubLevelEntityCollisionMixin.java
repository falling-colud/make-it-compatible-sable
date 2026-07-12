package dev.micsable.littletiles_sable.mixin;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.micsable.littletiles_sable.SableShapes;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;

/**
 * Fix #3 (collision) - guards every {@code sable$allBoxes()} call in {@code SubLevelEntityCollision}
 * ({@code collide} has two, {@code hasCollision} has one) against the CreativeCore Unsafe-allocated shape NPE.
 * See {@link SableShapes} for the why.
 */
@Mixin(SubLevelEntityCollision.class)
public class SubLevelEntityCollisionMixin {

    @Redirect(
        method = { "collide", "hasCollision" },
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/mixinterface/voxel_shape_iteration/FastVoxelShapeIterable;sable$allBoxes()Ljava/util/Iterator;"))
    private static Iterator<BoundingBox3dc> mic$safeAllBoxes(final FastVoxelShapeIterable shape) {
        return SableShapes.safeAllBoxes(shape);
    }
}

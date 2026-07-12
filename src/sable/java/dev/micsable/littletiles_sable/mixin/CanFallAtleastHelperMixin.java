package dev.micsable.littletiles_sable.mixin;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.micsable.littletiles_sable.SableShapes;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;

/**
 * Fix #3 (player pose / fall checks) - the other place Sable iterates a {@code VoxelShape}'s boxes, hit while the
 * player tick recomputes its pose on a sub-level. Same CreativeCore Unsafe-allocated shape NPE as the collision
 * path; same guard. See {@link SableShapes}.
 */
@Mixin(CanFallAtleastHelper.class)
public class CanFallAtleastHelperMixin {

    @Redirect(
        method = "canFallAtleastWithSubLevels",
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/mixinterface/voxel_shape_iteration/FastVoxelShapeIterable;sable$allBoxes()Ljava/util/Iterator;"))
    private static Iterator<BoundingBox3dc> mic$safeAllBoxes(final FastVoxelShapeIterable shape) {
        return SableShapes.safeAllBoxes(shape);
    }
}

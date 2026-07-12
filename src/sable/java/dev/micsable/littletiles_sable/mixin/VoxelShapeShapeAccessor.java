package dev.micsable.littletiles_sable.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the (protected, final) {@code shape} field of a {@link VoxelShape} so we can rebuild Sable's
 *  fast box iterator for shapes whose Sable state was never initialised (see {@link SubLevelEntityCollisionMixin}). */
@Mixin(VoxelShape.class)
public interface VoxelShapeShapeAccessor {

    @Accessor("shape")
    DiscreteVoxelShape mic$getShape();
}

package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import team.creative.creativecore.common.util.math.matrix.IVecOrigin;
import team.creative.littletiles.common.entity.LittleEntity;

/**
 * Writes {@code LittleEntity.origin} (protected, mirrors the animation level's origin) for
 * {@code SableAnimationSync}'s deferred origin re-parenting - LittleTiles only assigns the field in
 * {@code setSubLevel}/{@code setParentLevel}, neither of which may be re-run on a loaded entity.
 */
@Mixin(value = LittleEntity.class, remap = false)
public interface LittleEntityAccessor {

    @Accessor("origin")
    void mic$setOrigin(IVecOrigin origin);
}

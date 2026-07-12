package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.Level;

import dev.ryanhcode.sable.util.LevelAccelerator;

/**
 * Sable's mass pipeline hands {@code MassTracker.build} a {@link LevelAccelerator}, not the {@link Level} itself;
 * the tile-mass bookkeeping in {@code SableTilePhysics} needs the underlying level to key its per-level
 * contribution cache.
 */
@Mixin(LevelAccelerator.class)
public interface LevelAcceleratorAccessor {

    @Accessor("level")
    Level mic$getLevel();
}

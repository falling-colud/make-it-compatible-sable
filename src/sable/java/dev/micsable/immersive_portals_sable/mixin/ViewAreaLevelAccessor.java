package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.world.level.Level;

/** Exposes {@link ViewArea}'s level, so {@link ImmPtlViewAreaMixin} can resolve the right plot container per dimension. */
@Mixin(ViewArea.class)
public interface ViewAreaLevelAccessor {

    @Accessor("level")
    Level mic$getLevel();
}

package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import dev.micsable.littletiles_sable.SableChildVecOrigin;
import dev.micsable.littletiles_sable.SableSubLevelOrigin;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;

import team.creative.creativecore.common.util.math.matrix.ChildVecOrigin;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.littletiles.common.entity.animation.LittleAnimationLevel;

/**
 * Fix (LittleTiles animations on Sable vehicles) - when a door/animation structure activates, LittleTiles moves its
 * blocks into a {@code LittleAnimationEntity} whose origin transform is anchored at the structure's coordinates. On
 * a Sable vehicle those are far-away <em>plot</em> coordinates, so the animation rendered millions of blocks from
 * the vehicle, rays never hit it and it ignored the vehicle's motion entirely. LittleTiles already supports nested
 * origins (animations inside animations); this parents the animation's origin to the Sable sub-level's pose
 * ({@link SableSubLevelOrigin}) whenever the animation lives on a plot - rendering, culling, interaction rays and
 * collision then follow the vehicle through LittleTiles' own machinery, on both server and client.
 */
@Mixin(LittleAnimationLevel.class)
public class LittleAnimationLevelOriginMixin {

    @Inject(method = "setOrigin", at = @At("TAIL"), remap = false)
    private void mic$parentOriginToSubLevel(final Vec3d center, final CallbackInfo ci) {
        final LittleAnimationLevel self = (LittleAnimationLevel) (Object) this;
        if (self.origin instanceof ChildVecOrigin)
            return; // nested inside another LittleTiles level - its parent chain already applies
        try {
            final Level real = self.getRealLevel();
            if (real == null)
                return;
            final SubLevel subLevel = Sable.HELPER.getContaining(real, BlockPos.containing(center.x, center.y, center.z));
            if (subLevel != null && !subLevel.isRemoved())
                self.origin = new SableChildVecOrigin(new SableSubLevelOrigin(subLevel), center);
        } catch (final Throwable t) {
            dev.micsable.littletiles_sable.LittleTilesSablePatch.LOGGER.error("Failed to parent an animation origin to its Sable sub-level", t);
        }
    }
}

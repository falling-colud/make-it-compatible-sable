package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.companion.SableCompanion;

/**
 * Silences Immersive Portals' teleportation-debug logging for Sable plot-grid moves.
 *
 * <p>With {@code IPGlobal.teleportationDebugEnabled} on, Immersive Portals' {@code MixinEntity#onSetPos} logs a
 * stack trace whenever an entity's position jumps a large distance in one call. Sable constantly moves entities
 * between world space and its plot grid (~&#177;20&nbsp;million blocks away) - every embark/disembark would
 * produce a scary wall of "abrupt teleport" traces. This gates the debug-flag read so the check only fires when
 * <em>neither</em> end of the move is in the plot grid; genuine cross-portal teleport debugging is untouched.
 * (Ported from the upstream bridge's {@code MixinSquaredEntity_2}.)</p>
 */
@Mixin(value = Entity.class, priority = 2000)
public abstract class EntitySetPosLogMixin {

    @Shadow
    private Level level;

    @Shadow
    private Vec3 position;

    @TargetHandler(
        mixin = "qouteall.imm_ptl.core.mixin.common.collision.MixinEntity",
        name = "onSetPos")
    @ModifyExpressionValue(
        method = "@MixinSquared:Handler",
        at = @At(value = "FIELD", target = "Lqouteall/imm_ptl/core/IPGlobal;teleportationDebugEnabled:Z"))
    private boolean mic$noTeleportDebugForPlotGridMoves(final boolean original,
                                                        @Local(argsOnly = true, ordinal = 0) final double newX,
                                                        @Local(argsOnly = true, ordinal = 1) final double newY,
                                                        @Local(argsOnly = true, ordinal = 2) final double newZ) {
        if (!original || this.level == null)
            return original;
        return !(SableCompanion.INSTANCE.isInPlotGrid(this.level, new Vec3(newX, newY, newZ))
            || (this.position != null && SableCompanion.INSTANCE.isInPlotGrid(this.level, this.position)));
    }
}

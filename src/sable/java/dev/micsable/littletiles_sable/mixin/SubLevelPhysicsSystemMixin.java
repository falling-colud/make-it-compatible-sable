package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.SableAnimationSync;
import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.micsable.littletiles_sable.SableTilePhysicsTicker;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;

/**
 * Marks sub-levels for end-of-tick reconciliation (see {@code SableTilePhysicsTicker}) on plot block changes:
 * <ul>
 * <li>any change involving a tile block also dirties the mass tracker - LittleTiles converts blocks to/from
 * vanilla during placement and destruction along paths where the incremental mass bookkeeping cannot stay exact
 * (e.g. a secretly-placed tile block converting straight to a vanilla block was never weighed at all);</li>
 * <li>every block change dirties tile connectivity - removing a plain vanilla block can be what physically
 * separated two tile groups that only met through it.</li>
 * </ul>
 *
 * <p>Additionally guards Sable's zero-mass destruction: while a LittleTiles animation entity belongs to the
 * sub-level, its blocks are legitimately (maybe entirely) in entity form - an "invalid" tracker then means
 * "content temporarily elsewhere", not "vehicle gone". Without this, an LT-only vehicle was destroyed the moment
 * its only structure started animating.</p>
 */
@Mixin(SubLevelPhysicsSystem.class)
public class SubLevelPhysicsSystemMixin {

    @Inject(method = "updateMassDataFromBlockChange", at = @At("HEAD"))
    private void mic$markDirty(final SubLevel subLevel, final BlockPos globalBlockPos, final BlockState oldState,
                               final BlockState newState, final boolean notifyPipeline, final CallbackInfo ci) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved())
            return;
        if (SableTilePhysics.isTileBlock(oldState) || SableTilePhysics.isTileBlock(newState))
            SableTilePhysicsTicker.markTilesDirty(serverSubLevel);
        else
            SableTilePhysicsTicker.markConnectivityDirty(serverSubLevel);
    }

    @WrapOperation(method = "updateMassDataFromBlockChange",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/mass/MassTracker;isInvalid()Z"),
        require = 0)
    private boolean mic$keepAnimatedSubLevelsAlive(final MassTracker tracker, final Operation<Boolean> original,
                                                   @Local(argsOnly = true) final SubLevel subLevel) {
        if (original.call(tracker)) {
            if (subLevel instanceof ServerSubLevel serverSubLevel && SableAnimationSync.hasLiveAnimation(serverSubLevel))
                return false; // blocks are animating as an entity, not gone - don't destroy the vehicle
            return true;
        }
        return false;
    }
}

package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.micsable.littletiles_sable.SableAnimationSync;
import dev.micsable.littletiles_sable.SablePlots;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Fix (right-clicking a structure on an LT-only vehicle destroyed the whole vehicle). When a LittleTiles animation
 * activates it moves its blocks into an entity; if the structure owned <em>every</em> block of the sub-level (a
 * vehicle built purely from LittleTiles), the plot's block bounding box collapses to empty and
 * {@code ServerSubLevel.onPlotBoundsChanged} - Sable's "nothing left on the plot" valve - removed the sub-level on
 * the spot. The animation entity anchored to it was then destroyed as an orphan by {@code SableAnimationSync}:
 * structure, vehicle and blocks all gone, with the clients' entity copies left behind as ghosts.
 *
 * <p>Same principle as the mass guards ({@code SubLevelPhysicsSystemMixin}, {@code SableTilePhysics.onTilesChanged},
 * the ticker's rebuild): while a live animation belongs to the sub-level, an empty plot means "content temporarily
 * in entity form", never "vehicle gone". The sub-level is registered with
 * {@link SableAnimationSync#watchEmptyAnimatedSubLevel} instead, which re-applies Sable's removal the moment the
 * animation ends without putting any blocks back.</p>
 */
@Mixin(ServerSubLevel.class)
public class ServerSubLevelMixin {

    @Inject(method = "onPlotBoundsChanged", at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$keepAnimatedEmptyPlotAlive(final CallbackInfo ci) {
        final ServerSubLevel self = (ServerSubLevel) (Object) this;
        if (self.isRemoved())
            return;
        final BoundingBox3ic bounds = self.getPlot().getBoundingBox();
        if (SablePlots.isEmptyPlotBounds(bounds) && SableAnimationSync.hasLiveAnimation(self)) {
            SableAnimationSync.watchEmptyAnimatedSubLevel(self);
            ci.cancel();
        }
    }

    /**
     * While the plot is empty because its whole content is animating, freeze the sub-level at the pose it had when
     * the animation began and skip the rest of {@code tick} - the physics integration (no colliders, stale mass)
     * and the extreme-Y removal valve both live past this point and would drift the body to an impossible Y and
     * delete it, destroying the animated structure ("right-clicking an LT-only structure makes it disappear").
     * Only affects sub-levels registered empty+animated, so vehicles that still have blocks tick normally.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$freezeEmptyAnimated(final CallbackInfo ci) {
        if (SableAnimationSync.freezeIfEmptyAnimated((ServerSubLevel) (Object) this))
            ci.cancel();
    }
}

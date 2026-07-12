package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;

import qouteall.imm_ptl.core.render.ImmPtlViewArea;

/**
 * Routes section-dirty marks for Sable plot chunks to the sub-level's own render data when Immersive Portals owns
 * the view area (ported from the upstream bridge; resolved per-dimension instead of via the current level).
 *
 * <p>Immersive Portals replaces vanilla's {@code ViewArea} with {@link ImmPtlViewArea}, whose section grid is
 * anchored around the camera. Sable's plot chunks sit ~&#177;20&nbsp;million blocks out, far outside that grid, so
 * a block edit on a vehicle marks nothing dirty and the vehicle's mesh never rebuilds (edits stay invisible until
 * a relog). Sable normally avoids this because vanilla's {@code setDirty} is only reached for its own sections;
 * with Immersive Portals in front we re-route: if the dirtied section belongs to a plot of this view area's level,
 * mark the sub-level's render data dirty instead and skip Immersive Portals' grid entirely.</p>
 *
 * <p>The plot container is looked up on the <em>view area's</em> level (via {@link ViewAreaLevelAccessor}), not on
 * {@code Minecraft.getInstance().level} - Immersive Portals keeps one view area per visible dimension, and a
 * portal into another dimension must consult that dimension's plots.</p>
 */
@Mixin(ImmPtlViewArea.class)
public abstract class ImmPtlViewAreaMixin {

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void mic$routePlotSectionDirtyToSubLevel(final int x, final int y, final int z,
                                                     final boolean playerChanged, final CallbackInfo ci) {
        final Level level = ((ViewAreaLevelAccessor) this).mic$getLevel();
        if (level == null)
            return;
        final SubLevelContainer container = ((SubLevelContainerHolder) level).sable$getPlotContainer();
        if (container == null)
            return;
        final LevelPlot plot = container.getPlot(x, z);
        if (plot != null && plot.getSubLevel() instanceof ClientSubLevel clientSubLevel) {
            clientSubLevel.getRenderData().setDirty(x, y, z, playerChanged);
            ci.cancel();
        }
    }
}

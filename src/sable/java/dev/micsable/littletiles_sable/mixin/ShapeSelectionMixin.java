package dev.micsable.littletiles_sable.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.micsable.littletiles_sable.client.SableTiles;

import team.creative.littletiles.client.tool.shaper.ShapePosition;
import team.creative.littletiles.client.tool.shaper.ShapeSelection;

/**
 * Fix #7 - stop the crash when a shape/drag tool (e.g. the little hammer) is started on a Sable vehicle and
 * finished in the world.
 *
 * <p>{@link ShapeSelection} grows one box to include every clicked position relative to the first. If the points
 * straddle a vehicle (far-away plot coordinates) and the world, that box becomes astronomically large and crashes
 * the game. We filter the positions to a single coordinate space (the first point's) before the selection is
 * built, so an out-of-space end is simply ignored.</p>
 */
@Mixin(ShapeSelection.class)
public class ShapeSelectionMixin {

    @ModifyVariable(method = "of", at = @At("HEAD"), argsOnly = true)
    private static List<ShapePosition> mic$filterCrossSpace(final List<ShapePosition> positions) {
        return SableTiles.filterSameSpace(positions);
    }
}

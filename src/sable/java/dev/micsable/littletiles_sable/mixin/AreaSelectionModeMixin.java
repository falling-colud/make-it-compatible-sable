package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.micsable.littletiles_sable.SableInteract;

import team.creative.littletiles.common.action.source.LittleActionSource;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.item.component.SelectionComponent;
import team.creative.littletiles.common.placement.selection.AreaSelectionMode;
import team.creative.littletiles.common.placement.selection.SelectionParameters;

/**
 * Fix #2 (area mode) - refuse to build a selection whose two corners are in different coordinate spaces.
 *
 * <p>{@code select} stores {@code pos1}/{@code pos2} as raw block coordinates and takes their min/max. If one
 * corner was clicked on a Sable vehicle (plot coordinates) and the other in the world, the result spans from
 * the plot region across to the world - a box millions of blocks wide, which both produces a nonsensical
 * fill and freezes the game while {@code SelectionBuilder.scanLevel} walks every block. We return {@code null}
 * (the method's own "nothing selected" value) instead.</p>
 */
@Mixin(AreaSelectionMode.class)
public class AreaSelectionModeMixin {

    @Inject(
        method = "select(Lnet/minecraft/world/level/Level;Lteam/creative/littletiles/common/action/source/LittleActionSource;Lteam/creative/littletiles/common/placement/selection/SelectionParameters;Lnet/minecraft/world/item/ItemStack;Lteam/creative/littletiles/common/item/component/SelectionComponent;)Lteam/creative/littletiles/common/block/little/tile/group/LittleGroup;",
        at = @At("HEAD"),
        cancellable = true)
    private void mic$guardCrossSpace(final Level level, final LittleActionSource source, final SelectionParameters selection,
            final ItemStack stack, final SelectionComponent config, final CallbackInfoReturnable<LittleGroup> cir) {
        final CompoundTag nbt = config.getConfig();
        if (!nbt.contains("pos1") || !nbt.contains("pos2"))
            return;
        final int[] a = nbt.getIntArray("pos1");
        final int[] b = nbt.getIntArray("pos2");
        if (a.length < 3 || b.length < 3)
            return;
        if (SableInteract.differentSpace(level, a[0], a[2], b[0], b[2]))
            cir.setReturnValue(null);
    }
}

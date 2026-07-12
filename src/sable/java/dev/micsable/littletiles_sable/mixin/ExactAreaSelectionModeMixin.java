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
import team.creative.littletiles.common.math.box.LittleBoxAbsolute;
import team.creative.littletiles.common.placement.selection.ExactAreaSelectionMode;
import team.creative.littletiles.common.placement.selection.SelectionParameters;

/**
 * Fix #2 (exact mode) - same guard as {@link AreaSelectionModeMixin}, but this mode stores each corner as a
 * {@link LittleBoxAbsolute} (grid box + anchor block pos) instead of a raw block position.
 */
@Mixin(ExactAreaSelectionMode.class)
public class ExactAreaSelectionModeMixin {

    @Inject(
        method = "select(Lnet/minecraft/world/level/Level;Lteam/creative/littletiles/common/action/source/LittleActionSource;Lteam/creative/littletiles/common/placement/selection/SelectionParameters;Lnet/minecraft/world/item/ItemStack;Lteam/creative/littletiles/common/item/component/SelectionComponent;)Lteam/creative/littletiles/common/block/little/tile/group/LittleGroup;",
        at = @At("HEAD"),
        cancellable = true)
    private void mic$guardCrossSpace(final Level level, final LittleActionSource source, final SelectionParameters selection,
            final ItemStack stack, final SelectionComponent config, final CallbackInfoReturnable<LittleGroup> cir) {
        final CompoundTag nbt = config.getConfig();
        if (!nbt.contains("pos1") || !nbt.contains("pos2"))
            return;
        try {
            final LittleBoxAbsolute p1 = LittleBoxAbsolute.of(nbt.getIntArray("pos1"));
            final LittleBoxAbsolute p2 = LittleBoxAbsolute.of(nbt.getIntArray("pos2"));
            if (p1 == null || p2 == null || p1.pos == null || p2.pos == null)
                return;
            if (SableInteract.differentSpace(level, p1.pos.getX(), p1.pos.getZ(), p2.pos.getX(), p2.pos.getZ()))
                cir.setReturnValue(null);
        } catch (final Throwable ignored) {
            // Malformed NBT - let the real select() deal with it rather than failing here.
        }
    }
}

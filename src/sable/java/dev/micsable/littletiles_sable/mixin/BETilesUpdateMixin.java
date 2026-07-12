package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import dev.micsable.littletiles_sable.SableTilePhysics;

import team.creative.littletiles.common.block.entity.BETiles;

/**
 * Fix (stale physics after tile edits and assembly) - two moments change a tile block's collision geometry and
 * mass <em>without</em> the block-state change Sable's physics hook keys on (Sable samples inside
 * {@code LevelChunk.setBlockState}, before the block entity even exists):
 *
 * <ul>
 * <li>{@code updateTiles(boolean, boolean)} - the funnel every server-side tile mutation ends in (placement,
 * destruction, editing, structure changes).</li>
 * <li>{@code loadAdditional} with a live level - Sable's assembly ({@code moveBlocks}, used by both the assemble
 * command and the disconnect split) sets the tile block state first and applies the BE's NBT afterwards; without
 * this hook the moved block would stay massless and collision-less on its new plot.</li>
 * </ul>
 */
@Mixin(BETiles.class)
public class BETilesUpdateMixin {

    @Inject(method = "updateTiles(ZZ)V", at = @At("TAIL"))
    private void mic$notifySablePhysics(final boolean updateNeighbour, final boolean rebuildFaces, final CallbackInfo ci) {
        SableTilePhysics.onTilesChanged((BETiles) (Object) this);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mic$notifySableAfterLoad(final CompoundTag nbt, final HolderLookup.Provider provider, final CallbackInfo ci) {
        final BETiles be = (BETiles) (Object) this;
        if (be.getLevel() != null)
            SableTilePhysics.onTilesChanged(be); // no-op unless server-side in a Sable-managed level
    }
}

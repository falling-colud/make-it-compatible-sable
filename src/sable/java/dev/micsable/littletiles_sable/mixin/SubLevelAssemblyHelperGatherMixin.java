package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;

/**
 * Fix (assembling vehicles with LittleTiles structures) - Sable's connected-block gathering (the assemble command)
 * flood-fills the 18-neighborhood over non-air blocks, so parts of a build that only connect through a LittleTiles
 * structure (gaps, corner-only contact, detached door parts) were left behind in the world - and the resulting
 * torn structure misbehaved on both sides. Replaced with the structure-aware gather in
 * {@link SableTilePhysics#gatherWithStructures}: identical flood-fill semantics, plus every block of a popped tile
 * block's structure trees joins the frontier (the frontier predicate is still consulted, with a {@code null}
 * direction exactly like Sable's own diagonal connections).
 */
@Mixin(SubLevelAssemblyHelper.class)
public class SubLevelAssemblyHelperGatherMixin {

    @Inject(method = "gatherConnectedBlocks", at = @At("HEAD"), cancellable = true)
    private static void mic$gatherWithStructures(final BlockPos gatherOrigin, final ServerLevel level,
            final int maximumBlocksToAssemble, final SubLevelAssemblyHelper.FrontierPredicate frontierPredicate,
            final CallbackInfoReturnable<SubLevelAssemblyHelper.GatherResult> cir) {
        cir.setReturnValue(SableTilePhysics.gatherWithStructures(gatherOrigin, level, maximumBlocksToAssemble, frontierPredicate));
    }
}

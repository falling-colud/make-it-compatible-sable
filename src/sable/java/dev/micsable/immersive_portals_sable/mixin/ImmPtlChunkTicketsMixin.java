package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import dev.ryanhcode.sable.companion.SableCompanion;

import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;

/**
 * Keeps Immersive Portals' chunk-ticket system out of Sable's plot grid (ported from the upstream bridge).
 *
 * <p>Sable manages its plot chunks itself (loading, ticking and saving them with the sub-level). When Immersive
 * Portals' throttled ticket flusher also claims those chunks - which happens once {@code ChunkLoaderMixin} makes
 * portal loaders aware of them - the two owners fight during shutdown ticket teardown, which is the reported
 * "world stuck on the saving screen" hang (two portals + a vehicle in the same chunk). Wrapping the pending-map
 * membership check makes the flusher treat plot chunks as not-pending, so Immersive Portals never places its own
 * tickets there; the loader awareness from {@code ChunkLoaderMixin} (distance bookkeeping, load order) is kept.</p>
 */
@Mixin(ImmPtlChunkTickets.class)
public abstract class ImmPtlChunkTicketsMixin {

    @WrapOperation(
        method = "flushThrottling",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;containsKey(J)Z",
            remap = false),
        remap = false)
    private static boolean mic$noTicketsInsidePlotGrid(final Long2ObjectOpenHashMap<?> chunkMap, final long chunkPos,
                                                       final Operation<Boolean> original,
                                                       @Local(argsOnly = true) final ServerLevel level) {
        if (!original.call(chunkMap, chunkPos))
            return false;
        return !SableCompanion.INSTANCE.isInPlotGrid(level, new ChunkPos(chunkPos));
    }
}

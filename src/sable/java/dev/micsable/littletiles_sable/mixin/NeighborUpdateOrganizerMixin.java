package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;

import team.creative.littletiles.server.level.util.NeighborUpdateOrganizer;

/**
 * Fix (hidden tile faces staying invisible after a neighbor block breaks on a vehicle) - when a neighbor of a tile
 * block changes, LittleTiles collects the position server-side and ships a {@code NeighborUpdate} packet so the
 * client re-culls that block entity's cached mesh ({@code BETiles.onNeighbourChanged} &rarr;
 * {@code render.onNeighbourChanged()}). The organizer only sends positions whose chunk is within <em>vanilla view
 * distance</em> of the player - Sable plot chunks live at far-away plot coordinates, so their updates were
 * collected and then silently dropped: the freshly exposed tile face stayed unrendered until some other tile edit
 * rebuilt the cache. Treat a plot chunk as "in range" for exactly the players Sable says are tracking that
 * sub-level.
 */
@Mixin(NeighborUpdateOrganizer.class)
public class NeighborUpdateOrganizerMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lteam/creative/littletiles/server/level/util/NeighborUpdateOrganizer;checkerboardDistance(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/ServerPlayer;Z)I"))
    private int mic$plotChunksUseSableTracking(final ChunkPos chunkPos, final ServerPlayer player, final boolean useLastSectionPos,
                                               final Operation<Integer> original, @Local final Level level) {
        if (level instanceof ServerLevel serverLevel) {
            try {
                final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
                if (container != null && container.getPlot(chunkPos) != null)
                    return container.getPlayersTracking(chunkPos).contains(player) ? 0 : Integer.MAX_VALUE;
            } catch (final Throwable ignored) {
                // fall through to vanilla distance
            }
        }
        return original.call(chunkPos, player, useLastSectionPos);
    }
}

package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;

/**
 * Teaches Immersive Portals' per-player chunk loaders about Sable's plot chunks, so vehicles seen through a
 * portal stay loaded (ported from the upstream bridge).
 *
 * <p>An Immersive Portals {@link ChunkLoader} describes a square of chunks around a portal's far side that should
 * stay loaded for a player. A Sable sub-level whose world-space bounds intersect that square actually keeps its
 * blocks in far-away <em>plot</em> chunks - which Immersive Portals' ring iteration never visits, so the vehicle
 * unloads (and pops out of the portal view) even though it is right there:</p>
 *
 * <ul>
 *   <li><b>{@code foreachChunkPos}</b> - after the original ring, also emit every loaded plot chunk of each
 *       sub-level intersecting the loader's world-space bounds, at the distance of the sub-level itself (so
 *       Immersive Portals' nearest-first loading order treats the vehicle like terrain at that range);</li>
 *   <li><b>{@code getChunkNum}</b> - count those plot chunks too, keeping Immersive Portals' incremental
 *       load-budget arithmetic consistent with what {@code foreachChunkPos} emits.</li>
 * </ul>
 */
@Mixin(ChunkLoader.class)
public abstract class ChunkLoaderMixin {

    @Shadow
    @Final
    private ResourceKey<Level> dimension;

    @Shadow
    @Final
    private int x;

    @Shadow
    @Final
    private int z;

    @Shadow
    @Final
    private int radius;

    @WrapMethod(method = "foreachChunkPos")
    private void mic$alsoVisitSubLevelPlotChunks(final ChunkLoader.ChunkPosConsumer func, final Operation<Void> original) {
        original.call(func);
        final Level level = mic$loaderLevel();
        if (level == null)
            return;
        for (final SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, mic$loaderBounds(level))) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel))
                continue;
            final Vec3 subLevelPos = JOMLConversion.toMojang(serverSubLevel.logicalPose().position());
            final int distX = SectionPos.posToSectionCoord(subLevelPos.x) - this.x;
            final int distZ = SectionPos.posToSectionCoord(subLevelPos.z) - this.z;
            final int dist = Math.max(Math.abs(distX), Math.abs(distZ));
            serverSubLevel.getPlot().getLoadedChunks().forEach(holder -> {
                final ChunkPos pos = holder.getPos();
                func.consume(this.dimension, pos.x, pos.z, dist);
            });
        }
    }

    @WrapMethod(method = "getChunkNum")
    private int mic$countSubLevelPlotChunks(final Operation<Integer> original) {
        int result = original.call();
        final Level level = mic$loaderLevel();
        if (level == null)
            return result;
        for (final SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, mic$loaderBounds(level))) {
            if (subLevel instanceof ServerSubLevel serverSubLevel)
                result += serverSubLevel.getPlot().getLoadedChunks().size();
        }
        return result;
    }

    @Unique
    private Level mic$loaderLevel() {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(this.dimension);
    }

    /** World-space bounds of this loader's chunk square, full build height. */
    @Unique
    private BoundingBox3d mic$loaderBounds(final Level level) {
        final int minBlockX = SectionPos.sectionToBlockCoord(this.x - this.radius);
        final int minBlockZ = SectionPos.sectionToBlockCoord(this.z - this.radius);
        final int maxBlockX = SectionPos.sectionToBlockCoord(this.x + this.radius + 1);
        final int maxBlockZ = SectionPos.sectionToBlockCoord(this.z + this.radius + 1);
        return new BoundingBox3d(minBlockX, level.getMinBuildHeight(), minBlockZ,
            maxBlockX, level.getMaxBuildHeight(), maxBlockZ);
    }
}

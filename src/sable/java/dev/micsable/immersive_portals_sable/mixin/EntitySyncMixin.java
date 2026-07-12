package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;

import qouteall.imm_ptl.core.chunk_loading.EntitySync;

/**
 * Projects plot-grid entities into world space for Immersive Portals' entity-sync tick gate (ported from the
 * upstream bridge).
 *
 * <p>Immersive Portals replaces vanilla's per-chunk entity sync with its own {@link EntitySync} pass, which only
 * calls {@code sendChanges()} for entities whose {@code chunkPosition()} is in entity-ticking range. An entity
 * riding a Sable sub-level has raw plot-grid coordinates (~&#177;20&nbsp;million blocks out) - never "in range" -
 * so its movement/velocity/equipment updates silently stop reaching clients. Wrapping the position read to return
 * the chunk the entity <em>visually</em> occupies (its position projected out of the sub-level) restores sync for
 * everything standing on or riding a vehicle.</p>
 */
@Mixin(EntitySync.class)
public abstract class EntitySyncMixin {

    @WrapOperation(
        method = "lambda$tick$2",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private static ChunkPos mic$projectedChunkPos(final Entity entity, final Operation<ChunkPos> original) {
        final Vec3 pos = entity.position();
        final SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(entity.level(), pos);
        if (subLevel != null)
            return new ChunkPos(BlockPos.containing(subLevel.logicalPose().transformPosition(pos)));
        return original.call(entity);
    }
}

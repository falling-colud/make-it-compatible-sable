package dev.micsable.immersive_portals_sable.mixin;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

import dev.ryanhcode.sable.companion.SableCompanion;

import qouteall.imm_ptl.core.McHelper;

/**
 * Two plot-grid fixes in Immersive Portals' entity helper (ported from the upstream bridge):
 *
 * <ul>
 *   <li><b>{@code traverseEntities}</b> - Immersive Portals iterates a section cuboid and queries each section for
 *       entities. Sable's entity sections live ~&#177;20&nbsp;million blocks out in the plot grid, and the storage
 *       walk Immersive Portals uses becomes pathological once those far-away sections exist. Replaced with a single
 *       {@link LevelEntityGetter#get(EntityTypeTest, AABB, AbortableIterationConsumer) spatial AABB query} over the
 *       same cuboid plus a per-entity section filter - the visited set is identical (an entity positioned in a
 *       section within the range always intersects the box; anything merely overlapping is filtered out), including
 *       the abort-on-first-result contract, without ever walking unrelated sections.</li>
 *   <li><b>{@code adjustVehicle}</b> - skipped while the vehicle rides Sable's plot grid: the passenger's raw
 *       coordinates are plot-space while Immersive Portals reasons in world space, so its vehicle "snap" would
 *       yank riders across the map. Sable positions passengers of its sub-level entities itself.</li>
 * </ul>
 */
@Mixin(McHelper.class)
public abstract class McHelperMixin {

    @WrapMethod(method = "traverseEntities")
    private static <T extends Entity, R> R mic$traverseByBoxQuery(final Class<T> entityClass,
                                                                  final LevelEntityGetter<Entity> entityLookup,
                                                                  final int chunkXStart, final int chunkXEnd,
                                                                  final int chunkYStart, final int chunkYEnd,
                                                                  final int chunkZStart, final int chunkZEnd,
                                                                  final Function<T, R> function,
                                                                  final Operation<R> original) {
        Validate.isTrue(chunkXEnd >= chunkXStart);
        Validate.isTrue(chunkYEnd >= chunkYStart);
        Validate.isTrue(chunkZEnd >= chunkZStart);
        Validate.isTrue(chunkXEnd - chunkXStart < 1000, "range too big");
        Validate.isTrue(chunkZEnd - chunkZStart < 1000, "range too big");

        final EntityTypeTest<Entity, T> typeFilter = EntityTypeTest.forClass(entityClass);

        final AABB boundingBox = new AABB(
            SectionPos.sectionToBlockCoord(chunkXStart),
            SectionPos.sectionToBlockCoord(chunkYStart),
            SectionPos.sectionToBlockCoord(chunkZStart),
            SectionPos.sectionToBlockCoord(chunkXEnd + 1),
            SectionPos.sectionToBlockCoord(chunkYEnd + 1),
            SectionPos.sectionToBlockCoord(chunkZEnd + 1));

        final AtomicReference<R> result = new AtomicReference<>();

        entityLookup.get(typeFilter, boundingBox, entity -> {
            final SectionPos sectionPos = SectionPos.of(entity.position());
            if (sectionPos.x() < chunkXStart || sectionPos.x() > chunkXEnd
                    || sectionPos.y() < chunkYStart || sectionPos.y() > chunkYEnd
                    || sectionPos.z() < chunkZStart || sectionPos.z() > chunkZEnd)
                return AbortableIterationConsumer.Continuation.CONTINUE;
            final R oneResult = function.apply(entityClass.cast(entity));
            if (oneResult != null) {
                result.set(oneResult);
                return AbortableIterationConsumer.Continuation.ABORT;
            }
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return result.get();
    }

    @WrapMethod(method = "adjustVehicle")
    private static void mic$skipAdjustOnSubLevels(final Entity entity, final Operation<Void> original) {
        final Entity vehicle = entity.getVehicle();
        if (vehicle != null && SableCompanion.INSTANCE.isInPlotGrid(vehicle))
            return;
        original.call(entity);
    }
}

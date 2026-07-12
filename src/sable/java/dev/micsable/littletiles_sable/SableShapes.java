package dev.micsable.littletiles_sable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.micsable.littletiles_sable.mixin.VoxelShapeShapeAccessor;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.voxel_shape_iteration.FastVoxelShapeIterator;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;

import team.creative.creativecore.common.util.math.box.ABB;
import team.creative.creativecore.common.util.math.box.BoxesVoxelShape;

/**
 * Accurate + crash-safe box iteration for Sable over CreativeCore/LittleTiles shapes.
 *
 * <p>Sable's collision, step-up and fall checks consume every block's collision {@link VoxelShape} through its own
 * {@code VoxelShape.sable$allBoxes()} (an {@code Iterator<BoundingBox3dc>} of block-local boxes). Two things go wrong
 * for LittleTiles' {@link BoxesVoxelShape}:</p>
 *
 * <ol>
 * <li><b>It crashes.</b> CreativeCore allocates {@code BoxesVoxelShape} with {@code Unsafe} (skipping constructors),
 * so Sable's mixin-added per-thread iterator field is {@code null} and {@code sable$allBoxes()} NPEs.</li>
 * <li><b>Rebuilding from the discrete shape is WRONG.</b> Every {@code BoxesVoxelShape} shares a static <b>1x1x1
 * full-cube</b> {@code DISCRETE_SHAPE}; its per-axis coord lists are the sorted edges of the real tile boxes. Feeding
 * those to Sable's {@link FastVoxelShapeIterator} yields a single bogus box spanning {@code coords[0] -> coords[1]}
 * per axis - only accidentally correct for a lone full-height box. Any multi-box tile shape (stairs, panels,
 * furniture, several tiles per block) collided as a garbage sliver: the "inaccurate collisions on vehicles".</li>
 * </ol>
 *
 * <p>The real fix is {@code BoxesVoxelShapeMixin}, which overrides {@code sable$allBoxes()} on the subclass to
 * iterate the shape's <b>actual box list</b> ({@link #exactBoxes}) - every Sable call site then gets exact tile
 * geometry via ordinary virtual dispatch. The {@link #safeAllBoxes} entry point remains as the redirect target in
 * Sable's hot paths: it now simply trusts {@code sable$allBoxes()} (accurate for tiles thanks to the override) and
 * keeps the discrete-shape rebuild only as a last-resort fallback for <em>other</em> exotic shapes that skipped
 * Sable's field init.</p>
 */
public final class SableShapes {

    private SableShapes() {}

    /**
     * Per-thread cache of rebuilt fallback iterators, mirroring Sable's own per-thread {@code sable$boxIterator}.
     * Only non-{@link BoxesVoxelShape} shapes that NPE'd out of {@code sable$allBoxes()} ever land in here.
     */
    private static final ThreadLocal<WeakHashMap<VoxelShape, FastVoxelShapeIterator>> CACHE =
        ThreadLocal.withInitial(WeakHashMap::new);

    public static Iterator<BoundingBox3dc> safeAllBoxes(final FastVoxelShapeIterable shape) {
        try {
            // BoxesVoxelShape dispatches to our accurate override (BoxesVoxelShapeMixin); everything else uses
            // Sable's own per-thread iterator.
            return shape.sable$allBoxes();
        } catch (final NullPointerException ignored) {
            // An Unsafe-allocated shape without the override - rebuild the iterator the way Sable would have.
        }
        final VoxelShape vs = (VoxelShape) shape;
        if (vs instanceof BoxesVoxelShape boxes)
            return exactBoxes(boxes); // override missing (mixin failed?) - still return the exact boxes
        final WeakHashMap<VoxelShape, FastVoxelShapeIterator> cache = CACHE.get();
        FastVoxelShapeIterator it = cache.get(vs);
        if (it == null) {
            final DiscreteVoxelShape ds = ((VoxelShapeShapeAccessor) (Object) vs).mic$getShape();
            it = new FastVoxelShapeIterator(ds,
                vs.getCoords(Direction.Axis.X).toDoubleArray(),
                vs.getCoords(Direction.Axis.Y).toDoubleArray(),
                vs.getCoords(Direction.Axis.Z).toDoubleArray());
            cache.put(vs, it);
        }
        it.reset();
        return it;
    }

    /**
     * The exact, block-local collision boxes of a {@link BoxesVoxelShape} in Sable's iterator contract: each
     * {@code next()} returns a <em>reused</em> mutable box (Sable's callers copy what they keep before advancing,
     * matching {@link FastVoxelShapeIterator}'s behaviour). A fresh iterator per call keeps nested iterations of the
     * same shape (main pass + offset-shape pass in {@code SubLevelEntityCollision.collide}) independent.
     *
     * <p>Sloped/advanced {@code ABB} subclasses are represented by their axis-aligned bounds - the tightest fit
     * Sable's OBB SAT test can consume.</p>
     */
    public static Iterator<BoundingBox3dc> exactBoxes(final BoxesVoxelShape shape) {
        final List<ABB> boxes = shape.boxes;
        return new Iterator<>() {
            private final BoundingBox3d box = new BoundingBox3d();
            private int index;

            @Override
            public boolean hasNext() {
                return boxes != null && this.index < boxes.size();
            }

            @Override
            public BoundingBox3dc next() {
                if (!this.hasNext())
                    throw new NoSuchElementException();
                final ABB abb = boxes.get(this.index++);
                return this.box.set(abb.minX, abb.minY, abb.minZ, abb.maxX, abb.maxY, abb.maxZ);
            }
        };
    }
}

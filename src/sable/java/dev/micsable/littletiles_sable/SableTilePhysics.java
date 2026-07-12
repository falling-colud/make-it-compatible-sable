package dev.micsable.littletiles_sable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.micsable.littletiles_sable.mixin.LevelAcceleratorAccessor;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import team.creative.creativecore.common.util.math.box.ABB;
import team.creative.creativecore.common.util.math.box.BoxesVoxelShape;
import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.mc.BlockTile;
import team.creative.littletiles.common.structure.LittleStructure;
import team.creative.littletiles.common.structure.connection.children.StructureChildConnection;
import team.creative.littletiles.common.structure.exception.CorruptedConnectionException;
import team.creative.littletiles.common.structure.exception.NotYetConnectedException;

/**
 * The LittleTiles side of Sable's <em>server physics</em>: solidity, mass, rigid-body colliders, the
 * disconnect-split check and connected-block gathering.
 *
 * <p><b>Why every LittleTiles block was physically "air" to Sable.</b> A tile block's collision geometry lives in its
 * {@link BETiles block entity}; the {@link BlockTile} state alone always reports an empty collision shape. Sable's
 * physics however samples blocks <em>per state</em>, memoized, at {@code BlockPos.ZERO} through getters that return
 * no block entity: {@code VoxelNeighborhoodState.isSolid/isFullBlock}, {@code RapierVoxelColliderBakery} (rigid-body
 * voxel colliders) and, through the solid gate, {@code PhysicsBlockPropertyHelper.getMass}. The consequences the
 * mixins in this package fix, all through the position-aware helpers here:</p>
 *
 * <ol>
 * <li><b>Sub-levels with only tile blocks vanished.</b> Every tile block weighed 0, so the sub-level's
 * {@code MassTracker} stayed invalid (mass &le; 0, no center of mass) and Sable's safety valves
 * ({@code SubLevelHeatMapManager.split}, {@code updateMassDataFromBlockChange}, {@code recoverSubLevel}) destroyed
 * the plot. Fixed by {@code PhysicsBlockPropertyHelperMixin} + {@code VoxelNeighborhoodStateMixin}: a tile block is
 * solid iff its tiles have collision boxes, and weighs {@code clamp(totalTileVolume, 0.05, 1) * sable mass property}
 * (so a full block of tiles weighs exactly like a vanilla block).</li>
 * <li><b>Vehicles phased through the world wherever their hull was tiles.</b> The collider bakery memoized an EMPTY
 * collider for the tile-block state, so the plot's rapier body had literal holes. Fixed by
 * {@code RapierVoxelColliderBakeryMixin} / {@code RapierPhysicsPipelineMixin}: tile blocks get per-position collider
 * entries built from their real (BE) collision boxes, deduplicated by box set. Works for plot blocks <em>and</em>
 * world terrain the vehicle can hit.</li>
 * <li><b>Tile edits never reached the physics.</b> Editing tiles doesn't change the block state, so Sable's block
 * change hook never fires; {@code BETilesUpdateMixin} notifies the physics system (collider re-bake + mass delta)
 * from {@code BETiles.updateTiles}.</li>
 * <li><b>The disconnect check tore LittleTiles structures apart.</b> Sable's heat-map split and the assemble
 * command's flood-fill only see grid adjacency; a LittleTiles structure spanning a gap (or corner-only contact) was
 * "disconnected" and split off, corrupting the structure. Fixed by {@code SubLevelHeatMapManagerMixin} +
 * {@code SubLevelAssemblyHelperGatherMixin} using {@link #collectStructurePositions}: all blocks of a structure tree
 * count as connected.</li>
 * </ol>
 *
 * <p><b>Mass bookkeeping.</b> Sable removes a block's mass by re-computing {@code getMass(oldState)} - impossible
 * for tile blocks once the BE is gone. {@link #massAt} therefore remembers each tile block's last contribution per
 * level+position and replays it when asked about a position whose BE no longer exists, keeping the tracker's
 * add/remove symmetric. A solid tile block weighs exactly its Sable mass property (default 1.0) - the same flat
 * per-block mass Sable gives every vanilla partial block (slabs, fences, carpets), because volume-scaled masses
 * made tile vehicles feel light and bouncy. Decorative-only tile blocks (no collision boxes anywhere)
 * intentionally weigh 0 - identical to how vanilla Sable treats collision-less blocks like torches.</p>
 *
 * <p><b>Center of mass.</b> Sable memoizes each state's block-local center of mass and falls back to the block
 * center for empty-at-ZERO shapes - so every tile block "balanced" around (0.5, 0.5, 0.5) no matter where its
 * tiles are, making thin panels and L-shapes rock between rest poses. {@code MassTrackerMixin} routes those
 * lookups to {@link #tileCenterOfMass}: the volume-weighted centroid of the tiles actually in the block.</p>
 */
public final class SableTilePhysics {

    /**
     * Last mass contribution per position, per level (weak-keyed; plots live inside their parent
     * {@link ServerLevel}). Written whenever a live BE is weighed, consumed when the BE is already gone.
     */
    private static final Map<Level, Long2DoubleOpenHashMap> CONTRIBUTED_MASS = new WeakHashMap<>();

    /** The 18-neighborhood (6 faces + 12 edges) Sable uses for both the heat map and gathering. */
    public static final BlockPos[] CONNECTIVITY_OFFSETS;

    static {
        final java.util.List<BlockPos> offsets = new java.util.ArrayList<>(18);
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    final int total = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (total == 1 || total == 2)
                        offsets.add(new BlockPos(x, y, z));
                }
        CONNECTIVITY_OFFSETS = offsets.toArray(new BlockPos[0]);
    }

    private SableTilePhysics() {}

    public static boolean isTileBlock(final BlockState state) {
        return state.getBlock() instanceof BlockTile;
    }

    // ------------------------------------------------------------------ collision geometry

    /**
     * The tile block's real collision boxes at this position, block-local and clamped to the unit cube (Sable's
     * voxel colliders are per-voxel), flattened as {@code [minX,minY,minZ,maxX,maxY,maxZ]*}. Empty when the BE is
     * missing or none of its tiles collide.
     */
    public static DoubleArrayList collectCollisionBoxes(final BlockGetter level, final BlockPos pos, final BlockState state) {
        try {
            return boxesOf(state.getCollisionShape(level, pos, SableCollisionContext.get()));
        } catch (final Throwable ignored) {
            // corrupted BE/structure - treat as no collision rather than crashing the physics tick
            return new DoubleArrayList();
        }
    }

    private static DoubleArrayList boxesOf(final VoxelShape shape) {
        final DoubleArrayList out = new DoubleArrayList();
        if (shape instanceof BoxesVoxelShape boxes) {
            if (boxes.boxes != null)
                for (final ABB b : boxes.boxes)
                    addClamped(out, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        } else if (!shape.isEmpty()) {
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> addClamped(out, x1, y1, z1, x2, y2, z2));
        }
        return out;
    }

    /**
     * Volume-weighted centroid (block-local) of the tiles at this position, for Sable's mass tracker. Computed
     * from <em>all</em> tiles (decorative ones carry mass too), falling back to the collision boxes; {@code null}
     * when nothing is known (the caller then keeps Sable's block-center fallback).
     */
    public static Vector3dc tileCenterOfMass(final BlockGetter level, final BlockPos pos, final BlockState state) {
        DoubleArrayList boxes;
        try {
            boxes = boxesOf(state.getShape(level, pos, CollisionContext.empty()));
        } catch (final Throwable ignored) {
            boxes = new DoubleArrayList();
        }
        if (boxes.isEmpty())
            boxes = collectCollisionBoxes(level, pos, state);
        if (boxes.isEmpty())
            return null;
        double volume = 0.0, x = 0.0, y = 0.0, z = 0.0;
        for (int i = 0; i + 5 < boxes.size(); i += 6) {
            final double minX = boxes.getDouble(i), minY = boxes.getDouble(i + 1), minZ = boxes.getDouble(i + 2);
            final double maxX = boxes.getDouble(i + 3), maxY = boxes.getDouble(i + 4), maxZ = boxes.getDouble(i + 5);
            final double vol = (maxX - minX) * (maxY - minY) * (maxZ - minZ);
            volume += vol;
            x += (minX + maxX) * 0.5 * vol;
            y += (minY + maxY) * 0.5 * vol;
            z += (minZ + maxZ) * 0.5 * vol;
        }
        return volume <= 1.0E-9 ? null : new Vector3d(x / volume, y / volume, z / volume);
    }

    private static void addClamped(final DoubleArrayList out, final double x1, final double y1, final double z1,
                                   final double x2, final double y2, final double z2) {
        final double minX = Math.max(x1, 0.0), minY = Math.max(y1, 0.0), minZ = Math.max(z1, 0.0);
        final double maxX = Math.min(x2, 1.0), maxY = Math.min(y2, 1.0), maxZ = Math.min(z2, 1.0);
        if (maxX - minX <= 1.0E-7 || maxY - minY <= 1.0E-7 || maxZ - minZ <= 1.0E-7)
            return; // fully outside the block (extra-collision boxes) or degenerate
        out.add(minX);
        out.add(minY);
        out.add(minZ);
        out.add(maxX);
        out.add(maxY);
        out.add(maxZ);
    }

    /** Physics solidity of a tile block: does any of its tiles actually collide? */
    public static boolean hasCollision(final BlockGetter level, final BlockPos pos, final BlockState state) {
        return !collectCollisionBoxes(level, pos, state).isEmpty();
    }

    /** Combined volume of a flattened box list, clamped to one block. */
    public static double boxVolume(final double[] boxes) {
        double volume = 0.0;
        for (int i = 0; i + 5 < boxes.length; i += 6)
            volume += (boxes[i + 3] - boxes[i]) * (boxes[i + 4] - boxes[i + 1]) * (boxes[i + 5] - boxes[i + 2]);
        return Math.min(volume, 1.0);
    }

    /** Collider dedup key: identical box sets (+ surface properties) share one native collider entry. */
    public record BoxSetKey(double[] boxes, double friction, double restitution, boolean callback) {
        @Override
        public boolean equals(final Object obj) {
            return obj instanceof BoxSetKey other && this.friction == other.friction
                && this.restitution == other.restitution && this.callback == other.callback
                && Arrays.equals(this.boxes, other.boxes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.boxes) * 31 + Double.hashCode(this.friction + this.restitution * 31.0);
        }
    }

    // ------------------------------------------------------------------ mass

    /**
     * Position-aware replacement for {@code PhysicsBlockPropertyHelper.getMass} on tile blocks. A tile block with
     * any colliding tile weighs its full Sable mass property (default 1.0) - flat per-block mass, exactly like
     * Sable treats vanilla partial blocks (a carpet weighs the same as a stone block); the result is remembered so
     * the block's removal (BE already gone by the time Sable asks about the old state) subtracts exactly what was
     * added.
     */
    public static double massAt(final BlockGetter getter, final BlockPos pos, final BlockState state) {
        final BETiles be = BlockTile.loadBE(getter, pos);
        final Level cacheLevel = resolveLevel(getter, be);
        if (be != null) {
            final double mass = hasCollision(getter, pos, state) ? massProperty(state) : 0.0;
            if (cacheLevel != null) {
                final Long2DoubleOpenHashMap cache = cacheFor(cacheLevel);
                if (mass > 0.0)
                    cache.put(pos.asLong(), mass);
                else
                    cache.remove(pos.asLong());
            }
            return mass;
        }
        // BE gone (block being removed): replay the last contribution so the tracker stays symmetric
        if (cacheLevel != null) {
            final Long2DoubleOpenHashMap cache = CONTRIBUTED_MASS.get(cacheLevel);
            if (cache != null)
                return cache.remove(pos.asLong()); // returns 0 when absent
        }
        return 0.0;
    }

    /** The last remembered mass contribution at this position (0 if none). Does not consume the entry. */
    public static double rememberedMass(final Level level, final BlockPos pos) {
        final Long2DoubleOpenHashMap cache = CONTRIBUTED_MASS.get(level);
        return cache == null ? 0.0 : cache.get(pos.asLong());
    }

    private static Long2DoubleOpenHashMap cacheFor(final Level level) {
        return CONTRIBUTED_MASS.computeIfAbsent(level, l -> new Long2DoubleOpenHashMap());
    }

    private static Level resolveLevel(final BlockGetter getter, final BETiles be) {
        if (getter instanceof Level level)
            return level;
        if (getter instanceof LevelAccelerator accelerator)
            return ((LevelAcceleratorAccessor) accelerator).mic$getLevel();
        return be != null ? be.getLevel() : null;
    }

    private static double massProperty(final BlockState state) {
        try {
            final Double mass = ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
            return mass != null ? mass : 1.0;
        } catch (final Throwable ignored) {
            return 1.0;
        }
    }

    // ------------------------------------------------------------------ tile edits -> physics

    /**
     * Called from {@code BETiles.updateTiles} (the funnel every server-side tile mutation goes through): tile edits
     * change collision geometry and mass <em>without</em> a block state change, so Sable's own hooks never fire.
     * Applies the mass delta to the owning sub-level (if any), then re-bakes the physics voxel + collider at this
     * position (also covers tile blocks in world terrain, which Sable's physics collides vehicles against).
     */
    public static void onTilesChanged(final BETiles be) {
        try {
            final Level beLevel = be.getLevel();
            if (!(beLevel instanceof ServerLevel level) || be.isRemoved())
                return;
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                return;
            final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
            if (physicsSystem == null)
                return;
            final BlockPos pos = be.getBlockPos();
            final BlockState state = be.getBlockState();
            if (!isTileBlock(state))
                return;

            // 1) mass delta, BEFORE the re-bake below refreshes the remembered contribution
            final LevelPlot plot = container.getPlot(new ChunkPos(pos));
            if (plot != null && plot.getSubLevel() instanceof ServerSubLevel subLevel && !subLevel.isRemoved()) {
                // authoritative end-of-tick reconciliation: full mass rebuild + tile connectivity re-check
                SableTilePhysicsTicker.markTilesDirty(subLevel);
                final double old = rememberedMass(level, pos);
                final double fresh = massAt(level, pos, state); // re-remembers
                if (Math.abs(fresh - old) > 1.0E-9) {
                    final MassTracker tracker = subLevel.getSelfMassTracker();
                    final boolean animated = SableAnimationSync.hasLiveAnimation(subLevel);
                    if (animated && tracker.getMass() + fresh - old <= 1.0E-6) {
                        // A structure animating into entity form is emptying the plot (an LT-only vehicle's door
                        // takes ALL of its blocks along). The vehicle is not gone - its content is temporarily an
                        // entity - so never let the tracker reach zero (the 1/mass math turns NaN, poisons the
                        // pose, and Sable's recovery removes the sub-level) and never destroy it. Keep the last
                        // valid mass; the end-of-tick rebuild resyncs once the blocks return.
                    } else {
                        if (fresh > 0.0)
                            tracker.addBlockMass(level, state, pos, fresh, null);
                        if (old > 0.0)
                            tracker.addBlockMass(level, state, pos, -old, null);
                        if (tracker.isInvalid()) {
                            if (!animated) {
                                // vanilla parity with updateMassDataFromBlockChange: nothing massive left -> plot dies
                                subLevel.getPlot().destroyAllBlocks();
                                subLevel.markRemoved();
                                return;
                            }
                        } else {
                            subLevel.updateMergedMassData((float) physicsSystem.getPartialPhysicsTick());
                            physicsSystem.getPipeline().onStatsChanged(subLevel);
                        }
                    }
                }
            }

            // 2) re-bake neighborhood state + collider at this position (state "change" old == new)
            final LevelChunk chunk = level.getChunkAt(pos);
            final int sectionIndex = chunk.getSectionIndex(pos.getY());
            final LevelChunkSection section = chunk.getSection(sectionIndex);
            final SectionPos sectionPos = SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(sectionIndex));
            physicsSystem.handleBlockChange(sectionPos, section, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, state);
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Failed to update Sable physics after a tile change at {}", be.getBlockPos(), t);
        }
    }

    // ------------------------------------------------------------------ structure connectivity

    /**
     * Every block position rigidly connected to this block entity through LittleTiles structures: for each loaded
     * structure, the whole same-world structure tree (root via parent links, then all children). Positions are
     * collected exception-safely - a not-yet-loaded connection contributes what it can instead of failing the scan.
     */
    public static void collectStructurePositions(final BETiles be, final LongSet out) {
        try {
            for (final LittleStructure structure : be.loadedStructures()) {
                LittleStructure root = structure;
                try {
                    while (root.hasParent()) {
                        final StructureChildConnection parent = root.getParent();
                        if (parent.isLinkToAnotherWorld())
                            break;
                        root = parent.getStructure();
                    }
                } catch (CorruptedConnectionException | NotYetConnectedException | RuntimeException ignored) {
                    root = structure;
                }
                collectTreePositions(root, out, 0);
                if (root != structure)
                    collectTreePositions(structure, out, 0);
            }
        } catch (final Throwable ignored) {
            // never let structure inspection break physics/splitting
        }
    }

    private static void collectTreePositions(final LittleStructure structure, final LongSet out, final int depth) {
        if (depth > 32)
            return;
        for (final BlockPos pos : structure.positions())
            out.add(pos.asLong());
        try {
            for (final StructureChildConnection child : structure.children.all()) {
                if (child.isLinkToAnotherWorld())
                    continue;
                final LittleStructure childStructure;
                try {
                    childStructure = child.getStructure();
                } catch (CorruptedConnectionException | NotYetConnectedException | RuntimeException ignored) {
                    continue;
                }
                collectTreePositions(childStructure, out, depth + 1);
            }
        } catch (final RuntimeException ignored) {}
    }

    // ------------------------------------------------------------------ structure-aware gathering

    /**
     * Structure-aware replacement for {@code SubLevelAssemblyHelper.gatherConnectedBlocks}: the same 18-neighborhood
     * flood-fill over non-air blocks honoring the frontier predicate, plus the closure of LittleTiles structures -
     * popping a tile block also queues every block of its structure trees (predicate consulted with a {@code null}
     * direction, like Sable's own diagonal connections). Without this, assembling a vehicle leaves behind structure
     * parts that only connect through a structure (and the split that follows corrupts them).
     */
    public static SubLevelAssemblyHelper.GatherResult gatherWithStructures(final BlockPos gatherOrigin, final ServerLevel level,
            final int maximumBlocksToAssemble, final SubLevelAssemblyHelper.FrontierPredicate frontierPredicate) {
        final LevelAccelerator accelerator = new LevelAccelerator(level);
        final BlockState originState = accelerator.getBlockState(gatherOrigin);
        if (originState.isAir())
            return new SubLevelAssemblyHelper.GatherResult(null, 0, null, SubLevelAssemblyHelper.GatherResult.State.NO_BLOCKS);

        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new ObjectOpenHashSet<>(1024);
        final Set<BlockPos> blocks = new ObjectOpenHashSet<>(1024);
        final BlockPos origin = gatherOrigin.immutable();
        queue.add(origin);
        seen.add(origin);

        int minX = origin.getX(), minY = origin.getY(), minZ = origin.getZ();
        int maxX = origin.getX(), maxY = origin.getY(), maxZ = origin.getZ();
        int blockCount = 0;
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            final BlockPos pos = queue.poll();
            final BlockState posState = accelerator.getBlockState(pos);
            if (++blockCount > maximumBlocksToAssemble)
                return new SubLevelAssemblyHelper.GatherResult(null, blockCount, null, SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            blocks.add(pos);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        final int absTotal = Math.abs(x) + Math.abs(y) + Math.abs(z);
                        if (absTotal == 0 || absTotal == 3)
                            continue;
                        mutable.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                        if (seen.contains(mutable))
                            continue;
                        final BlockState candidateState = accelerator.getBlockState(mutable);
                        if (candidateState.isAir())
                            continue;
                        final Direction direction = absTotal == 1 ? Direction.fromDelta(x, y, z) : null;
                        if (frontierPredicate != null
                            && !frontierPredicate.isValidConnection(pos, posState, mutable, candidateState, direction))
                            continue;
                        final BlockPos immutable = mutable.immutable();
                        queue.add(immutable);
                        seen.add(immutable);
                    }
                }
            }

            if (isTileBlock(posState)) {
                final BETiles be = BlockTile.loadBE(accelerator, pos);
                if (be != null) {
                    final LongSet closure = new LongOpenHashSet();
                    collectStructurePositions(be, closure);
                    final LongIterator it = closure.iterator();
                    while (it.hasNext()) {
                        final BlockPos linked = BlockPos.of(it.nextLong());
                        if (seen.contains(linked))
                            continue;
                        final BlockState linkedState = accelerator.getBlockState(linked);
                        if (linkedState.isAir())
                            continue;
                        if (frontierPredicate != null
                            && !frontierPredicate.isValidConnection(pos, posState, linked, linkedState, null))
                            continue;
                        queue.add(linked);
                        seen.add(linked);
                    }
                }
            }
        }

        final BoundingBox3i bounds = new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
        return blocks.isEmpty()
            ? new SubLevelAssemblyHelper.GatherResult(null, blockCount, null, SubLevelAssemblyHelper.GatherResult.State.NO_BLOCKS)
            : new SubLevelAssemblyHelper.GatherResult(blocks, blockCount, bounds, SubLevelAssemblyHelper.GatherResult.State.SUCCESS);
    }
}

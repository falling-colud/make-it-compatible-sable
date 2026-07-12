package dev.micsable.littletiles_sable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import org.joml.Vector3dc;

import team.creative.creativecore.common.util.math.box.ABB;
import team.creative.creativecore.common.util.type.list.Pair;
import team.creative.littletiles.common.action.LittleAction;
import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.block.little.tile.LittleTile;
import team.creative.littletiles.common.block.little.tile.parent.IParentCollection;
import team.creative.littletiles.common.block.mc.BlockTile;
import team.creative.littletiles.common.grid.LittleGrid;
import team.creative.littletiles.common.math.box.LittleBox;
import team.creative.littletiles.common.structure.LittleStructure;
import team.creative.littletiles.common.structure.connection.children.StructureChildConnection;
import team.creative.littletiles.common.structure.exception.CorruptedConnectionException;
import team.creative.littletiles.common.structure.exception.NotYetConnectedException;

/**
 * Tile-geometry-aware disconnect detection for Sable sub-levels - the part Sable's block-granular heat map cannot
 * see. Sable considers any two adjacent non-air blocks connected; with LittleTiles that is wrong in both
 * directions: two neighboring tile blocks whose tiles never touch "hold" a vehicle together, and two tile groups
 * inside a <em>single</em> block (a 1-pixel slab at the bottom and another at the top) can never separate at all.
 *
 * <p>{@link #scanAndSplit} builds the real contact graph of a plot - tile boxes touching each other (face or edge
 * contact, like Sable's 18-neighborhood), tiles touching vanilla neighbor blocks, vanilla blocks touching each
 * other by plain adjacency (Sable parity), and LittleTiles structure trees as rigid links - and splits every
 * disconnected component into its own sub-level:</p>
 * <ul>
 * <li>components that own whole blocks are split with Sable's own {@code assembleBlocks} (identical to a heat-map
 * split, structures move intact);</li>
 * <li>tile groups sharing a block with another component are surgically extracted from the source block entity and
 * rebuilt in the new sub-level's plot - this is what makes the two-slabs-in-one-block case separate;</li>
 * <li>components whose shared-block tiles belong to a structure are left connected (relocating a live structure
 * across block entities is not safely possible from outside LittleTiles) - whole-block structures split fine.</li>
 * </ul>
 */
public final class SableTileConnectivity {

    /** Contact tolerance in blocks; grid coordinates are exact, this only absorbs double conversion. */
    private static final double EPS = 1.0E-5;

    private static final BlockPos[] NEIGHBORS = SableTilePhysics.CONNECTIVITY_OFFSETS;

    private SableTileConnectivity() {}

    /** One collision/tile box of a tile block, with its world-space bounds and union-find node. */
    private record TileBox(BETiles be, IParentCollection parent, LittleTile tile, LittleBox box, double[] world, int node) {}

    /** A plot block in the contact graph: either a vanilla block (one node) or a tile block (node per box). */
    private record BlockNode(int vanillaNode, List<TileBox> boxes) {
        boolean isVanilla() {
            return this.boxes == null;
        }
    }

    public static void scanAndSplit(final ServerSubLevel subLevel) {
        final ServerLevel level = subLevel.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || subLevel.isRemoved())
            return;
        final LevelPlot plot = subLevel.getPlot();

        // ---- 1. collect all solid blocks and their contact nodes
        final IntArrayList unionFind = new IntArrayList();
        final Long2ObjectMap<BlockNode> blocks = new Long2ObjectLinkedOpenHashMap<>();
        boolean anyTiles = false;
        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos chunkPos = chunk.getPos();
            final LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                final LevelChunkSection section = sections[i];
                if (section.hasOnlyAir())
                    continue;
                final int minY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            final BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir())
                                continue;
                            final BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, minY + y, chunkPos.getMinBlockZ() + z);
                            if (SableTilePhysics.isTileBlock(state)) {
                                final BlockNode node = collectTileBlock(level, pos, unionFind);
                                if (node != null) {
                                    blocks.put(pos.asLong(), node);
                                    anyTiles = true;
                                }
                            } else {
                                blocks.put(pos.asLong(), new BlockNode(newNode(unionFind), null));
                            }
                        }
                    }
                }
            }
        }
        if (!anyTiles)
            return; // pure vanilla plots are the heat map's job

        // ---- 2. unions: geometry contact across the 18-neighborhood + structure rigidity
        for (final Long2ObjectMap.Entry<BlockNode> entry : blocks.long2ObjectEntrySet()) {
            final BlockPos pos = BlockPos.of(entry.getLongKey());
            for (final BlockPos offset : NEIGHBORS) {
                final BlockPos neighborPos = pos.offset(offset);
                final BlockNode neighbor = blocks.get(neighborPos.asLong());
                if (neighbor != null)
                    unionBlocks(unionFind, entry.getValue(), pos, neighbor, neighborPos);
            }
        }
        final boolean[] structureInFlux = {false};
        unionStructures(unionFind, blocks, structureInFlux);
        if (structureInFlux[0]) {
            // A LittleTiles structure on this plot currently has broken/incomplete connections - the transient
            // state while a structure activates into (or returns from) an animation, where its own blocks briefly
            // scan as disconnected from one another. Splitting now tears the structure across sub-levels (a
            // whole-block split even relocates one of its blocks into a brand-new sub-level), corrupting the
            // animating structure; the client's render thread then thrashes rebuilding it and freezes - exactly the
            // crash seen when a structure that is the sole content of its sub-level is right-clicked to animate.
            // This is timing-independent (it reads the structure's own connection state, not whether the animation
            // entity has been recognized yet), so it also closes the window before isTopologyInFlux sees the
            // animation. Defer: leave the sub-level dirty so the scan retries once the connections settle.
            SableTilePhysicsTicker.markConnectivityDirty(subLevel);
            return;
        }

        // ---- 3. partition contents
        final Int2ObjectOpenHashMap<Partition> partitions = new Int2ObjectOpenHashMap<>();
        for (final Long2ObjectMap.Entry<BlockNode> entry : blocks.long2ObjectEntrySet()) {
            final long posLong = entry.getLongKey();
            final BlockNode node = entry.getValue();
            if (node.isVanilla()) {
                final Partition part = partitions.computeIfAbsent(find(unionFind, node.vanillaNode()), r -> new Partition());
                part.whole.add(BlockPos.of(posLong));
                part.volume += 1.0;
            } else {
                // group this block's boxes by partition root
                final Int2ObjectOpenHashMap<List<TileBox>> byRoot = new Int2ObjectOpenHashMap<>();
                for (final TileBox box : node.boxes())
                    byRoot.computeIfAbsent(find(unionFind, box.node()), r -> new ArrayList<>()).add(box);
                if (byRoot.size() == 1) {
                    final int root = byRoot.keySet().iterator().nextInt();
                    final Partition part = partitions.computeIfAbsent(root, r -> new Partition());
                    part.whole.add(BlockPos.of(posLong));
                    part.volume += volumeOf(node.boxes());
                } else {
                    for (final Int2ObjectOpenHashMap.Entry<List<TileBox>> group : byRoot.int2ObjectEntrySet()) {
                        final Partition part = partitions.computeIfAbsent(group.getIntKey(), r -> new Partition());
                        part.partial.put(posLong, group.getValue());
                        part.volume += volumeOf(group.getValue());
                        for (final TileBox box : group.getValue())
                            if (box.parent().isStructure())
                                part.hasStructurePartial = true;
                    }
                }
            }
        }
        if (partitions.size() <= 1)
            return;

        // ---- 4. keep the biggest partition, split off the rest
        Partition main = null;
        for (final Partition part : partitions.values())
            if (main == null || part.blockCount() > main.blockCount()
                || part.blockCount() == main.blockCount() && part.volume > main.volume)
                main = part;
        for (final Partition part : partitions.values()) {
            if (part == main)
                continue;
            if (part.hasStructurePartial) {
                LittleTilesSablePatch.LOGGER.info(
                    "Skipping split of a disconnected part on sub-level {}: its shared-block tiles belong to a LittleTiles structure.", subLevel);
                continue;
            }
            try {
                materialize(level, container, subLevel, part);
            } catch (final Throwable t) {
                LittleTilesSablePatch.LOGGER.error("Failed to split disconnected tile part off sub-level {}", subLevel, t);
            }
        }
    }

    // ------------------------------------------------------------------ graph construction

    private static BlockNode collectTileBlock(final ServerLevel level, final BlockPos pos, final IntArrayList unionFind) {
        if (!(level.getBlockEntity(pos) instanceof BETiles be) || be.isEmpty())
            return null; // an empty tile block is a leftover shell with no physical presence
        final List<TileBox> boxes = new ArrayList<>();
        try {
            for (final Pair<IParentCollection, LittleTile> pair : be.allTiles()) {
                final LittleGrid grid = pair.key.getGrid();
                for (final LittleBox box : pair.value) {
                    final ABB abb = box.getABB(grid);
                    final double[] world = {
                        pos.getX() + abb.minX, pos.getY() + abb.minY, pos.getZ() + abb.minZ,
                        pos.getX() + abb.maxX, pos.getY() + abb.maxY, pos.getZ() + abb.maxZ
                    };
                    boxes.add(new TileBox(be, pair.key, pair.value, box, world, newNode(unionFind)));
                }
            }
        } catch (final Throwable ignored) {
            return null; // corrupted BE - leave it out of the graph rather than break the scan
        }
        if (boxes.isEmpty())
            return null;
        // intra-block contact
        for (int i = 0; i < boxes.size(); i++)
            for (int j = i + 1; j < boxes.size(); j++)
                if (touches(boxes.get(i).world(), boxes.get(j).world()))
                    union(unionFind, boxes.get(i).node(), boxes.get(j).node());
        return new BlockNode(-1, boxes);
    }

    private static void unionBlocks(final IntArrayList unionFind, final BlockNode a, final BlockPos aPos, final BlockNode b, final BlockPos bPos) {
        if (a.isVanilla() && b.isVanilla()) {
            union(unionFind, a.vanillaNode(), b.vanillaNode()); // Sable parity: adjacency is enough for vanilla
        } else if (a.isVanilla()) {
            final double[] cube = unitCube(aPos);
            for (final TileBox box : b.boxes())
                if (touches(cube, box.world()))
                    union(unionFind, a.vanillaNode(), box.node());
        } else if (b.isVanilla()) {
            final double[] cube = unitCube(bPos);
            for (final TileBox box : a.boxes())
                if (touches(cube, box.world()))
                    union(unionFind, b.vanillaNode(), box.node());
        } else {
            for (final TileBox boxA : a.boxes())
                for (final TileBox boxB : b.boxes())
                    if (touches(boxA.world(), boxB.world()))
                        union(unionFind, boxA.node(), boxB.node());
        }
    }

    /**
     * All tiles of one structure tree are rigidly linked, no matter whether their boxes touch. If a structure's
     * connections cannot be fully resolved right now (it is mid animation-transition), {@code structureInFlux} is
     * raised so {@link #scanAndSplit} defers rather than tearing the structure apart.
     */
    private static void unionStructures(final IntArrayList unionFind, final Long2ObjectMap<BlockNode> blocks,
                                        final boolean[] structureInFlux) {
        final Reference2IntOpenHashMap<LittleStructure> representatives = new Reference2IntOpenHashMap<>();
        representatives.defaultReturnValue(-1);
        for (final BlockNode node : blocks.values()) {
            if (node.isVanilla())
                continue;
            for (final TileBox box : node.boxes()) {
                if (!box.parent().isStructure())
                    continue;
                LittleStructure root;
                try {
                    root = structureRoot(box.parent().getStructure(), structureInFlux);
                } catch (CorruptedConnectionException | NotYetConnectedException | RuntimeException ignored) {
                    structureInFlux[0] = true; // a structure mid-transition (see scanAndSplit) - don't split it apart
                    continue;
                }
                final int existing = representatives.getInt(root);
                if (existing == -1)
                    representatives.put(root, box.node());
                else
                    union(unionFind, existing, box.node());
            }
        }
    }

    private static LittleStructure structureRoot(final LittleStructure structure, final boolean[] structureInFlux) {
        LittleStructure root = structure;
        try {
            int depth = 0;
            while (root.hasParent() && depth++ < 32) {
                final StructureChildConnection parent = root.getParent();
                if (parent.isLinkToAnotherWorld())
                    break;
                root = parent.getStructure();
            }
        } catch (CorruptedConnectionException | NotYetConnectedException | RuntimeException ignored) {
            structureInFlux[0] = true; // parent chain not fully connected right now - structure mid-transition
            return structure;
        }
        return root;
    }

    // ------------------------------------------------------------------ materialization

    private static final class Partition {
        final List<BlockPos> whole = new ArrayList<>();
        final Long2ObjectMap<List<TileBox>> partial = new Long2ObjectLinkedOpenHashMap<>();
        double volume;
        boolean hasStructurePartial;

        int blockCount() {
            return this.whole.size() + this.partial.size();
        }
    }

    private static void materialize(final ServerLevel level, final ServerSubLevelContainer container,
                                    final ServerSubLevel source, final Partition part) {
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final List<BlockPos> allPositions = new ArrayList<>(part.whole);
        for (final long posLong : part.partial.keySet())
            allPositions.add(BlockPos.of(posLong));
        if (allPositions.isEmpty())
            return;
        final BoundingBox3i bounds = BoundingBox3i.from(allPositions).expand(1, 1, 1);

        final ServerSubLevel sub;
        final BlockPos anchor;
        final boolean manual = part.whole.isEmpty();
        if (!manual) {
            anchor = part.whole.get(0);
            sub = SubLevelAssemblyHelper.assembleBlocks(level, anchor, part.whole, bounds);
        } else {
            anchor = allPositions.get(0);
            final SubLevel containing = Sable.HELPER.getContaining(level, anchor);
            if (containing != null && containing.isRemoved())
                return;
            final Pose3d pose = new Pose3d();
            pose.position().set(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (containing != null) {
                final Pose3d containingPose = new Pose3d(containing.logicalPose());
                containingPose.transformPosition(pose.position());
                pose.orientation().set(containingPose.orientation());
            }
            sub = (ServerSubLevel) container.allocateNewSubLevel(pose);
            final LevelPlot plot = sub.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());
        }
        final BlockPos plotAnchor = sub.getPlot().getCenterBlock();

        // extract shared-block tile groups from their source block entities and rebuild them on the new plot
        for (final Long2ObjectMap.Entry<List<TileBox>> entry : part.partial.long2ObjectEntrySet()) {
            final BlockPos srcPos = BlockPos.of(entry.getLongKey());
            final List<TileBox> extracted = entry.getValue();
            final BETiles srcBE = extracted.get(0).be();
            final LittleGrid grid = srcBE.getGrid();

            srcBE.updateTiles(x -> {
                for (final TileBox box : extracted)
                    x.get(box.parent()).remove(box.tile(), box.box());
            });
            if (srcBE.isEmpty())
                srcBE.convertBlockToVanilla();

            final BlockPos newPos = plotAnchor.offset(srcPos.getX() - anchor.getX(), srcPos.getY() - anchor.getY(), srcPos.getZ() - anchor.getZ());
            LittleAction.setBlockPreventPredict(level, newPos, BlockTile.getStateByAttribute(level, newPos, 0), 3);
            if (!(level.getBlockEntity(newPos) instanceof BETiles targetBE)) {
                LittleTilesSablePatch.LOGGER.error("Could not create a tile block at {} while splitting sub-level {}", newPos, source);
                continue;
            }
            targetBE.convertTo(grid);
            targetBE.updateTiles(x -> {
                for (final TileBox box : extracted) {
                    final List<LittleBox> single = new ArrayList<>(1);
                    single.add(box.box().copy());
                    x.noneStructureTiles().add(new LittleTile((LittleElement) box.tile(), single));
                }
            });
            targetBE.convertToSmallest();
        }

        if (manual) {
            // mirror the tail of SubLevelAssemblyHelper.assembleBlocks now that the tiles (and their mass) exist
            final Vector3dc centerOfMass = sub.getMassTracker().getCenterOfMass();
            Vec3 subLevelCenter = Vec3.atLowerCornerOf(anchor);
            if (centerOfMass != null)
                subLevelCenter = subLevelCenter.subtract(Vec3.atLowerCornerOf(plotAnchor)).add(centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
            else
                sub.logicalPose().rotationPoint().set(plotAnchor.getX() + 0.5, plotAnchor.getY() + 0.5, plotAnchor.getZ() + 0.5);
            sub.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);
            final PhysicsPipeline pipeline = physicsSystem.getPipeline();
            final SubLevel containing = source.isRemoved() ? null : source;
            if (containing != null)
                SubLevelAssemblyHelper.kickFromContainingSubLevel(level, physicsSystem, pipeline, sub, containing);
            if (!sub.isRemoved())
                pipeline.teleport(sub, sub.logicalPose().position(), sub.logicalPose().orientation());
            sub.updateLastPose();
        }

        // parity with the heat-map split: a massless result cannot be simulated, destroy it
        if (!sub.isRemoved() && (sub.getSelfMassTracker().getCenterOfMass() == null || sub.getSelfMassTracker().getMass() <= 0.0)) {
            sub.getPlot().destroyAllBlocks();
            container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        } else {
            LittleTilesSablePatch.LOGGER.info("Split a disconnected tile part ({} block(s), {} shared-block group(s)) off sub-level {}",
                part.whole.size(), part.partial.size(), source);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static double[] unitCube(final BlockPos pos) {
        return new double[]{pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0};
    }

    private static double volumeOf(final List<TileBox> boxes) {
        double volume = 0.0;
        for (final TileBox box : boxes) {
            final double[] w = box.world();
            volume += (w[3] - w[0]) * (w[4] - w[1]) * (w[5] - w[2]);
        }
        return volume;
    }

    /**
     * Face or edge contact between two closed boxes - overlap (or exact meeting) on every axis, with at most two
     * axes merely meeting. Pure corner-point contact does not connect, matching Sable's 18-neighborhood.
     */
    private static boolean touches(final double[] a, final double[] b) {
        final double wx = Math.min(a[3], b[3]) - Math.max(a[0], b[0]);
        final double wy = Math.min(a[4], b[4]) - Math.max(a[1], b[1]);
        final double wz = Math.min(a[5], b[5]) - Math.max(a[2], b[2]);
        if (wx < -EPS || wy < -EPS || wz < -EPS)
            return false;
        final int meetings = (wx <= EPS ? 1 : 0) + (wy <= EPS ? 1 : 0) + (wz <= EPS ? 1 : 0);
        return meetings <= 2;
    }

    private static int newNode(final IntArrayList unionFind) {
        final int node = unionFind.size();
        unionFind.add(node);
        return node;
    }

    private static int find(final IntArrayList unionFind, final int node) {
        int root = node;
        while (unionFind.getInt(root) != root)
            root = unionFind.getInt(root);
        int current = node;
        while (current != root) {
            final int next = unionFind.getInt(current);
            unionFind.set(current, root);
            current = next;
        }
        return root;
    }

    private static void union(final IntArrayList unionFind, final int a, final int b) {
        final int rootA = find(unionFind, a);
        final int rootB = find(unionFind, b);
        if (rootA != rootB)
            unionFind.set(rootB, rootA);
    }
}

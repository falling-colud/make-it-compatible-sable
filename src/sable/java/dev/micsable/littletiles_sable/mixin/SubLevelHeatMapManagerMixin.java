package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import team.creative.littletiles.common.block.entity.BETiles;

/**
 * Fix (disconnect check vs. LittleTiles structures) - Sable's heat-map split only knows grid adjacency (6 faces +
 * 12 edges) over non-air blocks. A LittleTiles structure whose blocks connect through the structure itself (gaps,
 * corner-only contact, doors with detached parts) was declared "disconnected": the split tore the structure apart,
 * and the split-off half - often pure tiles that used to weigh nothing - was destroyed outright.
 *
 * <p>Before Sable's {@code split()} materializes new sub-levels, this walks every candidate group: blocks linked by
 * a LittleTiles structure tree ({@link SableTilePhysics#collectStructurePositions}) are merged into one group, and
 * any group whose structure also owns a block in the <em>main body</em> (solid but not a split candidate) is not
 * split at all - its blocks are removed from the candidate set and the heat map is re-seeded around them so the
 * incremental algorithm heals (candidates lost their heat during the CLEARING pass; flooding resumes from
 * re-discovered heat-bearing neighbors, or from the block itself for structure-only bridges the grid flood can
 * never reach).</p>
 */
@Mixin(SubLevelHeatMapManager.class)
public abstract class SubLevelHeatMapManagerMixin {

    @Shadow
    @Final
    private ServerSubLevel subLevel;

    @Shadow
    @Final
    private Long2IntOpenHashMap subLevelSplits;

    @Shadow
    @Final
    private IntArrayList splitIndexMap;

    @Shadow
    @Final
    private ObjectList<BlockPos> newStarts;

    @Shadow
    private boolean heatMapContains(final BlockPos neighbor) {
        throw new AssertionError();
    }

    @Shadow
    private void heatMapSet(final BlockPos blockPos, final short value) {
        throw new AssertionError();
    }

    @Inject(method = "split", at = @At("HEAD"), cancellable = true)
    private void mic$mergeStructureLinkedGroups(final CallbackInfo ci) {
        if (this.subLevelSplits.isEmpty())
            return;
        final Level level = this.subLevel.getLevel();
        if (level == null)
            return;
        if (dev.micsable.littletiles_sable.SableAnimationSync.isTopologyInFlux(this.subLevel, level.getGameTime())) {
            // a LittleTiles structure is mid animation-transition: its block<->structure connections are briefly
            // broken, so the heat map sees the structure's own blocks as disconnected. Splitting them now tears the
            // structure apart (the piece lands at an impossible pose and Sable removes it). Defer - subLevelSplits
            // stays populated and the heat map retries next cycle, by when the connections have settled.
            ci.cancel();
            return;
        }

        // Resolve each candidate position to its current group id, and find the tile blocks among them.
        final Long2IntOpenHashMap posToGroup = new Long2IntOpenHashMap();
        posToGroup.defaultReturnValue(-1);
        final LongArrayList tilePositions = new LongArrayList();
        for (final Long2IntMap.Entry entry : this.subLevelSplits.long2IntEntrySet()) {
            final int resolved = this.splitIndexMap.getInt(entry.getIntValue());
            posToGroup.put(entry.getLongKey(), resolved);
            final BlockState state = level.getBlockState(BlockPos.of(entry.getLongKey()));
            if (SableTilePhysics.isTileBlock(state))
                tilePositions.add(entry.getLongKey());
        }
        if (tilePositions.isEmpty())
            return; // no LittleTiles involved - vanilla split proceeds untouched

        // Union-find over group ids; groups reached by a structure that also touches the main body get anchored.
        final Int2IntOpenHashMap parent = new Int2IntOpenHashMap();
        final IntSet anchored = new IntOpenHashSet();
        boolean merged = false;
        for (final LongIterator it = tilePositions.iterator(); it.hasNext(); ) {
            final long posLong = it.nextLong();
            final BlockPos pos = BlockPos.of(posLong);
            if (!(level.getBlockEntity(pos) instanceof BETiles be))
                continue;
            final LongSet closure = new LongOpenHashSet();
            SableTilePhysics.collectStructurePositions(be, closure);
            final int group = mic$find(parent, posToGroup.get(posLong));
            for (final LongIterator linked = closure.iterator(); linked.hasNext(); ) {
                final long linkedLong = linked.nextLong();
                if (linkedLong == posLong)
                    continue;
                final int linkedGroup = posToGroup.get(linkedLong);
                if (linkedGroup != -1) {
                    if (mic$union(parent, group, linkedGroup))
                        merged = true;
                } else if (!level.getBlockState(BlockPos.of(linkedLong)).isAir()) {
                    anchored.add(mic$find(parent, group)); // structure continues into the main body
                }
            }
        }
        if (!merged && anchored.isEmpty())
            return;

        // Normalize anchors to current roots (unions after an anchor may have moved the root).
        final IntSet anchoredRoots = new IntOpenHashSet();
        for (final int group : anchored)
            anchoredRoots.add(mic$find(parent, group));

        // Drop anchored groups from the candidate set and re-seed the heat map around their blocks.
        if (!anchoredRoots.isEmpty()) {
            for (final Long2IntMap.Entry entry : posToGroup.long2IntEntrySet()) {
                if (!anchoredRoots.contains(mic$find(parent, entry.getIntValue())))
                    continue;
                final long posLong = entry.getLongKey();
                this.subLevelSplits.remove(posLong);
                final BlockPos pos = BlockPos.of(posLong);
                boolean seeded = false;
                for (final BlockPos offset : SableTilePhysics.CONNECTIVITY_OFFSETS) {
                    final BlockPos neighbor = pos.offset(offset);
                    if (this.heatMapContains(neighbor)) {
                        this.newStarts.add(neighbor);
                        seeded = true;
                    }
                }
                if (!seeded) {
                    // structure-only bridge: no grid path exists, give the block heat directly so it seeds itself
                    this.heatMapSet(pos, (short) 1);
                    this.newStarts.add(pos);
                }
            }
            if (this.subLevelSplits.isEmpty()) {
                ci.cancel(); // nothing left to split; the FILLING pass will heal the re-seeded region
                return;
            }
        }

        // Rewrite the remaining candidates with canonical merged group ids for vanilla split() to consume.
        if (merged) {
            final Long2IntOpenHashMap rewritten = new Long2IntOpenHashMap();
            final Int2IntOpenHashMap rootToId = new Int2IntOpenHashMap();
            rootToId.defaultReturnValue(-1);
            int nextId = 0;
            for (final Long2IntMap.Entry entry : this.subLevelSplits.long2IntEntrySet()) {
                final int root = mic$find(parent, this.splitIndexMap.getInt(entry.getIntValue()));
                int id = rootToId.get(root);
                if (id == -1)
                    rootToId.put(root, id = nextId++);
                rewritten.put(entry.getLongKey(), id);
            }
            this.subLevelSplits.clear();
            this.subLevelSplits.putAll(rewritten);
            this.splitIndexMap.clear();
            for (int i = 0; i < nextId; i++)
                this.splitIndexMap.add(i);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static int mic$find(final Int2IntOpenHashMap parent, final int group) {
        int root = group;
        while (parent.getOrDefault(root, root) != root)
            root = parent.get(root);
        int current = group;
        while (current != root) { // path compression
            final int next = parent.getOrDefault(current, root);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean mic$union(final Int2IntOpenHashMap parent, final int a, final int b) {
        final int rootA = mic$find(parent, a);
        final int rootB = mic$find(parent, b);
        if (rootA == rootB)
            return false;
        parent.put(rootB, rootA);
        return true;
    }
}

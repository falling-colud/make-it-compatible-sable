package dev.micsable.littletiles_sable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.mixin.LittleEntityAccessor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import team.creative.creativecore.common.level.ISubLevel;
import team.creative.creativecore.common.network.CreativeNetwork;
import team.creative.creativecore.common.network.CreativePacket;
import team.creative.creativecore.common.util.math.matrix.ChildVecOrigin;
import team.creative.creativecore.common.util.math.matrix.IVecOrigin;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.littletiles.LittleTiles;
import team.creative.littletiles.common.entity.LittleEntity;
import team.creative.littletiles.common.entity.animation.LittleAnimationEntity;
import team.creative.littletiles.common.entity.animation.LittleAnimationLevel;
import team.creative.littletiles.common.level.handler.LittleAnimationHandler;

/**
 * Server&harr;client synchronization for LittleTiles animation entities living on Sable plots.
 *
 * <p><b>Why this exists.</b> LittleTiles reaches clients through vanilla entity tracking (the add-entity packet
 * spawns the client entity, NeoForge's start-tracking event triggers LittleTiles' init packet, all further packets
 * go to "players tracking this entity"). A door on a Sable vehicle animates at plot coordinates - millions of
 * blocks away - and, because {@code LittleEntity.setPos} uses {@code setPosRaw}, the entity is filed forever in the
 * entity-section where {@code addFreshEntity} put it (plot coordinates). No player tracks that section, so vanilla
 * never pairs anyone and none of LittleTiles' networking fires.</p>
 *
 * <p><b>The fix, anchored to the sub-level (not the entity's transient position).</b> A plot animation entity is
 * recognized by its origin ({@link SableChildVecOrigin}, installed by {@code LittleAnimationLevelOriginMixin}),
 * which yields the owning {@link SubLevel} regardless of where the entity's position currently is (it starts at
 * plot coordinates and moves to world coordinates after the first tick). Each such entity is paired with exactly
 * the players Sable reports as tracking that sub-level, for the whole animation lifetime:</p>
 * <ul>
 * <li>the vanilla add-entity packet is sent at the entity's <b>world</b> position (its origin-transformed bounding
 * box centre) - never the plot position, or the client would file it in an unloaded section and drop it a tick
 * later (the "clicking a door makes it disappear" bug);</li>
 * <li>then LittleTiles' own init packet, then {@code startTracking} - mirroring
 * {@code LittleAnimationHandlers.trackEntity};</li>
 * <li>players who stop tracking the sub-level (or disconnect) are unpaired and the entity is despawned for them,
 * and removed entities are despawned for all their paired clients.</li>
 * </ul>
 *
 * <p>Vanilla's own tracking of these entities is suppressed ({@code LittleAnimationEntityBroadcastMixin} returns
 * {@code false} from {@code broadcastToPlayer}) so this class is their sole networking owner - no duplicate spawns
 * once the entity's {@code chunkPosition} drifts into world coordinates.</p>
 *
 * <p>Two more lifetime duties live in {@link #serverTick}: {@link #reparentLoadedAnimation} re-attaches animation
 * entities deserialized on world load to their sub-level (Sable materializes sub-levels after chunk entities load,
 * so the load-time origin lookup always misses - doors vanished on every reload), and {@link #EMPTY_WHILE_ANIMATED}
 * finishes Sable's empty-plot removal for sub-levels {@code ServerSubLevelMixin} kept alive while their entire
 * content was animating (an LT-only vehicle's door takes every block with it).</p>
 */
public final class SableAnimationSync {

    private static final Map<LittleEntity, Set<ServerPlayer>> PAIRED = new WeakHashMap<>();

    /**
     * Sub-levels whose plot went completely empty while a live animation owned their content ({@code
     * ServerSubLevelMixin} spared them from Sable's empty-bounds removal), mapped to the pose they had at that
     * moment. While watched, the vehicle is pinned to that pose every tick - its rigid body has no colliders and
     * a frozen mass left, so gravity would otherwise sink it through the world during the animation. The watch
     * ends when the animation puts blocks back (plot non-empty again) or dies without any - then Sable's removal
     * is applied after all, so a destroyed animation cannot leak an invisible, empty vehicle.
     */
    private static final Map<ServerSubLevel, Pose3d> EMPTY_WHILE_ANIMATED = new WeakHashMap<>();

    /**
     * Last game-tick each sub-level had a live animation. A LittleTiles structure moving between block and entity
     * form breaks and re-establishes its block↔structure connections over a few ticks; during that window the
     * connectivity scan ({@link SableTileConnectivity}) sees the structure's own blocks as disconnected and splits
     * them into a new sub-level, which then lands at an impossible pose and Sable removes it - the animated
     * structure "fully disappears". Splits are deferred while an animation is live and for {@link #SETTLE_TICKS}
     * afterwards, by which point the connections have settled and the blocks scan as one structure again.
     */
    private static final Map<ServerSubLevel, Long> LAST_ANIMATED = new WeakHashMap<>();

    /** Ticks after an animation ends during which topology splits stay deferred (structure re-connection window). */
    private static final int SETTLE_TICKS = 40;

    private SableAnimationSync() {}

    /**
     * Whether the sub-level must not be re-partitioned right now: a live animation belongs to it, or one ended
     * within the last {@link #SETTLE_TICKS} ticks (its structure connections may still be re-establishing).
     */
    public static boolean isTopologyInFlux(final ServerSubLevel subLevel, final long gameTime) {
        if (hasLiveAnimation(subLevel))
            return true;
        final Long last = LAST_ANIMATED.get(subLevel);
        return last != null && gameTime - last < SETTLE_TICKS;
    }

    /** The Sable sub-level a LittleTiles entity is animating on, or {@code null} if it is not on a plot. */
    public static SubLevel subLevelOf(final Entity entity) {
        if (entity instanceof LittleEntity littleEntity && littleEntity.getOrigin() instanceof SableChildVecOrigin origin)
            return origin.sableParent.subLevel;
        return null;
    }

    /**
     * A server-side animation entity anchored inside Sable's plot-coordinate region but not (yet) parented to a
     * sub-level - the state a freshly deserialized plot door is in until {@link #serverTick}'s
     * {@link #reparentLoadedAnimation} runs (Sable materializes sub-levels only after chunk entities have loaded).
     * Vanilla tracking must leave these alone exactly like parented ones ({@code EntityBroadcastMixin}): a vanilla
     * pairing during that gap is followed by a vanilla remove the moment the origin re-parents, which would wipe
     * the client copy this patch just spawned.
     */
    public static boolean isOrphanedPlotAnimation(final Entity entity) {
        try {
            if (!(entity instanceof LittleEntity littleEntity) || !(entity.level() instanceof ServerLevel level)
                || !(littleEntity.getSubLevel() instanceof LittleAnimationLevel animationLevel))
                return false;
            final IVecOrigin origin = animationLevel.getOrigin();
            if (origin == null || origin instanceof ChildVecOrigin)
                return false;
            final SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                return false;
            final Vec3d center = origin.center();
            return container.inBounds(BlockPos.containing(center.x, center.y, center.z));
        } catch (final Throwable ignored) {
            return false;
        }
    }

    /**
     * Whether any live LittleTiles animation entity currently belongs to this sub-level. While one exists, the
     * sub-level's blocks may legitimately be (partially or completely) in entity form - an empty plot must then be
     * treated as "contents temporarily elsewhere", never as "vehicle gone" (see the mass guards in
     * {@code SableTilePhysics}/{@code SubLevelPhysicsSystemMixin} and the empty-bounds guard in
     * {@code ServerSubLevelMixin}: destroying an LT-only vehicle the moment its only structure started animating
     * was exactly how activating doors made whole vehicles vanish).
     */
    public static boolean hasLiveAnimation(final ServerSubLevel subLevel) {
        try {
            final LittleAnimationHandler handler = LittleTiles.ANIMATION_HANDLERS.get(subLevel.getLevel());
            if (handler == null)
                return false;
            for (final LittleEntity entity : handler.entities)
                if (!entity.isRemoved() && subLevelOf(entity) == subLevel)
                    return true;
        } catch (final Throwable ignored) {}
        return false;
    }

    /**
     * Deterministic client despawn when a plot animation entity is removed server-side (called from
     * {@code LittleEntityRemovalMixin}). The periodic {@code PAIRED} sweep alone raced the garbage collector: the
     * map is weak-keyed, so a removed entity could vanish from it before the sweep ran and its client copy leaked -
     * a visible "ghost" door that the server no longer knows ("entity not found" on click).
     */
    public static void onEntityRemoved(final LittleEntity entity) {
        final Set<ServerPlayer> paired = PAIRED.remove(entity);
        if (paired == null)
            return;
        for (final ServerPlayer player : paired)
            if (!player.hasDisconnected())
                player.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId()));
    }

    private static List<ServerPlayer> trackers(final SubLevel subLevel, final MinecraftServer server) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved())
            return List.of();
        final List<ServerPlayer> players = new ArrayList<>();
        for (final UUID uuid : serverSubLevel.getTrackingPlayers()) {
            final ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && !player.hasDisconnected())
                players.add(player);
        }
        return players;
    }

    /** Registers a sub-level whose plot emptied into a live animation (see {@link #EMPTY_WHILE_ANIMATED}). */
    public static void watchEmptyAnimatedSubLevel(final ServerSubLevel subLevel) {
        if (EMPTY_WHILE_ANIMATED.putIfAbsent(subLevel, new Pose3d(subLevel.logicalPose())) == null)
            LittleTilesSablePatch.LOGGER.info("[anim] plot of {} emptied into a live animation - frozen until it returns", subLevel);
    }

    /**
     * Freezes a sub-level whose entire content is currently animating (empty plot) at the pose it had when the
     * animation began - called from {@code ServerSubLevelMixin} at the head of {@code ServerSubLevel.tick}. Without
     * it, the collider-less, stale-mass rigid body drifts under gravity and Sable's extreme-Y valve (inside the very
     * {@code tick} we cancel) removes the sub-level a second later, destroying the animated structure. Restoring the
     * pose and cancelling the tick is <b>pure Java on purpose</b>: the rigid body itself is corrected by {@link #pin}
     * from the end-of-tick reconcile, the only phase where touching the native physics pipeline is proven safe
     * (Sable itself defers body wake-ups that would land inside the physics step; a native call from the wrong
     * phase kills the JVM with no Java trace).
     *
     * @return {@code true} if the sub-level was frozen (caller should cancel the tick)
     */
    public static boolean freezeIfEmptyAnimated(final ServerSubLevel subLevel) {
        final Pose3d pinned = EMPTY_WHILE_ANIMATED.get(subLevel);
        if (pinned == null || subLevel.isRemoved())
            return false;
        try {
            subLevel.logicalPose().set(pinned);
            subLevel.updateLastPose();
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Failed to freeze empty animated sub-level {}", subLevel, t);
        }
        return true;
    }

    public static void serverTick(final MinecraftServer server) {
        for (final ServerLevel level : server.getAllLevels()) {
            final LittleAnimationHandler handler = LittleTiles.ANIMATION_HANDLERS.get(level);
            if (handler == null || handler.entities.isEmpty())
                continue;
            for (final LittleEntity entity : handler.entities) {
                SubLevel subLevel = subLevelOf(entity);
                if (subLevel == null) {
                    reparentLoadedAnimation(entity, level);
                    subLevel = subLevelOf(entity);
                }
                if (subLevel == null)
                    continue;
                if (subLevel.isRemoved()) {
                    // the vehicle is gone (destroyed, unloaded improperly, ...): an animation anchored to it can
                    // never resolve its structure or return to block form - it would float at the frozen pose
                    // forever as an uninteractable ghost. Put it down cleanly (despawns via onEntityRemoved).
                    try {
                        entity.destroyAnimation();
                    } catch (final Throwable t) {
                        LittleTilesSablePatch.LOGGER.error("Failed to remove orphaned animation entity {}", entity, t);
                    }
                    continue;
                }
                if (subLevel instanceof ServerSubLevel serverSubLevel)
                    LAST_ANIMATED.put(serverSubLevel, level.getGameTime()); // defer topology splits (see isTopologyInFlux)
                sync(entity, subLevel, server);
            }
        }
        PAIRED.entrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved())
                return false;
            for (final ServerPlayer player : entry.getValue())
                if (!player.hasDisconnected())
                    player.connection.send(new ClientboundRemoveEntitiesPacket(entry.getKey().getId()));
            return true;
        });
        EMPTY_WHILE_ANIMATED.entrySet().removeIf(entry -> {
            final ServerSubLevel subLevel = entry.getKey();
            if (subLevel.isRemoved())
                return true;
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (!SablePlots.isEmptyPlotBounds(bounds))
                return true; // the animation put blocks back - a normal vehicle again
            if (hasLiveAnimation(subLevel)) {
                pin(subLevel, entry.getValue());
                return false;
            }
            // the animation ended without restoring a single block: apply the empty-plot removal it was spared from
            LittleTilesSablePatch.LOGGER.info("[anim] animation on {} ended without restoring blocks - removing the empty sub-level", subLevel);
            subLevel.markRemoved();
            return true;
        });
    }

    /** Holds a collider-less, content-in-entity-form vehicle at the pose it had when its plot emptied. */
    private static void pin(final ServerSubLevel subLevel, final Pose3d pose) {
        try {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            if (container == null || container.physicsSystem() == null)
                return;
            final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
            pipeline.resetVelocity(subLevel);
            pipeline.teleport(subLevel, pose.position(), pose.orientation());
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Failed to pin animated empty sub-level {}", subLevel, t);
        }
    }

    /**
     * Re-parents a deserialized animation entity to its Sable sub-level when that was impossible at load time.
     * On world load the saved entity comes back with the chunk that holds it (near the vehicle's world position) -
     * but Sable only materializes sub-levels from its holding chunks once all chunks covering the vehicle are
     * loaded, so {@code LittleAnimationLevelOriginMixin}'s {@code getContaining} lookup during entity load finds
     * nothing and the origin stays a plain {@code VecOrigin} at plot coordinates. The entity then sat invisible
     * millions of blocks away and the next save filed it into the plot's entity region, where no chunk ever loads
     * again - every animated door on a vehicle silently vanished across a world reload. Retried each tick until
     * the sub-level exists; entities that genuinely animate in the world (no plot at their center) stay untouched.
     */
    private static void reparentLoadedAnimation(final LittleEntity entity, final ServerLevel level) {
        try {
            if (entity.isRemoved() || entity.level() != level || !(entity.getSubLevel() instanceof LittleAnimationLevel animationLevel))
                return;
            final IVecOrigin origin = animationLevel.getOrigin();
            if (origin == null || origin instanceof ChildVecOrigin)
                return; // nested inside another fake level (parent chain applies) or already parented
            final Vec3d center = origin.center();
            final SubLevel subLevel = Sable.HELPER.getContaining(level, BlockPos.containing(center.x, center.y, center.z));
            if (subLevel == null || subLevel.isRemoved())
                return;
            final SableChildVecOrigin parented = new SableChildVecOrigin(new SableSubLevelOrigin(subLevel), new Vec3d(center));
            parented.set(origin); // keep the saved mid-animation pose (offset + rotation, last and current)
            animationLevel.origin = parented;
            ((LittleEntityAccessor) (Object) entity).mic$setOrigin(parented);
            if (entity instanceof LittleAnimationEntity animation)
                animation.setCenter(animation.getCenter()); // reroutes child origins to the new parent
            entity.markOriginChange();
            LittleTilesSablePatch.LOGGER.info("[anim] re-parented loaded animation entity {} to sub-level {}", entity.getUUID(), subLevel);
            // recompute bounding box + position through the new origin NOW: sync() pairs this entity later in the
            // same tick and spawns it client-side at the bounding box center, which still holds plot coordinates
            // (the client would file that in an unloaded section and discard it - an invisible door until relog)
            if (entity.physic.getOBB() != null) {
                entity.physic.setBB(entity.physic.getOBB());
                entity.physic.updateBoundingBox();
                final Vec3 worldCenter = entity.physic.getCenter();
                if (worldCenter != null)
                    entity.setPos(worldCenter.x, worldCenter.y, worldCenter.z);
            }
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Failed to re-parent loaded animation entity {} to its Sable sub-level", entity, t);
        }
    }

    /** Reconciles the players paired with this animation entity against the sub-level's current trackers. */
    private static void sync(final LittleEntity entity, final SubLevel subLevel, final MinecraftServer server) {
        if (entity.isRemoved())
            return;
        final List<ServerPlayer> desired = trackers(subLevel, server);
        final Set<ServerPlayer> paired = PAIRED.computeIfAbsent(entity, e -> new ReferenceOpenHashSet<>());
        for (final ServerPlayer player : desired)
            if (paired.add(player))
                pair(entity, player);
        paired.removeIf(player -> {
            if (!player.hasDisconnected() && desired.contains(player))
                return false;
            unpair(entity, player);
            return true;
        });
    }

    private static void pair(final LittleEntity entity, final ServerPlayer player) {
        try {
            // world position, NOT entity.getX/Y/Z() (plot coords at spawn) - the client must file it in a loaded
            // section near the vehicle or its transient entity manager discards it on the next tick
            final Vec3 world = entity.getBoundingBox().getCenter();
            player.connection.send(new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(),
                world.x, world.y, world.z, entity.getXRot(), entity.getYRot(),
                entity.getType(), 0, entity.getDeltaMovement(), entity.getYHeadRot()));
            LittleTiles.NETWORK.sendToClient(entity.initClientPacket(), player);
            entity.startTracking(player);
        } catch (final Throwable t) {
            LittleTilesSablePatch.LOGGER.error("Failed to sync animation entity {} to {}", entity, player.getGameProfile().getName(), t);
        }
    }

    private static void unpair(final LittleEntity entity, final ServerPlayer player) {
        try {
            entity.stopTracking(player);
        } catch (final Throwable ignored) {}
        if (!player.hasDisconnected())
            player.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId()));
    }

    // ------------------------------------------------------------------ packet rerouting (CreativeNetworkMixin)

    /**
     * Reroutes a "send to players tracking this entity" to the sub-level's Sable trackers when the entity is a plot
     * animation (vanilla tracking knows nobody there). Pairs first so the packet never beats the entity to the
     * client - covers the block&harr;entity transitions, animation starts and origin changes fired synchronously
     * during activation, before the next {@link #serverTick}.
     *
     * @return true when handled (the vanilla send must be cancelled)
     */
    public static boolean redirectEntityTracking(final CreativeNetwork network, final CreativePacket packet, final Entity entity) {
        final SubLevel subLevel = subLevelOf(entity);
        if (subLevel == null || !(entity.level() instanceof ServerLevel level))
            return false;
        sync((LittleEntity) entity, subLevel, level.getServer());
        for (final ServerPlayer player : trackers(subLevel, level.getServer()))
            network.sendToClient(packet, player);
        return true;
    }

    /** Reroutes "send to players tracking this chunk" for Sable plot chunks (the vehicle body's own tile updates). */
    public static boolean redirectChunkTracking(final CreativeNetwork network, final CreativePacket packet, final LevelChunk chunk) {
        if (chunk.getLevel() instanceof ISubLevel || !(chunk.getLevel() instanceof ServerLevel level))
            return false;
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getPlot(chunk.getPos()) == null)
            return false;
        for (final ServerPlayer player : container.getPlayersTracking(chunk.getPos()))
            network.sendToClient(packet, player);
        return true;
    }
}

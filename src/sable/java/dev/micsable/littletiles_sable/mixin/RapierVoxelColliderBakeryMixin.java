package dev.micsable.littletiles_sable.mixin;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import dev.micsable.littletiles_sable.PosAwareColliderBakery;
import dev.micsable.littletiles_sable.SableTilePhysics;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Fix (vehicles phasing through the world) - Sable bakes one rigid-body voxel collider per <em>block state</em>,
 * memoized, sampling {@code getCollisionShape} at {@code BlockPos.ZERO} through a getter with no block entities.
 * For a LittleTiles block that is always an empty shape, so every tile block on a plot (and in world terrain) was
 * a hole in the rapier body. This adds the position-aware entry point ({@link PosAwareColliderBakery}) used by
 * {@code RapierPhysicsPipelineMixin}: tile blocks are baked from their real BE collision boxes, with one native
 * collider entry shared per distinct box set (tile builds repeat shapes heavily).
 */
@Mixin(RapierVoxelColliderBakery.class)
public abstract class RapierVoxelColliderBakeryMixin implements PosAwareColliderBakery {

    @Shadow
    public abstract RapierVoxelColliderData getPhysicsDataForBlock(BlockState state);

    /** The BE-capable getter Sable constructed the bakery with (the wrapped field hides block entities). */
    @Unique
    private BlockGetter mic$realLevel;

    @Unique
    private Object2ObjectOpenHashMap<SableTilePhysics.BoxSetKey, RapierVoxelColliderData> mic$tileColliders;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mic$captureRealLevel(final BlockGetter blockGetter, final CallbackInfo ci) {
        this.mic$realLevel = blockGetter;
        this.mic$tileColliders = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public RapierVoxelColliderData mic$getPhysicsDataForBlockAt(final BlockState state, final BlockPos pos) {
        if (!SableTilePhysics.isTileBlock(state) || this.mic$realLevel == null)
            return this.getPhysicsDataForBlock(state);
        final DoubleArrayList boxList = SableTilePhysics.collectCollisionBoxes(this.mic$realLevel, pos, state);
        if (boxList.isEmpty())
            return null; // Sable's convention for "no collision here"
        final double friction = PhysicsBlockPropertyHelper.getFriction(state);
        final double restitution = PhysicsBlockPropertyHelper.getRestitution(state);
        final BlockSubLevelCollisionCallback callback = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        final SableTilePhysics.BoxSetKey key = new SableTilePhysics.BoxSetKey(boxList.toDoubleArray(), friction, restitution, callback != null);
        final RapierVoxelColliderData cached = this.mic$tileColliders.get(key);
        if (cached != null)
            return cached;
        final double[] boxes = key.boxes();
        final RapierVoxelColliderData entry = Rapier3D.createVoxelColliderEntry(friction, SableTilePhysics.boxVolume(boxes), restitution, false, callback);
        for (int i = 0; i + 5 < boxes.length; i += 6)
            entry.addBox(new Vector3d(boxes[i], boxes[i + 1], boxes[i + 2]), new Vector3d(boxes[i + 3], boxes[i + 4], boxes[i + 5]));
        this.mic$tileColliders.put(key, entry);
        return entry;
    }
}

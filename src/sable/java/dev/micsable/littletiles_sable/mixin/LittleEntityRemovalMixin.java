package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.micsable.littletiles_sable.SableAnimationSync;

import team.creative.littletiles.common.entity.LittleEntity;

/**
 * Fix (ghost animation entities on Sable vehicles). Plot animation entities are synced to clients by
 * {@code SableAnimationSync}, not vanilla tracking, so vanilla never sends their removal either. The periodic
 * paired-players sweep could race the garbage collector (the pairing map is weak-keyed): a server entity removed
 * and collected between sweeps left its client copy alive forever - a visible ghost door the server no longer
 * knows, yielding "entity not found" when clicked. Despawn paired clients the moment the entity leaves the level.
 */
@Mixin(LittleEntity.class)
public class LittleEntityRemovalMixin {

    @Inject(method = "onRemovedFromLevel", at = @At("TAIL"), remap = false)
    private void mic$despawnOnPairedClients(final CallbackInfo ci) {
        final LittleEntity self = (LittleEntity) (Object) this;
        if (!self.level().isClientSide)
            SableAnimationSync.onEntityRemoved(self);
    }
}

package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.micsable.littletiles_sable.SableAnimationSync;

import team.creative.creativecore.common.network.CreativeNetwork;
import team.creative.creativecore.common.network.CreativePacket;

/**
 * Fix (LittleTiles packets never reaching players near a Sable vehicle) - CreativeCore broadcasts through vanilla
 * "players tracking this entity/chunk", which is always empty for Sable plot coordinates. Every structure and
 * animation packet (block&harr;entity transitions, animation starts, block changes, structure updates) was silently
 * dropped for anything on a vehicle. Reroute those sends to the players Sable reports as tracking the sub-level;
 * entity sends also pair the client first ({@link SableAnimationSync}) so a packet never beats its entity.
 */
@Mixin(CreativeNetwork.class)
public class CreativeNetworkMixin {

    @Inject(method = "sendToClientTracking(Lteam/creative/creativecore/common/network/CreativePacket;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$plotEntityTracking(final CreativePacket message, final Entity entity, final CallbackInfo ci) {
        if (SableAnimationSync.redirectEntityTracking((CreativeNetwork) (Object) this, message, entity))
            ci.cancel();
    }

    @Inject(method = "sendToClientTrackingAndSelf(Lteam/creative/creativecore/common/network/CreativePacket;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$plotEntityTrackingAndSelf(final CreativePacket message, final Entity entity, final CallbackInfo ci) {
        if (SableAnimationSync.redirectEntityTracking((CreativeNetwork) (Object) this, message, entity))
            ci.cancel();
    }

    @Inject(method = "sendToClient(Lteam/creative/creativecore/common/network/CreativePacket;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$plotChunkTracking(final CreativePacket message, final LevelChunk chunk, final CallbackInfo ci) {
        if (SableAnimationSync.redirectChunkTracking((CreativeNetwork) (Object) this, message, chunk))
            ci.cancel();
    }
}

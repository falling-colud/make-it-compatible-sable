package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;

/**
 * Stamps the player's real dimension onto Sable's hand-built teleport packets so Immersive Portals routes them
 * to the right client level (ported from the upstream bridge).
 *
 * <p>When a player rides a sub-level, Sable's {@code ServerPlayerMixin#sable$adjustTeleportPacket} intercepts
 * {@code ServerGamePacketListenerImpl#teleport} and sends its own {@code ClientboundPlayerPositionPacket}
 * (rewritten into plot coordinates). Immersive Portals extends that packet with a dimension tag
 * ({@link IEPlayerPositionLookS2CPacket}) because its client can hold several loaded levels at once and must know
 * which one a position update belongs to; packets sent through the normal vanilla path get the tag stamped by
 * Immersive Portals' own hook, but Sable's bespoke send bypasses it - leaving the tag at its default, so the
 * client may apply the teleport against the wrong level (mis-teleports/desyncs when portals are around).
 * Wrapping the send inside Sable's handler stamps the correct dimension first.</p>
 */
@Mixin(value = ServerPlayer.class, priority = 2000)
public abstract class ServerPlayerTeleportPacketMixin {

    @Shadow
    public abstract ServerLevel serverLevel();

    @TargetHandler(
        mixin = "dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding.ServerPlayerMixin",
        name = "sable$adjustTeleportPacket")
    @WrapOperation(
        method = "@MixinSquared:Handler",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mic$stampDimensionBeforeSend(final ServerGamePacketListenerImpl connection, final Packet<?> packet,
                                              final Operation<Void> original) {
        ((IEPlayerPositionLookS2CPacket) packet).ip_setPlayerDimension(this.serverLevel().dimension());
        original.call(connection, packet);
    }
}

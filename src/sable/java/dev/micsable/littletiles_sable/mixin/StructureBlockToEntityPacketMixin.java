package dev.micsable.littletiles_sable.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;

import dev.micsable.littletiles_sable.SableAnimationSync;

import team.creative.littletiles.client.LittleTilesClient;
import team.creative.littletiles.common.entity.LittleEntity;
import team.creative.littletiles.common.structure.LittleStructure;

/**
 * Fix (clicking a door on a Sable vehicle makes it disappear). When a structure animates, LittleTiles transfers the
 * door's already-built chunk mesh straight into the animation entity ({@code StructureBlockToEntityPacket}) so it
 * appears seamlessly. The transfer pulls the source geometry from {@code RenderingLevelHandler.getRenderChunk(plot
 * level, section)} - but a Sable plot is rendered by Sable's own dispatcher, not LittleTiles' render-chunk map, so
 * that returns {@code null} and the packet dies with an NPE ({@code RenderChunkExtender.backToRAM()} on null). The
 * door blocks were already removed from the plot and the animation's render was never set up, so the door vanished.
 *
 * <p>The whole packet is only an optimization: skipping it for plot animations makes the animation build its mesh
 * from its own blocks (the init packet's {@code loadBlocks} already marked its render manager dirty), which is the
 * correct - if very slightly less efficient - path. Non-Sable animations keep the fast transfer.</p>
 */
@Mixin(targets = "team.creative.littletiles.common.packet.structure.StructureBlockToEntityPacket")
public class StructureBlockToEntityPacketMixin {

    @Shadow
    public UUID uuid;

    @Inject(method = "execute(Lnet/minecraft/world/entity/player/Player;Lteam/creative/littletiles/common/structure/LittleStructure;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mic$skipBufferTransferOnPlot(final Player player, final LittleStructure structure, final CallbackInfo ci) {
        final LittleEntity animation = LittleTilesClient.ANIMATION_HANDLER.find(this.uuid);
        if (animation != null && SableAnimationSync.subLevelOf(animation) != null)
            ci.cancel();
    }
}

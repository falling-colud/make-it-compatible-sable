package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;

import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;

/**
 * Uses the <em>active</em> renderer's buffers for Sable's sub-level block entities under Immersive Portals
 * (ported from the upstream bridge).
 *
 * <p>Sable's {@link VanillaSubLevelBlockEntityRenderer} captures Minecraft's global {@link RenderBuffers} once at
 * construction. Immersive Portals renders each portal view with a separate {@code LevelRenderer} (each owning its
 * own buffers) and swaps the active renderer for the duration of the pass - so when a vehicle is visible inside a
 * portal, Sable's block entities draw into the <em>outer</em> world's buffer source mid-pass. Two passes writing
 * one {@code BufferSource} interleaves/loses geometry and can crash on batch state. Re-reading the buffer source
 * from the currently active {@code Minecraft.levelRenderer} keeps every pass in its own buffers.</p>
 */
@Mixin(value = VanillaSubLevelBlockEntityRenderer.class, remap = false)
public abstract class VanillaSubLevelBlockEntityRendererMixin {

    @WrapOperation(
        method = {"renderSingleBE", "lambda$renderSingleBE$0"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;",
            remap = true))
    private MultiBufferSource.BufferSource mic$useActiveRendererBuffers(final RenderBuffers instance,
                                                                        final Operation<MultiBufferSource.BufferSource> original) {
        final var levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer == null)
            return original.call(instance);
        return ((LevelRendererAccessor) levelRenderer).mic$getRenderBuffers().bufferSource();
    }
}

package dev.micsable.sable_iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;

import dev.micsable.sable_iris.IrisShaders;

import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;

/**
 * Primes the render-thread shaderpack sample once per frame, right before Sable compiles its sub-level sections.
 *
 * <p>Sable compiles a sub-level's dirty sections from {@code compileSections}, which runs on the render thread every
 * frame, and schedules the heavy section builds on async worker threads. Those worker builds ask
 * {@link IrisShaders#packInUse()} how to bake their shading - but Iris' "is a pack in use" query reads render-thread
 * pipeline state and is unreliable off-thread (see {@link IrisShaders}). By calling {@link IrisShaders#packInUse()}
 * here at the <em>head</em> of the compile pass - on the render thread, where the query is valid - we refresh the cache
 * just before this frame's async builds read it, so every build bakes against the same, correct shaderpack state and
 * the vehicle's lighting stops flickering as it is edited.</p>
 */
@Mixin(VanillaChunkedSubLevelRenderData.class)
public class SubLevelShadeStateMixin {

    @Inject(method = "compileSections", at = @At("HEAD"))
    private void mic$primeShaderState(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache cache,
            final Camera camera, final CallbackInfo ci) {
        // Render-thread call -> refreshes IrisShaders' cached pack state for this frame's async section builds.
        IrisShaders.packInUse();
    }
}

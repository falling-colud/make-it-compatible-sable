package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.SectionPos;

import dev.micsable.littletiles_sable.client.SableTiles;

import team.creative.littletiles.client.render.cache.build.RenderingBlockContext;
import team.creative.littletiles.client.render.cache.build.RenderingLevelHandler;
import team.creative.littletiles.client.render.cache.pipeline.LittleRenderPipelineType;
import team.creative.littletiles.client.render.mc.RenderChunkExtender;
import team.creative.littletiles.common.block.entity.BETiles;

/**
 * Fix (door still invisible after closing on a Sable plot - the stale-handler context). LittleTiles picks a
 * {@link RenderingLevelHandler} once, when a tile block is <em>queued</em> ({@code RenderingBlockQueue.queue}, where
 * {@code RenderingBlockQueueMixin} routes plot blocks to the Sable bridge), and stores it {@code final} in the
 * {@link RenderingBlockContext}. But when a door closes, the returning tile blocks are queued the moment their block
 * packets arrive - typically <em>before</em> the plot-bounds packet swaps the sub-level's render data back to the
 * chunked type - so the bridge check fails and the context is created with the Sodium world handler. The context then
 * outlives the swap: {@code BERenderManager.queue} is a no-op while {@code queued} is already true (a re-queue only
 * bumps {@code requestedIndex}), and {@code RenderingThread} re-runs the <em>same</em> context object on requeue. The
 * tiles bake through the Sodium pipeline (compact vertex format), the splice into Sable's section trips
 * {@code BufferHolderMixin}'s stride guard (the recurring "cache baked at 24 bytes/vertex but the section builder
 * writes 32" log), the cache is rejected - and the structure renders as outline only.
 *
 * <p>Late-bind instead: the three handler decisions that differ between the Sodium world handler and
 * {@link SableTiles#SABLE_HANDLER} - pipeline (vertex format), upload/notify target section, and section index - are
 * re-resolved when they are <em>used</em>, so a context built after the plot became bridgeable routes through the
 * bridge no matter when it was queued. Queue accounting ({@code queuedSection}) and model offsets are identical
 * between the two handlers and stay on the stored one. Non-plot contexts resolve to their stored handler and are
 * untouched; a plot that stopped being bridgeable mid-flight keeps its stored handler, whose own per-call fallbacks
 * (and the stride guard) already handle that direction.</p>
 */
@Mixin(value = RenderingBlockContext.class, remap = false)
public abstract class RenderingBlockContextMixin {

    @Shadow @Final public BETiles be;
    @Shadow @Final private RenderingLevelHandler handler;
    @Shadow @Final private long pos;

    @Unique
    private RenderingLevelHandler mic$effectiveHandler() {
        try {
            if (SableTiles.isBridgeable(this.be.getLevel(), SectionPos.asLong(this.be.getBlockPos())))
                return SableTiles.SABLE_HANDLER;
        } catch (final Throwable ignored) {
            // resolution races the main thread (this runs on LT's build threads); fall back to the stored handler
        }
        return this.handler;
    }

    @Inject(method = "getPipeline", at = @At("HEAD"), cancellable = true)
    private void mic$lateBoundPipeline(final CallbackInfoReturnable<LittleRenderPipelineType> cir) {
        final RenderingLevelHandler effective = this.mic$effectiveHandler();
        if (effective != this.handler)
            cir.setReturnValue(effective.getPipeline());
    }

    @Inject(method = "getRenderChunk", at = @At("HEAD"), cancellable = true)
    private void mic$lateBoundRenderChunk(final CallbackInfoReturnable<RenderChunkExtender> cir) {
        final RenderingLevelHandler effective = this.mic$effectiveHandler();
        if (effective != this.handler)
            cir.setReturnValue(effective.getRenderChunk(this.be.getLevel(), this.pos));
    }

    @Inject(method = "sectionIndex", at = @At("HEAD"), cancellable = true)
    private void mic$lateBoundSectionIndex(final CallbackInfoReturnable<Integer> cir) {
        final RenderingLevelHandler effective = this.mic$effectiveHandler();
        if (effective != this.handler)
            cir.setReturnValue(effective.sectionIndex(this.be.getLevel(), this.pos));
    }
}

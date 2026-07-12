package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.micsable.littletiles_sable.client.IrisQuadTracker;
import dev.micsable.littletiles_sable.client.SableTiles;

import team.creative.creativecore.client.render.VertexFormatUtils;
import team.creative.littletiles.client.render.cache.buffer.BufferHolder;
import team.creative.littletiles.client.render.cache.buffer.ChunkBufferUploader;

/**
 * Repairs LittleTiles' cached-buffer splicing under an Iris shaderpack (the vanilla/FORGE pipeline Sable sub-levels
 * render through). Three independent defects, one target class:
 *
 * <ol>
 * <li><b>Iris quad-tracker desync (the black faces).</b> Every {@code upload(ChunkBufferUploader)} into a vanilla
 * {@link BufferBuilder} is a raw memcpy that leaves the builder's {@code vertexPointer} pointing at the start of the
 * copied region. Iris's extended-vertex mixin records that stale pointer as the corner of the next quad, knocking its
 * per-quad tangent/mid-texture/<b>normal</b> filler one vertex out of alignment for the rest of the section - which
 * blacks out faces (wrong normals) on everything compiled after the first tile splice. After each splice we arm
 * Iris's own one-shot suppression flag so the stale pointer is never recorded (see {@link IrisQuadTracker}).</li>
 *
 * <li><b>Stale-stride caches.</b> A tile cache baked while the shaderpack state differed (pack toggled mid-session)
 * has a different vertex stride (32 vs 52 bytes) than the section builder it is spliced into - memcpying it would
 * shear every vertex boundary in the section. We detect the mismatch, {@code invalidate()} the holder instead of
 * splicing it, and let LittleTiles' own {@code hasInvalidBuffers()} machinery queue the block entity for a clean
 * re-bake at the current stride.</li>
 *
 * <li><b>{@code applyOffset} stride walk.</b> When an animated structure (door) lands, LittleTiles shifts the cached
 * positions by walking the buffer at {@code VertexFormatUtils.blockFormatSize()} - a constant 32 bytes. Under Iris the
 * data is 52 bytes wide, so the walk writes "position offsets" into colors, normals and tangents of most vertices.
 * The holder itself knows its real stride ({@code length / vertexCount}); we make the walk use that.</li>
 * </ol>
 */
@Mixin(value = BufferHolder.class, remap = false)
public abstract class BufferHolderMixin {

    @Shadow private int length;
    @Shadow private int vertexCount;

    @Shadow public abstract void invalidate();

    /**
     * Fix #2 - refuse to splice a cache whose vertex stride does not match the destination builder's. Invalidating
     * the holder makes {@code BlockBufferCache.hasInvalidBuffers()} true, which LittleTiles' {@code BERenderManager}
     * treats as "queue this block entity for a rebuild" - so the tiles re-bake at the correct stride within a frame
     * or two instead of splicing garbage into the section.
     */
    @Inject(
        method = "upload(Lteam/creative/littletiles/client/render/cache/buffer/ChunkBufferUploader;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void mic$rejectStaleStride(final ChunkBufferUploader uploader, final CallbackInfoReturnable<Boolean> cir) {
        if (!(uploader instanceof BufferBuilder builder) || this.vertexCount <= 0 || this.length <= 0)
            return;
        final VertexFormat format = ((BufferBuilderFormatAccessor) (Object) builder).mic$format();
        if (format != null && this.length != this.vertexCount * format.getVertexSize()) {
            SableTiles.diag("staleStride:" + this.length / this.vertexCount + "->" + format.getVertexSize(),
                    "[Iris splice fix] tile cache baked at " + this.length / this.vertexCount
                            + " bytes/vertex but the section builder writes " + format.getVertexSize()
                            + " (shaderpack state changed) -> cache invalidated for a clean re-bake instead of splicing garbage.");
            this.invalidate();
            cir.setReturnValue(false);
        }
    }

    /**
     * Fix #1 - immediately after the raw copy lands in the builder, arm Iris's one-shot corner-record suppression so
     * its quad tracker stays aligned. Must run after (not before) the copy: the copy's own leading
     * {@code endLastVertex()} still has to record the last real vertex of the quad built before the splice.
     */
    @Inject(
        method = "upload(Lteam/creative/littletiles/client/render/cache/buffer/ChunkBufferUploader;)Z",
        at = @At(value = "INVOKE",
            target = "Lteam/creative/littletiles/client/render/cache/buffer/ChunkBufferUploader;upload(Ljava/nio/ByteBuffer;)V",
            shift = At.Shift.AFTER))
    private void mic$resyncIrisQuadTracker(final ChunkBufferUploader uploader, final CallbackInfoReturnable<Boolean> cir) {
        IrisQuadTracker.spliceHappened(uploader);
    }

    /**
     * Fix #3 - walk {@code applyOffset} at the holder's <em>actual</em> vertex stride instead of the constant
     * un-extended 32-byte BLOCK size. The position element sits at offset 0 in both the BLOCK and the Iris TERRAIN
     * layout, so correcting the stride alone makes the walk hit every real position and nothing else.
     */
    @Redirect(
        method = "applyOffset",
        at = @At(value = "INVOKE",
            target = "Lteam/creative/creativecore/client/render/VertexFormatUtils;blockFormatSize()I"))
    private int mic$walkAtActualStride() {
        if (this.vertexCount > 0 && this.length % this.vertexCount == 0)
            return this.length / this.vertexCount;
        return VertexFormatUtils.blockFormatSize();
    }
}

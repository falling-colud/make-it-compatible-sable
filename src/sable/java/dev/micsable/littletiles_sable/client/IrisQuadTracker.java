package dev.micsable.littletiles_sable.client;

import java.lang.reflect.Field;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.micsable.littletiles_sable.mixin.BufferBuilderFormatAccessor;

/**
 * Keeps Iris's per-quad "extended vertex data" tracker in sync across LittleTiles' raw buffer splices.
 *
 * <p><b>The problem.</b> While a shaderpack is active, Iris extends every terrain {@link BufferBuilder} to its
 * 52-byte {@code TERRAIN} format and fills the extra attributes ({@code at_midTexCoord}, {@code at_tangent}, and -
 * during level rendering - the vertex <em>normal</em>) per completed quad. It tracks quad corners inside
 * {@code endLastVertex}: each call records the builder's current {@code vertexPointer} (the vertex being completed),
 * and every 4th record triggers the fill. LittleTiles splices its cached tile geometry into the same builder with a
 * raw {@code memcpy} ({@code ChunkBufferUploader.upload(ByteBuffer)}), which bumps the vertex count by hundreds and
 * leaves {@code vertexPointer} at the <em>start</em> of the copied region. The next real {@code addVertex} then makes
 * Iris record that stale pointer as a quad corner, and from that point on the tracker is permanently one vertex out of
 * step: every subsequent quad in the section gets its tangent/mid-texture (and, on sync builds, its <b>normal</b>)
 * computed from a window straddling two different quads and written one vertex off. Corrupted normals under a pack are
 * exactly the "whole faces black per direction" and shadow-acne symptoms on Sable vehicles carrying tiles.</p>
 *
 * <p><b>The fix.</b> Iris already solved this exact problem for Sodium's raw vertex pushes: its
 * {@code MixinBufferBuilder} keeps a {@code skipEndVertexOnce} flag that suppresses exactly one corner record. We set
 * that flag (reflectively - it is a mixin-added private field) right after every LittleTiles splice, so the stale
 * pointer left by the memcpy is never recorded and the tracker stays quad-aligned. The spliced bytes themselves need
 * no fill - they were baked through an Iris-extended builder and already carry complete extended data.</p>
 *
 * <p>Without Iris the field simply does not exist and this class is a permanent no-op.</p>
 */
public final class IrisQuadTracker {

    private IrisQuadTracker() {}

    /** Iris's {@code MixinBufferBuilder.skipEndVertexOnce}, or {@code null} when Iris is absent (permanent no-op). */
    private static final Field SKIP_END_VERTEX_ONCE = resolveSkipField();

    private static Field resolveSkipField() {
        Field exact = null;
        try {
            for (final Field field : BufferBuilder.class.getDeclaredFields())
                // The @Unique field keeps its name unless another mixin claims it, in which case Mixin decorates it -
                // match by substring so both spellings are found.
                if (field.getType() == boolean.class && field.getName().contains("skipEndVertexOnce")) {
                    field.setAccessible(true);
                    exact = field;
                    break;
                }
        } catch (final Throwable ignored) {
            // Reflection refused -> leave null, splices behave as before this patch.
        }
        return exact;
    }

    /**
     * Notifies the tracker that {@code uploader} just received a raw vertex-block copy. Must be called <em>after</em>
     * the copy: the flag suppresses the <em>next</em> corner record, and the copy's own leading
     * {@code endLastVertex()} still has to record the last legitimately-built vertex before it.
     */
    public static void spliceHappened(final Object uploader) {
        if (SKIP_END_VERTEX_ONCE == null || !(uploader instanceof BufferBuilder builder))
            return;
        // Only extended builders track quads; an un-extended BLOCK builder means no pack (or another format entirely).
        final VertexFormat format = ((BufferBuilderFormatAccessor) (Object) builder).mic$format();
        if (format == null || format == DefaultVertexFormat.BLOCK)
            return;
        try {
            SKIP_END_VERTEX_ONCE.setBoolean(builder, true);
            SableTiles.diag("irisResync", "[Iris splice fix] raw tile splice into an Iris-extended builder detected -> "
                    + "arming Iris's skipEndVertexOnce so its per-quad tangent/normal filler stays aligned.");
        } catch (final Throwable ignored) {
            // Field became unwritable (Iris internals changed) - do nothing rather than break the build thread.
        }
    }

    /** @return whether the Iris quad-tracker hook is live (Iris present and its field resolved). */
    public static boolean active() {
        return SKIP_END_VERTEX_ONCE != null;
    }
}

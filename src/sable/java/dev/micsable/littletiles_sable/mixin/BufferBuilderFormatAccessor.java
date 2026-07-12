package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Exposes the {@code format} field of a vanilla {@link BufferBuilder}.
 *
 * <p>The field is declared {@code private final} and normally equals whatever format the builder was constructed
 * with. Under an active Iris shaderpack, however, Iris's own {@code MixinBufferBuilder} rewrites that field at
 * construction to its extended {@code TERRAIN} format (32&nbsp;&rarr;&nbsp;52 bytes), so reading it back is the only
 * reliable way to know the <em>real</em> stride of the vertices a builder produces. {@link LittleRenderPipelineForgeMixin}
 * uses this to correct LittleTiles' FORGE index math, which otherwise assumes the un-extended 32-byte {@code BLOCK}
 * stride and mis-slices every tile structure on a Sable sub-level.</p>
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderFormatAccessor {

    @Accessor("format")
    VertexFormat mic$format();
}

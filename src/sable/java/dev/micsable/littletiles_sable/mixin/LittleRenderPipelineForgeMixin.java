package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;

import dev.micsable.littletiles_sable.client.FlatShadeGetter;
import dev.micsable.littletiles_sable.client.SableTiles;

import team.creative.littletiles.client.render.cache.pipeline.LittleRenderPipelineForge;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;

/**
 * Fix #5 - corrects the over-dark lighting of LittleTiles tiles on Sable sub-levels.
 *
 * <p>When the tile-block being baked sits in a Sable plot, we wrap the level handed to NeoForge's
 * {@code QuadLighter} so it reports flat directional shade ({@link FlatShadeGetter}). That stops LittleTiles
 * from baking static face-shading into the tiles, leaving Sable's chunk shader to apply directional shading
 * dynamically (rotating with the vehicle) - exactly as it already does for vanilla blocks.</p>
 */
@Mixin(LittleRenderPipelineForge.class)
public class LittleRenderPipelineForgeMixin {

    @Redirect(
        method = "buildCache",
        at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/model/lighting/QuadLighter;setup(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void mic$flatShadeOnSubLevel(final QuadLighter lighter, BlockAndTintGetter level, final BlockPos pos, final BlockState state) {
        // Only flat-bake when Sable is actually applying directional shade dynamically. Under an Iris shaderpack the
        // Sable x Iris patch reports this disabled (its Veil shading doesn't reach Iris shaders), so we let tiles bake
        // their normal directional shade just like the vehicle's vanilla blocks - keeping the two consistent.
        if (SableDynamicDirectionalShading.isEnabled() && level instanceof Level lvl
                && SableTiles.subLevelAt(lvl, SectionPos.asLong(pos)) != null)
            level = new FlatShadeGetter(level);
        lighter.setup(level, pos, state);
    }

    /**
     * THE FIX. LittleTiles' FORGE pipeline records each tile-structure's byte offset within a layer as
     * {@code vertexCount * format.getVertexSize()}, where {@code format} is the {@code DefaultVertexFormat.BLOCK}
     * (32-byte) value {@code RenderingThread} hands {@code buildCache}. But under an active Iris shaderpack, Iris
     * silently rewrites the actual {@link BufferBuilder} to its extended {@code TERRAIN} format (52 bytes), so the
     * vertices really are 52 bytes wide. The 32-vs-52 mismatch makes every structure offset wrong, so when the tiles
     * are spliced into Sable's section buffer the geometry is mis-sliced - which corrupts the section's normals and
     * blacks out whole faces (per-direction) on the vehicle, only under shaders.
     *
     * <p>We replace the assumed {@code BLOCK} stride with the builder's <em>real</em> vertex size (read from its
     * {@code format} field via {@link BufferBuilderFormatAccessor}). Without shaders that field is still {@code BLOCK},
     * so this is a no-op there; under shaders it returns 52 and the offsets line up. The {@code builder} is captured as
     * a {@link Local} - it is the single {@link BufferBuilder} in scope at this point in {@code buildCache}.</p>
     */
    @ModifyExpressionValue(
        method = "buildCache",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexFormat;getVertexSize()I"))
    private int mic$useActualBuilderStride(final int assumedStride, @Local final BufferBuilder builder) {
        final VertexFormat real = ((BufferBuilderFormatAccessor) (Object) builder).mic$format();
        if (real == null || real.getVertexSize() == assumedStride)
            return assumedStride;
        SableTiles.diag("strideFix:" + assumedStride + "->" + real.getVertexSize(),
                "[stride fix] FORGE index math corrected " + assumedStride + " -> " + real.getVertexSize()
                        + " bytes/vertex (Iris-extended TERRAIN) so tile structures splice into Sable sections aligned.");
        return real.getVertexSize();
    }
}

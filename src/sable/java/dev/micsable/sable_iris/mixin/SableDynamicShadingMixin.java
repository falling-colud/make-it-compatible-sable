package dev.micsable.sable_iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.micsable.sable_iris.IrisShaders;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;

/**
 * Fix: Sable sub-level faces render solid black under an Iris shaderpack.
 *
 * <p>Sable's "dynamic directional shading" bakes sub-level blocks <em>flat</em> (it forces {@code getShade == 1.0})
 * and re-creates directional shading at draw time through <b>Veil shader injections</b>
 * ({@code SableEnableNormalLighting}, the {@code block_brightness(Normal)} term, and the {@code SableSkyLightScale}
 * sub-level skylight compensation). Under an Iris shaderpack, Iris owns the terrain shaders, so none of those Veil
 * injections exist - the flat-baked faces keep no lighting and the pack renders whole faces black across the whole
 * vehicle (vanilla blocks and bridged LittleTiles alike).</p>
 *
 * <p>While a shaderpack is active we report dynamic shading as <em>disabled</em>, so Sable falls back to baking each
 * block's ordinary directional shade into the vertex colour - exactly what every other block in the world does, and
 * exactly what an Iris pack expects to read. Without a shaderpack this returns Sable's real setting untouched, so the
 * dynamic rotate-with-the-vehicle effect is kept for the no-shaders case. Toggling shaders triggers Iris' own chunk
 * rebuild, so sections are re-baked under the correct mode automatically.</p>
 *
 * <p>The trade-off under shaders is that shading no longer rotates with the vehicle (it is baked per build), which is
 * the same limitation any static-baked geometry has - and vastly preferable to fully black faces.</p>
 */
@Mixin(value = SableDynamicDirectionalShading.class, remap = false)
public class SableDynamicShadingMixin {

    @ModifyReturnValue(method = "isEnabled", at = @At("RETURN"))
    private static boolean mic$disableUnderShaders(final boolean original) {
        return original && !IrisShaders.packInUse();
    }
}

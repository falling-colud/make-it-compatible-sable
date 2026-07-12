package dev.micsable.littletiles_sable.mixin;

import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import dev.micsable.littletiles_sable.client.SableEntityLight;

/**
 * Fix (render-thread FREEZE the instant an LT-only structure is right-clicked to animate). Replaces Sable's
 * entity sky-light helper with a bounded implementation - see {@link SableEntityLight} for the full analysis.
 *
 * <p>The target is not a vanilla method: it is {@code sable$getSubLevelAccountedSkyLight}, a {@code @Unique}
 * <em>added to {@code EntityRenderer} by Sable's own {@code entity_rendering.EntityRendererMixin}</em> (thread
 * dumps confirm the merged name). Injecting into another mod's merged mixin method works as long as we apply
 * <b>after</b> Sable does - hence {@code priority = 2000} (Sable uses the default 1000) - and the method name is
 * stable because Sable declares it with its own {@code sable$} prefix (no conflict, no rename). {@code require = 0}:
 * if a future Sable version renames or removes the helper, this injection silently no-ops (the freeze guard is
 * lost but the game still boots) instead of failing the whole config.</p>
 */
@Mixin(value = EntityRenderer.class, priority = 2000)
public abstract class EntityRendererSableLightMixin {

    @Inject(method = "sable$getSubLevelAccountedSkyLight", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void mic$boundedSubLevelSkyLight(final int original, final Level level, final LightLayer lightLayer,
            final BlockPos blockPos, final Vector3dc probePosition, final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SableEntityLight.subLevelAccountedSkyLight(original, level, lightLayer, blockPos, probePosition));
    }
}

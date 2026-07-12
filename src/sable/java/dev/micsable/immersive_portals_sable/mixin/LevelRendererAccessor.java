package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;

/** Exposes {@link LevelRenderer}'s render buffers, so the block-entity fix can use the active renderer's buffers. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("renderBuffers")
    RenderBuffers mic$getRenderBuffers();
}

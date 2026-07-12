package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import team.creative.littletiles.client.render.overlay.PreviewManager;

/**
 * Makes the LittleTiles selection outline (and tool highlight overlays) appear on Sable vehicles.
 *
 * <p>When the crosshair targets a block on a Sable sub-level, Sable wraps NeoForge's {@code onDrawHighlight}: it
 * pushes the vehicle's full render pose onto the event's {@code PoseStack} and hands the event a
 * {@code SubLevelCamera} whose {@code getPosition()} is the real camera <em>transformed into the plot's coordinate
 * space</em>. Anything drawn at {@code blockPos - eventCameraPos} under that pose lands exactly on the visible,
 * rotating vehicle - that is how the vanilla block outline already works on vehicles.</p>
 *
 * <p>LittleTiles' {@code PreviewManager.drawHighlight} runs inside that same event but ignores the event camera: it
 * offsets its tile outline (and the active tool's highlight rendering) by {@code blockPos -
 * gameRenderer.getMainCamera().getPosition()} - the <b>world-space</b> camera. On a vehicle the block position is in
 * plot space (coordinates in the millions), so the outline was drawn millions of blocks off-screen; this compat
 * previously just suppressed it. Redirecting the single {@code Camera.getPosition()} call to the <em>event's</em>
 * camera fixes both the outline offset and the tool-highlight offset in one stroke: off a vehicle the event camera
 * <b>is</b> the main camera (identical value, unchanged behaviour), on a vehicle it is the plot-space camera the
 * pushed pose expects - so tiles get their proper per-shape wireframe on vehicles, chunked and single-block plots
 * alike.</p>
 */
@Mixin(PreviewManager.class)
public class PreviewManagerOutlineMixin {

    @Redirect(
        method = "drawHighlight",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 mic$useHighlightEventCamera(final Camera mainCamera, final RenderHighlightEvent.Block event) {
        // Sable substitutes a plot-space SubLevelCamera when the target rides a vehicle; otherwise this IS the
        // main camera and the value is identical to the original call.
        return event.getCamera().getPosition();
    }
}

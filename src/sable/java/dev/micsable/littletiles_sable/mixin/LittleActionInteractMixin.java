package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.SableInteract;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;

import team.creative.littletiles.common.action.LittleActionInteract;
import team.creative.littletiles.common.action.source.LittleActionSource;

/**
 * Fix #4 (actions) - makes break / place / use on a tile riding a Sable vehicle actually hit the tile instead of
 * reporting "tile not found".
 *
 * <p>{@code LittleActionInteract.action(source)} runs on <b>both</b> sides (the client predicts, the server
 * validates) and selects the sub-tile by raycasting {@code transformedPos -> transformedLook} against the tile's
 * boxes. It already projects that ray into LittleTiles' own animation levels (the {@code uuid} branch), but a
 * Sable plot's tile-block has no such uuid, so the ray stays in world space and misses the boxes at the vehicle's
 * plot coordinates - the server then throws {@code onTileNotFound()}.</p>
 *
 * <p>We transform the action's ray into the sub-level once, after the animation branch and before it is consumed
 * by both {@code getFocusedTile} and {@code rayTrace}. Because the action executes server-side too, this must be a
 * common mixin (it uses the sub-level's logical pose, which exists on both sides).</p>
 */
@Mixin(LittleActionInteract.class)
public abstract class LittleActionInteractMixin {

    @Shadow public BlockPos blockPos;
    @Shadow public Vec3 transformedPos;
    @Shadow public Vec3 transformedLook;
    @Shadow public boolean transformedCoordinates;

    @Inject(
        method = "action(Lteam/creative/littletiles/common/action/source/LittleActionSource;)Ljava/lang/Object;",
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private void mic$transformActionRayForSubLevel(final LittleActionSource source, final CallbackInfoReturnable<Object> cir) {
        if (this.transformedCoordinates || this.blockPos == null || this.transformedPos == null || this.transformedLook == null)
            return; // already handled (e.g. a LittleTiles animation), or nothing to transform
        final Level level = source.getActionLevel();
        final SubLevel sub = Sable.HELPER.getContaining(level, this.blockPos.getX() >> 4, this.blockPos.getZ() >> 4);
        if (sub != null) {
            this.transformedPos = SableInteract.intoSubLevel(sub, this.transformedPos);
            this.transformedLook = SableInteract.intoSubLevel(sub, this.transformedLook);
            this.transformedCoordinates = true;
        }
    }
}

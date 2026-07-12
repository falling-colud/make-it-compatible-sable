package dev.micsable.littletiles_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.phys.Vec3;

import dev.micsable.littletiles_sable.SableInteract;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;

import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.little.tile.LittleTileContext;

/**
 * Fix #4 - lets you interact with (break / place / use) individual tiles on a Sable vehicle.
 *
 * <p>{@code BETiles.getFocusedTile(player, pt)} raycasts the player's eye/look against the tile's boxes to pick
 * the precise sub-tile. It already projects that ray into LittleTiles' <em>own</em> oriented levels (animations),
 * but a Sable plot's tile-block reports the ordinary {@code ClientLevel}, so that branch is skipped and the ray
 * stays in world space while the tile boxes sit at the vehicle's far-away plot coordinates - they never meet, so
 * nothing is selectable.</p>
 *
 * <p>We redirect the inner {@code getFocusedTile(pos, look)} call: if the tile-block is in a Sable sub-level we
 * project the ray into that sub-level's plot space first (the same inverse transform Sable uses for collision).</p>
 */
@Mixin(BETiles.class)
public class BETilesFocusMixin {

    @Redirect(
        method = "getFocusedTile(Lnet/minecraft/world/entity/player/Player;F)Lteam/creative/littletiles/common/block/little/tile/LittleTileContext;",
        at = @At(value = "INVOKE",
            target = "Lteam/creative/littletiles/common/block/entity/BETiles;getFocusedTile(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lteam/creative/littletiles/common/block/little/tile/LittleTileContext;"))
    private LittleTileContext mic$focusOnSubLevel(final BETiles be, Vec3 pos, Vec3 look) {
        final SubLevel sub = Sable.HELPER.getContaining(be);
        if (sub != null) {
            pos = SableInteract.intoSubLevel(sub, pos);
            look = SableInteract.intoSubLevel(sub, look);
        }
        return be.getFocusedTile(pos, look);
    }
}

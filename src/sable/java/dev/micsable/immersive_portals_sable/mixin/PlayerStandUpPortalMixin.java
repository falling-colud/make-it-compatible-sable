package dev.micsable.immersive_portals_sable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;

/**
 * Re-applies Sable's stand-up collision check inside Immersive Portals' replacement of
 * {@code Player#canPlayerFitWithinBlocksAndEntitiesWhen}.
 *
 * <p>That method decides whether a player may change pose (stand up from crawling/swimming, un-shift under a
 * slab). Sable's {@code player_standup.PlayerMixin} wraps the {@code Level#noCollision} call inside it so the
 * check also collides against sub-level blocks - otherwise players stand up <em>through</em> a vehicle's ceiling.
 * Immersive Portals <em>overwrites</em> the whole method ({@code MixinPlayer_Collision}, to use its
 * portal-clipped collision box), and a wrap cannot inject into a method merged by another mixin - with both mods
 * installed, Sable's injector fails at class load with a hard {@code InvalidInjectionException} <b>crash</b>
 * (this is a stock Sable + Immersive Portals crash, present even without this mod). Our annotation adjuster
 * cancels Sable's wrap, and this mixin re-applies the identical check - {@code noCollision} AND no sub-level
 * geometry in the box (via Sable's own {@link CanFallAtleastHelper}) - to the same call inside Immersive Portals'
 * merged method, portal-clipped box and all.</p>
 */
@Mixin(value = Player.class, priority = 2000)
public abstract class PlayerStandUpPortalMixin {

    @TargetHandler(
        mixin = "qouteall.imm_ptl.core.mixin.common.collision.MixinPlayer_Collision",
        name = "canPlayerFitWithinBlocksAndEntitiesWhen")
    @WrapOperation(
        method = "@MixinSquared:Handler",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean mic$alsoCollideSubLevels(final Level level, final Entity entity, final AABB box,
                                             final Operation<Boolean> original) {
        if (!original.call(level, entity, box))
            return false;
        return CanFallAtleastHelper.canFallAtleastWithSubLevels(level, box) == null;
    }
}

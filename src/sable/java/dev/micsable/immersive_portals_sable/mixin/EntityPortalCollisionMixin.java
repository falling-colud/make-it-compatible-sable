package dev.micsable.immersive_portals_sable.mixin;

import java.util.concurrent.atomic.AtomicInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.micsable.immersive_portals_sable.ImmersivePortalsSablePatch;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;

/**
 * Re-adds Immersive Portals' cross-portal collision as a <em>chainable</em> {@code @WrapOperation} on the
 * {@code Entity#collide} call inside {@code Entity#move}.
 *
 * <p>Immersive Portals ships this logic as a {@code @Redirect}
 * ({@code collision.MixinEntity#redirectHandleCollisions}), which hard-conflicts with Sable's own
 * {@code @Redirect} of the same call ({@code entity_sublevel_collision.EntityMixin#sable$collideRedirect}) - one
 * of the two silently loses. Our annotation adjuster cancels Immersive Portals' redirect, and this wrap restores
 * its behaviour <em>around</em> whatever the call site resolved to - i.e. {@code original.call(...)} now runs
 * Sable's sub-level-aware collision, and the portal collision handling composes on top. The body mirrors
 * Immersive Portals 6.0.7's {@code redirectHandleCollisions} exactly:</p>
 *
 * <ol>
 *   <li>with server-side portal collision disabled ({@code IPGlobal.enableServerCollision=false}), server-side
 *       non-players don't move (players still collide normally);</li>
 *   <li>absurdly fast movement (&gt;60 blocks/tick) skips collision entirely (chunk-loading DoS guard);</li>
 *   <li>run the underlying collision (vanilla + Sable's sub-levels), then let the entity's
 *       {@link PortalCollisionHandler} clip it against portal planes;</li>
 *   <li>oversized results (&gt;20 blocks) are discarded.</li>
 * </ol>
 *
 * <p>Priority 900 matches the upstream bridge (applies the wrap outside any default-priority wraps).</p>
 */
@Mixin(value = Entity.class, priority = 900)
public abstract class EntityPortalCollisionMixin implements IEEntity {

    /** Lifetime cap on the two diagnostic log messages, mirroring Immersive Portals' own CountDownInt(20). */
    @Unique
    private static final AtomicInteger MIC$LOG_BUDGET = new AtomicInteger(20);

    @Unique
    private static boolean mic$tryLog() {
        return MIC$LOG_BUDGET.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }

    @WrapOperation(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 mic$portalAwareCollide(final Entity entity, final Vec3 attemptedMove, final Operation<Vec3> original) {
        final PortalCollisionHandler portalCollisionHandler = ip_getPortalCollisionHandler();

        if (!IPGlobal.enableServerCollision && !entity.level().isClientSide()) {
            if (entity instanceof Player)
                return original.call(entity, attemptedMove);
            return Vec3.ZERO;
        }

        if (attemptedMove.lengthSqr() > 60 * 60) {
            // Avoid loading too many chunks in the collision calculation and lagging the server (IP's guard).
            if (mic$tryLog())
                ImmersivePortalsSablePatch.LOGGER.error(
                    ImmersivePortalsSablePatch.LOG_PREFIX
                        + "skipping collision calculation because entity moves too fast {} {} {}",
                    entity, attemptedMove, entity.level().getGameTime(), new Throwable());
            return Vec3.ZERO;
        }

        Vec3 result = original.call(entity, attemptedMove);

        if (IPGlobal.crossPortalCollision
                && portalCollisionHandler != null
                && portalCollisionHandler.hasCollisionEntry())
            result = portalCollisionHandler.handleCollision((Entity) (Object) this, result);

        if (result.lengthSqr() > 20 * 20) {
            if (mic$tryLog())
                ImmersivePortalsSablePatch.LOGGER.error(
                    ImmersivePortalsSablePatch.LOG_PREFIX + "cross portal collision result too large {} {} {}",
                    this, attemptedMove, result);
            return Vec3.ZERO;
        }

        return result;
    }
}

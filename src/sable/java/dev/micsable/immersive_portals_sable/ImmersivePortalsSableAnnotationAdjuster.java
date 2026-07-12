package dev.micsable.immersive_portals_sable;

import java.util.List;

import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;

/**
 * Defuses the two upstream mixins that make Immersive Portals and Sable collide, via MixinSquared's annotation
 * adjuster (registered by {@link ImmersivePortalsSableMixinPlugin} only when both mods are present):
 *
 * <ol>
 *   <li><b>Immersive Portals' {@code MixinEntity#redirectHandleCollisions}</b> - a {@code @Redirect} of the
 *       {@code Entity#collide} call inside {@code Entity#move}. Sable's
 *       {@code entity_sublevel_collision.EntityMixin#sable$collideRedirect} is a {@code @Redirect} of the <em>same
 *       instruction</em>; two redirects on one call is a hard mixin conflict where one silently loses (no vehicle
 *       collision, or no portal collision, depending on apply order). We cancel Immersive Portals' redirect and
 *       re-add its exact logic as a chainable {@code @WrapOperation}
 *       (see {@code mixin.EntityPortalCollisionMixin}), so portal collision now composes <em>around</em> Sable's
 *       sub-level collision instead of fighting it.</li>
 *   <li><b>Sable's {@code entity_tracking.TrackedEntityMixin}</b> - a {@code @Redirect} of {@code Entity#position()}
 *       inside {@code ChunkMap.TrackedEntity#updatePlayer}, which projects a plot-grid entity's position into world
 *       space so nearby players get its sync packets. Immersive Portals <em>overwrites</em> {@code updatePlayer}
 *       (and {@code updatePlayers}) to no-ops and re-implements tracking in its own
 *       {@code ip_updateEntityTrackingStatus}, so Sable's redirect is left targeting an empty method - dead at
 *       best, an injection error at worst. We cancel it and re-apply the same projection inside Immersive Portals'
 *       replacement (see {@code mixin.TrackedEntityMixin}).</li>
 *   <li><b>Sable's {@code player_standup.PlayerMixin}</b> - a {@code @WrapOperation} of the {@code
 *       Level#noCollision} call inside {@code Player#canPlayerFitWithinBlocksAndEntitiesWhen} (so the stand-up /
 *       change-pose check also collides against sub-level blocks). Immersive Portals <em>overwrites</em> that
 *       method ({@code MixinPlayer_Collision}, portal-clipped collision box), and a wrap cannot inject into a
 *       method merged by another mixin - with both mods installed this is a hard
 *       {@code InvalidInjectionException} <em>crash at class load</em>, even without this patch's other fixes. We
 *       cancel Sable's wrap and re-apply the identical check inside Immersive Portals' replacement (see
 *       {@code mixin.PlayerStandUpPortalMixin}).</li>
 * </ol>
 *
 * <p>Both adjustments follow the upstream ImmersivePortalSableBridge approach. The adjuster is a no-op for every
 * other mixin, and is never registered unless both mods are installed.</p>
 */
public final class ImmersivePortalsSableAnnotationAdjuster implements MixinAnnotationAdjuster {

    private static final String IP_ENTITY_COLLISION_MIXIN = "qouteall.imm_ptl.core.mixin.common.collision.MixinEntity";
    private static final String IP_COLLISION_HANDLER = "redirectHandleCollisions";
    private static final String SABLE_TRACKED_ENTITY_MIXIN = "dev.ryanhcode.sable.mixin.entity.entity_tracking.TrackedEntityMixin";
    private static final String SABLE_PLAYER_STANDUP_MIXIN = "dev.ryanhcode.sable.mixin.player_standup.PlayerMixin";

    /** Registers this adjuster with MixinSquared. Call once, from the patch's mixin plugin {@code onLoad}. */
    public static void register() {
        MixinAnnotationAdjusterRegistrar.register(new ImmersivePortalsSableAnnotationAdjuster());
    }

    @Override
    public AdjustableAnnotationNode adjust(final List<String> targetClassNames, final String mixinClassName,
                                           final MethodNode handlerNode, final AdjustableAnnotationNode annotationNode) {
        // (1) Cancel Immersive Portals' Entity#collide @Redirect (re-added as a wrap by EntityPortalCollisionMixin).
        if (IP_ENTITY_COLLISION_MIXIN.equals(mixinClassName)
                && annotationNode.is(Redirect.class)
                && handlerNode.name.contains(IP_COLLISION_HANDLER))
            return null;

        // (2) Cancel Sable's tracked-entity redirect (dead under Immersive Portals' updatePlayer overwrite;
        //     re-applied inside ip_updateEntityTrackingStatus by TrackedEntityMixin).
        if (SABLE_TRACKED_ENTITY_MIXIN.equals(mixinClassName))
            return null;

        // (3) Cancel Sable's stand-up collision wrap (crashes into Immersive Portals' overwrite of
        //     canPlayerFitWithinBlocksAndEntitiesWhen; re-applied there by PlayerStandUpPortalMixin).
        if (SABLE_PLAYER_STANDUP_MIXIN.equals(mixinClassName))
            return null;

        return annotationNode;
    }
}

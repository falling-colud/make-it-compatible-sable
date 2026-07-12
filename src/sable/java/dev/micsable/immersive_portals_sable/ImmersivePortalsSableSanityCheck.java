package dev.micsable.immersive_portals_sable;

import java.lang.reflect.Method;

import net.minecraft.world.entity.Entity;

/**
 * Post-load verification that the patch's riskiest mechanism - the MixinSquared annotation adjuster - actually
 * took effect, with a clear log line either way.
 *
 * <p>The adjuster cancels Immersive Portals' {@code Entity#collide} {@code @Redirect} so our chainable wrap can
 * replace it (see {@link ImmersivePortalsSableAnnotationAdjuster}). If MixinSquared failed to bootstrap (e.g. the
 * bundled library was stripped from the jar, or a future Immersive Portals renames the handler), the cancel
 * silently does nothing and the original redirect-vs-redirect conflict with Sable comes back - one of the two
 * silently loses. That state is otherwise invisible until someone notices vehicles or portals misbehaving, so we
 * inspect the merged {@link Entity} class once and report:</p>
 *
 * <ul>
 *   <li>annotation cancelled &rarr; the handler merges under its plain name ({@code redirectHandleCollisions});</li>
 *   <li>annotation survived &rarr; mixin merges it as a renamed injector handler
 *       ({@code *$redirectHandleCollisions});</li>
 *   <li>our own wrap merges as a handler containing {@code mic$portalAwareCollide}.</li>
 * </ul>
 *
 * <p>Runs on {@code FMLCommonSetupEvent} (registered only when both mods are present), by which point
 * {@code Entity} is loaded and all its mixins have applied.</p>
 */
public final class ImmersivePortalsSableSanityCheck {

    /** Logs whether the collision seam ended up in the intended shape. Never throws. */
    public static void run() {
        try {
            boolean cancelledRedirectPresent = false;
            boolean liveRedirectHandlerPresent = false;
            boolean ourWrapPresent = false;

            for (final Method method : Entity.class.getDeclaredMethods()) {
                final String name = method.getName();
                if ("redirectHandleCollisions".equals(name))
                    cancelledRedirectPresent = true;
                else if (name.endsWith("$redirectHandleCollisions"))
                    liveRedirectHandlerPresent = true;
                if (name.contains("mic$portalAwareCollide"))
                    ourWrapPresent = true;
            }

            if (liveRedirectHandlerPresent)
                ImmersivePortalsSablePatch.LOGGER.warn(ImmersivePortalsSablePatch.LOG_PREFIX
                    + "Immersive Portals' Entity#collide @Redirect is still armed - the MixinSquared annotation "
                    + "adjuster did not take effect, so the redirect conflict with Sable's vehicle collision is "
                    + "unresolved (one of the two silently loses). Check that MixinSquared loaded (bundled at "
                    + "META-INF/jarjar of the mod jar; in dev, put mixinsquared-neoforge in run/mods).");
            else if (cancelledRedirectPresent && ourWrapPresent)
                ImmersivePortalsSablePatch.LOGGER.info(ImmersivePortalsSablePatch.LOG_PREFIX
                    + "collision seam verified: Immersive Portals' redirect defused, portal-aware wrap in place.");
            else
                ImmersivePortalsSablePatch.LOGGER.warn(ImmersivePortalsSablePatch.LOG_PREFIX
                    + "collision seam looks different than expected (cancelled-redirect present: {}, our wrap "
                    + "present: {}). An Immersive Portals update may have moved/renamed its collision handler - "
                    + "the patch's targets need re-verifying against this Immersive Portals build.",
                    cancelledRedirectPresent, ourWrapPresent);
        } catch (final Throwable t) {
            ImmersivePortalsSablePatch.LOGGER.warn(ImmersivePortalsSablePatch.LOG_PREFIX
                + "could not inspect the collision seam: {}", t.toString());
        }
    }

    private ImmersivePortalsSableSanityCheck() {}
}

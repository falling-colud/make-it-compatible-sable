package dev.micsable.immersive_portals_sable;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Shared constants for the <b>Immersive Portals &times; Sable</b> patch.
 *
 * <p>The patch makes <a href="https://modrinth.com/mod/immersiveportals">Immersive Portals</a> (mod id
 * {@code imm_ptl}, NeoForge build) and Sable coexist. Sable files each sub-level (vehicle) in a far-away
 * "plot grid" region of the same dimension (~&#177;20&nbsp;million blocks out) and maps it into world space with a
 * pose transform. Immersive Portals rewrites many of the same vanilla systems (entity tracking, entity sync,
 * chunk loading, collision, the renderer's view area) under the assumption that an entity's raw coordinates are
 * where it visually is - which breaks both mods in specific, repeatable ways when they meet:</p>
 *
 * <ul>
 *   <li>a hard {@code @Redirect} conflict on {@code Entity#collide} (both mods redirect the same call - one
 *       silently loses, so either portal collision or vehicle collision is gone);</li>
 *   <li>Sable's entity-tracking redirect targets {@code ChunkMap.TrackedEntity#updatePlayer}, which Immersive
 *       Portals overwrites to a no-op (its replacement lives in {@code ip_updateEntityTrackingStatus}), so
 *       entities standing on vehicles stop syncing;</li>
 *   <li>Immersive Portals' chunk-loading bookkeeping either ignores or fights Sable's self-managed plot chunks
 *       (vehicles unload near portals, ticket churn, the rare "stuck on saving" exit hang);</li>
 *   <li>sub-levels seen <em>through</em> a portal (including from another dimension) are not considered
 *       "watched" by Sable's tracking system, so vehicles vanish in portal views;</li>
 *   <li>Immersive Portals' per-dimension renderers break Sable's block-entity rendering and section-dirtying
 *       for plot chunks.</li>
 * </ul>
 *
 * <p>Ported and adapted for Sable 2.0 from
 * <a href="https://github.com/rebbyIf/ImmersivePortalSableBridge">ImmersivePortalSableBridge</a>
 * (rebbyIf &amp; Bunting_chj), with additional fixes; see each mixin's class doc for its specific seam. The two
 * upstream-mixin adjustments (the {@code @Redirect} cancel and the tracked-entity re-target) go through
 * <a href="https://github.com/Bawnorton/MixinSquared">MixinSquared</a>, which this jar bundles.</p>
 *
 * <p>Known upstream limitation that still stands: Sable physics objects cannot travel <em>through</em> a portal
 * (they render and stay loaded behind it, but there is no cross-portal teleport for sub-levels).</p>
 */
public final class ImmersivePortalsSablePatch {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** Log prefix for the patch's runtime messages. */
    public static final String LOG_PREFIX = "[Make it Compatible: Sable / Immersive Portals x Sable] ";

    private ImmersivePortalsSablePatch() {}
}

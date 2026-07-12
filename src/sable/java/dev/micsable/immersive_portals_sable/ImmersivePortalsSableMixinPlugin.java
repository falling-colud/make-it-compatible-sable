package dev.micsable.immersive_portals_sable;

import dev.micsable.core.ModIds;
import dev.micsable.core.RequiredModsMixinPlugin;

/**
 * Gates the Immersive Portals &times; Sable mixins on both {@code sable} and {@code imm_ptl} being present, and -
 * only when they are - bootstraps MixinSquared and registers the patch's annotation adjuster.
 *
 * <p>The adjuster (see {@link ImmersivePortalsSableAnnotationAdjuster}) must be registered from a mixin config
 * plugin's {@code onLoad}, i.e. before any of the adjusted mixins apply. We also call
 * {@code MixinSquaredBootstrap.init()} ourselves (idempotent - it is the library's documented manual entry point)
 * rather than relying on MixinSquared's own {@code MixinConfigs} manifest bootstrap: depending on how the library
 * jar was located (JarJar'd game library in production, classpath entry in dev), that manifest config may never be
 * registered, which would leave the {@code @MixinSquared:Handler} selector unregistered and the adjuster inert.
 * Doing both here keeps the whole mechanism behind the same both-mods-present gate as the mixins: with either mod
 * missing, neither MixinSquared nor any mixin of this patch touches the game, so each mod alone behaves exactly
 * as stock.</p>
 */
public final class ImmersivePortalsSableMixinPlugin extends RequiredModsMixinPlugin {

    private static final String PATCH_NAME = "Immersive Portals x Sable";

    public ImmersivePortalsSableMixinPlugin() {
        super(PATCH_NAME, ModIds.SABLE, ModIds.IMMERSIVE_PORTALS);
    }

    @Override
    public void onLoad(final String mixinPackage) {
        super.onLoad(mixinPackage);
        if (allPresent(ModIds.SABLE, ModIds.IMMERSIVE_PORTALS)) {
            com.bawnorton.mixinsquared.MixinSquaredBootstrap.init();
            ImmersivePortalsSableAnnotationAdjuster.register();
            LOGGER.info("[Make it Compatible] patch '{}' active - bootstrapped MixinSquared and registered the "
                + "mixin annotation adjuster.", PATCH_NAME);
        }
    }
}

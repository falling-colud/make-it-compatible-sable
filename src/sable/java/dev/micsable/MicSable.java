package dev.micsable;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import dev.micsable.core.MicSableConfig;
import dev.micsable.core.ModIds;

/**
 * <b>Make it Compatible: Sable</b> &mdash; the Sable half of Make it Compatible: small, self-contained compatibility
 * patches that make <a href="https://modrinth.com/mod/sable">Sable</a> play nicely with other mods. Each patch lives
 * in its own sub-package under {@code dev.micsable} and activates only when the mods it bridges are present, so the
 * mod loads cleanly with any subset of them installed.
 *
 * <h2>Patches shipped here</h2>
 * <ul>
 *   <li><b>LittleTiles &times; Sable</b> &mdash; LittleTiles tiles render and behave correctly on Sable moving
 *       sub-levels (vehicles): chunk + Sodium rendering, lighting, block interaction, collision, physics, and
 *       placement previews. Sable hard-declares LittleTiles "incompatible" at the loader level; this mod's early
 *       loader lifts that automatically (see the nested loader service) - no fml.toml hand-edit needed.</li>
 *   <li><b>Sable &times; Iris</b> &mdash; keeps Sable sub-levels (vehicles) from rendering black under shaderpacks.</li>
 *   <li><b>Immersive Portals &times; Sable</b> &mdash; makes Immersive Portals (NeoForge) and Sable coexist:
 *       resolves their collision-mixin conflict, keeps entities on vehicles tracked and synced, keeps vehicles
 *       loaded and visible through portals (cross-dimension included), and fixes sub-level rendering in portal
 *       views. Based on ImmersivePortalSableBridge (rebbyIf &amp; Bunting_chj), adapted for Sable 2.0; see
 *       {@link dev.micsable.immersive_portals_sable.ImmersivePortalsSablePatch}.</li>
 * </ul>
 *
 * <p>The Voxy compatibility patches (including the Sable render-distance / Sable&times;Voxy compat) live in the
 * sibling mod <b>Make it Compatible: Voxy</b>.</p>
 */
@Mod(MicSable.MOD_ID)
public final class MicSable {

    public static final String MOD_ID = "micsable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MicSable(final IEventBus modBus, final ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT)
            container.registerConfig(ModConfig.Type.CLIENT, MicSableConfig.CLIENT_SPEC);

        LOGGER.info("[Make it Compatible: Sable] loaded - scanning for applicable compatibility patches.");

        // Mixin patches self-activate via their config plugins; we only log their status here.
        logPatch("LittleTiles x Sable", ModIds.LITTLETILES, ModIds.SABLE);
        logPatch("Sable x Iris sub-level shading", ModIds.SABLE, ModIds.IRIS);
        logPatch("Immersive Portals x Sable", ModIds.SABLE, ModIds.IMMERSIVE_PORTALS);

        // Event patches: register only when their mods are present.
        registerLittleTilesSableTicker();
        registerImmersivePortalsSanityCheck(modBus);
    }

    /**
     * Once everything has loaded, verifies the Immersive Portals x Sable patch's collision seam ended up in the
     * intended shape (its MixinSquared annotation adjuster is the one mechanism that can fail <em>silently</em>)
     * and logs the result.
     */
    private void registerImmersivePortalsSanityCheck(final IEventBus modBus) {
        if (allPresent(ModIds.SABLE, ModIds.IMMERSIVE_PORTALS))
            modBus.addListener((final FMLCommonSetupEvent event) ->
                dev.micsable.immersive_portals_sable.ImmersivePortalsSableSanityCheck.run());
    }

    /**
     * Server-tick reconciliation for LittleTiles content on Sable sub-levels (mass tracker rebuilds and
     * tile-geometry connectivity splits). The mixin half of the patch marks sub-levels dirty; this handler
     * processes them at the end of each server tick.
     */
    private void registerLittleTilesSableTicker() {
        if (allPresent(ModIds.LITTLETILES, ModIds.SABLE)) {
            NeoForge.EVENT_BUS.register(dev.micsable.littletiles_sable.SableTilePhysicsTicker.class);
            logPatchStatus("LittleTiles x Sable tile physics ticker", "ACTIVE");
        }
    }

    private static boolean allPresent(final String... modIds) {
        final ModList mods = ModList.get();
        if (mods == null)
            return false;
        for (final String id : modIds)
            if (!mods.isLoaded(id))
                return false;
        return true;
    }

    private static void logPatch(final String name, final String... requiredMods) {
        final ModList mods = ModList.get();
        final StringBuilder missing = new StringBuilder();
        for (final String id : requiredMods)
            if (mods == null || !mods.isLoaded(id))
                missing.append(missing.length() == 0 ? "" : ", ").append(id);
        logPatchStatus(name, missing.length() == 0 ? "ACTIVE" : "dormant (missing: " + missing + ")");
    }

    private static void logPatchStatus(final String name, final String status) {
        LOGGER.info("[Make it Compatible: Sable]   patch '{}' -> {}", name, status);
    }
}

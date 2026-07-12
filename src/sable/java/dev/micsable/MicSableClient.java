package dev.micsable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import dev.micsable.client.gui.MicSableOptionsScreen;

/**
 * Client-only: registers Make it Compatible: Sable's own options screen (the Sable compat options) behind
 * <b>Mods &rarr; Make it Compatible: Sable &rarr; Config</b>.
 */
@Mod(value = MicSable.MOD_ID, dist = Dist.CLIENT)
public final class MicSableClient {

    public MicSableClient(final ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (modContainer, parent) -> new MicSableOptionsScreen(parent));
    }
}

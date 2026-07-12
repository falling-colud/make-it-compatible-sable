package dev.micsable.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.micsable.core.MicSableConfig;
import dev.micsable.core.ModIds;
import dev.micsable.core.RequiredModsMixinPlugin;

/**
 * Make it Compatible: Sable's options screen (Mods &rarr; Make it Compatible: Sable &rarr; Config): the behavioural
 * compat options for the Sable patches. Each option renders disabled (with a "requires ..." tooltip) when its target
 * mods are missing, matching how the mixin patches themselves stay dormant.
 */
public final class MicSableOptionsScreen extends OptionsScreenBase {

    public MicSableOptionsScreen(final Screen parent) {
        super(Component.literal("Make it Compatible: Sable"), parent);
    }

    @Override
    protected List<Row> buildRows(final String query) {
        final List<Row> out = new ArrayList<>();

        addSection(out, query, "Sable x Iris", rows(
            new BoolRow("Async Sub-Level Build (Shaders)",
                "Under an Iris shaderpack, build a just-edited Sable sub-level section on the async chunk-build "
                    + "worker thread instead of synchronously on the render thread. Fixes the 'edit the vehicle and "
                    + "its blocks go black' bug (the render-thread path can bake the old vertex format). Costs a "
                    + "frame or two of latency before an edit shows. No effect without a shaderpack.",
                "Sable Iris shaders black vehicle",
                MicSableConfig::sableIrisAsyncSubLevelBuilds,
                MicSableConfig::setSableIrisAsyncSubLevelBuilds,
                true,
                RequiredModsMixinPlugin.allPresent(ModIds.SABLE, ModIds.IRIS) ? null : "Sable + Iris")));
        return out;
    }

    @Override
    protected void resetAllToDefaults() {
        MicSableConfig.setSableIrisAsyncSubLevelBuilds(true);
    }
}

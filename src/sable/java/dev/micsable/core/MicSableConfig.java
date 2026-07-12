package dev.micsable.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Make it Compatible: Sable's config ({@code micsable-client.toml}) &mdash; the behavioural compat options for the
 * Sable patches. Currently just the Sable &times; Iris sub-level shading toggle. Edited from the mod's own options
 * screen (Mods &rarr; Make it Compatible: Sable &rarr; Config).
 *
 * <p>All accessors guard {@link ModConfigSpec#isLoaded()} and fall back to the default, so they are safe to call
 * before the config has loaded. Every value sets an explicit {@code translation(...)} key defined in
 * {@code assets/micsable/lang/en_us.json}.</p>
 */
public final class MicSableConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    /** Prefix for every option's display-name translation key (see the lang file). */
    private static final String T = "micsable.config.";

    // --- CLIENT: Sable x Iris (sub-level shading / black faces) ---
    private static final ModConfigSpec.BooleanValue SABLE_IRIS_ASYNC_BUILD;

    static {
        final ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Sable x Iris compatibility - keep Sable sub-levels (vehicles) from rendering black under shaders.")
            .translation(T + "section.sableiris")
            .push("sable_iris");
        SABLE_IRIS_ASYNC_BUILD = b
            .comment("Under an Iris shaderpack, build a just-edited sub-level section on the async chunk-build worker",
                     "thread instead of synchronously on the render thread. Sable builds player-edited sections",
                     "synchronously for responsiveness, but on the render thread Iris may not widen the vertex format",
                     "to its shader (52-byte) layout, so the section bakes in the old 32-byte format and the shader",
                     "renders it solid black - the 'edit the vehicle and its blocks go black' bug. The async path is",
                     "the same one every other section already uses and always bakes the correct format. Costs a frame",
                     "or two of latency before an edit shows (the old mesh stays until then; no flash). No effect",
                     "without a shaderpack. Turn off only to confirm this is the cause. Requires Iris.")
            .translation(T + "sableiris.buildEditedSubLevelsAsyncUnderShaders")
            .define("buildEditedSubLevelsAsyncUnderShaders", true);
        b.pop();

        CLIENT_SPEC = b.build();
    }

    // --- Sable x Iris (CLIENT) ---

    /** @return true if just-edited Sable sub-level sections should build on the async worker thread under a shaderpack (fixes black-on-edit vehicles). */
    public static boolean sableIrisAsyncSubLevelBuilds() {
        return !CLIENT_SPEC.isLoaded() || SABLE_IRIS_ASYNC_BUILD.get();
    }

    /** Sets and persists whether just-edited sub-level sections build async under shaders. */
    public static void setSableIrisAsyncSubLevelBuilds(final boolean value) {
        SABLE_IRIS_ASYNC_BUILD.set(value);
        SABLE_IRIS_ASYNC_BUILD.save();
    }

    private MicSableConfig() {}
}

package dev.micsable.sable_iris;

import dev.micsable.core.ModIds;
import dev.micsable.core.RequiredModsMixinPlugin;

/**
 * Gates the dynamic-shading-off-under-shaders mixin on both {@code sable} and {@code iris} being present.
 */
public final class SableIrisMixinPlugin extends RequiredModsMixinPlugin {

    public SableIrisMixinPlugin() {
        super("Sable x Iris (sub-level shading)", ModIds.SABLE, ModIds.IRIS);
    }
}

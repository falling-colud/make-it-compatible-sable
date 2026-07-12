package dev.micsable.littletiles_sable;

import dev.micsable.core.RequiredModsMixinPlugin;

/**
 * Gates the LittleTiles &times; Sable mixin config on both {@code littletiles} and {@code sable} being present.
 * See {@link RequiredModsMixinPlugin} for the mechanism.
 *
 * <p>One mixin ({@code LittleAnimationRenderManagerSodiumMixin}) additionally targets a class that only exists when
 * Sodium is installed, so it is gated on Sodium presence to avoid a missing-target error on vanilla-renderer setups.</p>
 */
public final class LittleTilesSableMixinPlugin extends RequiredModsMixinPlugin {

    private static final String SODIUM = "sodium";
    private static final boolean SODIUM_PRESENT = RequiredModsMixinPlugin.allPresent(SODIUM);

    public LittleTilesSableMixinPlugin() {
        super("LittleTiles x Sable", LittleTilesSablePatch.LITTLETILES, LittleTilesSablePatch.SABLE);
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (mixinClassName.endsWith("LittleAnimationRenderManagerSodiumMixin"))
            return super.shouldApplyMixin(targetClassName, mixinClassName) && SODIUM_PRESENT;
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}

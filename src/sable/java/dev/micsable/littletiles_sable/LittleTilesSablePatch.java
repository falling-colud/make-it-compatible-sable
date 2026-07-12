package dev.micsable.littletiles_sable;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Shared constants for the <b>LittleTiles &times; Sable</b> patch of {@link dev.micsable.MicSable}.
 *
 * <p>This patch makes LittleTiles tiles render correctly on Sable moving "sub-levels" (vehicles) and behave
 * correctly there for lighting, block interaction, collision, and placement previews. All of the real work happens
 * in the mixins under {@code dev.micsable.littletiles_sable.mixin} (and their client/common helpers); those
 * mixins are applied only when both {@code littletiles} and {@code sable} are present &mdash; see
 * {@link LittleTilesSableMixinPlugin}. This class carries nothing but the patch's logger and id, and deliberately
 * references no LittleTiles or Sable types so it is safe to load even when the patch is dormant.</p>
 *
 * <p>Why the bridge works: Sable renders sub-level plot chunks through its own <em>vanilla</em>
 * {@code SectionRenderDispatcher} sections &mdash; even under Sodium (the "reach-around" renderer). LittleTiles'
 * vanilla render mixins therefore already fire for those sections; the only missing piece is that LittleTiles'
 * async geometry-build pipeline picks where to upload a tile's mesh via {@code RenderingLevelHandler.of(level)},
 * which under Sodium returns the global Sodium renderer and vertex format &mdash; both wrong for a Sable plot. The
 * patch redirects that choice, for tile-blocks inside a Sable plot, to a handler that uses the vanilla (Forge)
 * vertex format and Sable's own render section.</p>
 */
public final class LittleTilesSablePatch {

    /** The mod ids this patch bridges; the mixin plugin gates on both being present. */
    public static final String LITTLETILES = "littletiles";
    public static final String SABLE = "sable";

    public static final Logger LOGGER = LogUtils.getLogger();

    private LittleTilesSablePatch() {}
}

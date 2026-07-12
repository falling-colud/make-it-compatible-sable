package dev.micsable.littletiles_sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;

/**
 * Shared Sable plot-bounds helpers (common side).
 */
public final class SablePlots {

    private SablePlots() {}

    /**
     * Whether these plot block-bounds describe an <b>empty</b> plot (no blocks). This patch is the only thing that
     * ever keeps a sub-level alive with an empty plot - while its entire LittleTiles content is off animating as an
     * entity - so Sable code frequently mishandles the state and callers here must detect it reliably.
     *
     * <p>Beware the representations: {@code BoundingBox3i.EMPTY} is the literal box (0,0,0)-(0,0,0). On the
     * <b>server</b> an empty plot yields {@code getBoundingBox() == EMPTY} (the constant, {@code localBounds} is
     * null). On the <b>client</b> the bounds arrive over the network and are stored as a mutable <em>copy</em>, so
     * the reference test fails and {@code volume()} of the zero-box is 1 - value-equality is the only reliable
     * test there. A real plot can never legitimately have the zero-box bounds: plot blocks live in Sable's far-away
     * plot regions, never at world (0,0,0).</p>
     */
    public static boolean isEmptyPlotBounds(final BoundingBox3ic bounds) {
        return bounds == null || bounds.volume() <= 0 || bounds.equals(BoundingBox3i.EMPTY);
    }
}

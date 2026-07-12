package dev.micsable.sable_iris;

import com.mojang.blaze3d.systems.RenderSystem;

import net.irisshaders.iris.api.v0.IrisApi;

/**
 * Tiny isolation layer over Iris' stable {@code v0} API that answers "is a shaderpack rendering right now" in a way
 * that is <b>safe to read from any thread</b>.
 *
 * <p>Why this matters: {@code IrisApi.isShaderPackInUse()} is implemented as
 * {@code Iris.getPipelineManager().getPipelineNullable() != null}, and that pipeline is render-thread state. Called
 * from a chunk-build <em>worker</em> thread (Sable builds sub-level sections asynchronously) it can read {@code null}
 * and wrongly report <em>no shaderpack</em>. Our {@link dev.micsable.sable_iris.mixin.SableDynamicShadingMixin}
 * uses this value to decide how Sable bakes a sub-level section's shading; a worker reading the wrong value bakes the
 * section in Sable's dynamic-shade (Veil) path, which renders black/wrong under an Iris pack that has no Veil shader to
 * interpret it - and because each async build re-rolls the read, the vehicle's lighting flickers break/fix as the
 * player edits it.</p>
 *
 * <p>Fix: <b>only query Iris on the render thread</b> (where the pipeline is live), cache the result, and have every
 * other thread read that last render-thread sample. {@link dev.micsable.sable_iris.mixin.SubLevelShadeStateMixin}
 * calls {@link #packInUse()} once per frame at the head of Sable's sub-level compile pass (render thread) so the cache
 * is freshly primed before the async builds that frame read it.</p>
 */
public final class IrisShaders {

    private IrisShaders() {}

    private static volatile boolean cached;
    private static volatile boolean primed;
    private static volatile long cachedAtNanos;
    private static volatile boolean loggedDetected;

    /** Re-query Iris at most this often; pack state only changes on a shader reload, never mid-frame. */
    private static final long REFRESH_NANOS = 50_000_000L; // 50 ms

    /**
     * @return {@code true} while an Iris shaderpack is actively rendering. The underlying Iris query touches
     *         render-thread pipeline state, so it is <b>only performed on the render thread</b>; off-thread callers
     *         (async section builds) get the most recent render-thread sample. This keeps the value a build reads
     *         identical no matter which thread the build runs on, so a section's baked shading is consistent.
     */
    public static boolean packInUse() {
        if (RenderSystem.isOnRenderThread()) {
            final long now = System.nanoTime();
            // A {@code primed} flag (not a sentinel timestamp) forces the first render-thread call to query: nanoTime's
            // origin is arbitrary, so {@code now - 0} could overflow and wrongly skip the initial query.
            if (!primed || now - cachedAtNanos >= REFRESH_NANOS) {
                final boolean inUse = IrisApi.getInstance().isShaderPackInUse();
                cached = inUse;
                cachedAtNanos = now;
                primed = true;
                if (inUse && !loggedDetected) {
                    loggedDetected = true;
                    org.slf4j.LoggerFactory.getLogger("micsable/sable_iris").info(
                            "[Sable x Iris] shaderpack detected on the render thread; sub-level shade state is now "
                                    + "pinned for async builds (fixes vehicle lighting flickering on edit).");
                }
            }
        }
        return cached;
    }
}

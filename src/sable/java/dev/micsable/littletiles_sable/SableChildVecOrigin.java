package dev.micsable.littletiles_sable;

import com.mojang.blaze3d.vertex.PoseStack;

import team.creative.creativecore.common.util.math.matrix.ChildVecOrigin;
import team.creative.creativecore.common.util.math.matrix.VecOrigin;
import team.creative.creativecore.common.util.math.vec.Vec3d;

/**
 * The origin given to LittleTiles animation entities that live on a Sable plot: the animation's own motion
 * (CreativeCore {@link ChildVecOrigin} rotation/offset around its structure center, in plot coordinates) parented
 * to the sub-level's pose ({@link SableSubLevelOrigin}).
 *
 * <p>Point transforms compose through the inherited parent chain in doubles - fine. Rendering is the exception:
 * {@code ChildVecOrigin.setupRenderingInternal} would push parent and child matrices separately onto the float
 * {@link PoseStack}, and both contain translations of plot-coordinate magnitude (millions of blocks) that only
 * cancel <em>after</em> composition - float32 would eat the geometry (visible wobble). The override composes the
 * entire plot-camera-relative &rarr; world-camera-relative transform in double precision first and pushes a single
 * small-valued matrix (see {@code client.SableAnimationRender}).</p>
 */
public class SableChildVecOrigin extends ChildVecOrigin {

    public final SableSubLevelOrigin sableParent;

    public SableChildVecOrigin(final SableSubLevelOrigin parent, final Vec3d center) {
        super(parent, center);
        this.sableParent = parent;
    }

    @Override
    public void setupRenderingInternal(final PoseStack matrixStack, final double camX, final double camY, final double camZ, final float partialTicks) {
        dev.micsable.littletiles_sable.client.SableAnimationRender.setupComposedRendering(
            matrixStack, this, this.sableParent.subLevel, camX, camY, camZ, partialTicks);
    }

    @Override
    protected VecOrigin createInternalCopy() {
        return new SableChildVecOrigin(this.sableParent, new Vec3d(this.center()));
    }
}

package dev.micsable.littletiles_sable.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A {@link BlockAndTintGetter} that forwards everything to a delegate but reports <b>flat</b> directional shade
 * ({@code getShade == 1.0}).
 *
 * <p>Sable bakes vanilla blocks on a sub-level with flat shade (its {@code getShade} wrap returns {@code 1.0f})
 * and then applies directional face-shading dynamically in its chunk shader from the vertex normal, so it rotates
 * with the vehicle. LittleTiles bakes tiles through NeoForge's {@code QuadLighter}, which Sable's wrap doesn't
 * touch, so without this they keep their <em>own</em> static shade and then get Sable's dynamic shade on top -
 * doubly darkened. Wrapping the lighter's level in this makes tile shade flat too, so the two match.</p>
 *
 * <p>Lighting/brightness/AO all delegate unchanged - only the directional shade is neutralised.</p>
 */
public final class FlatShadeGetter implements BlockAndTintGetter {

    private final BlockAndTintGetter delegate;

    public FlatShadeGetter(final BlockAndTintGetter delegate) {
        this.delegate = delegate;
    }

    @Override
    public float getShade(final Direction direction, final boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
        return this.delegate.getBlockTint(pos, resolver);
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(final BlockPos pos) {
        return this.delegate.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        return this.delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos) {
        return this.delegate.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return this.delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.delegate.getMinBuildHeight();
    }
}

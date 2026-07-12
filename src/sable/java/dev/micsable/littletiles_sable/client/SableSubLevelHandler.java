package dev.micsable.littletiles_sable.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import team.creative.littletiles.client.render.cache.build.RenderingLevelHandler;
import team.creative.littletiles.client.render.cache.pipeline.LittleRenderPipelineType;
import team.creative.littletiles.client.render.mc.RenderChunkExtender;

/**
 * A LittleTiles {@link RenderingLevelHandler} that targets Sable sub-level render sections.
 *
 * <p>Sable always renders sub-level plots through vanilla {@code SectionRenderDispatcher} sections (even
 * under Sodium, via its "reach-around" renderer), so this handler:</p>
 * <ul>
 *   <li>uses the {@link LittleRenderPipelineType#FORGE} pipeline &mdash; i.e. the <em>vanilla</em> vertex
 *       format that those sections' {@code VertexBuffer}s expect (never the Sodium format);</li>
 *   <li>resolves the upload target section from the sub-level's
 *       {@code VanillaChunkedSubLevelRenderData} instead of the global chunk renderer;</li>
 *   <li>keeps section offsets section-local ({@code pos & 15}, the {@link RenderingLevelHandler} default),
 *       which matches the {@code CHUNK_OFFSET} uniform Sable sets when it draws each plot section.</li>
 * </ul>
 *
 * <p>The handler is stateless and shared (see {@link SableTiles#SABLE_HANDLER}); all sub-level lookup is
 * done per call from the {@code (level, sectionPos)} arguments, so it stays correct even as a vehicle moves
 * or its plot is resized between the moment a tile is queued and the moment its mesh is uploaded.</p>
 */
public final class SableSubLevelHandler extends RenderingLevelHandler {

    @Override
    public LittleRenderPipelineType getPipeline() {
        return LittleRenderPipelineType.FORGE;
    }

    @Override
    public RenderChunkExtender getRenderChunk(final Level level, final long pos) {
        final ClientSubLevel sub = SableTiles.subLevelAt(level, pos);
        if (sub != null) {
            final RenderChunkExtender section = SableTiles.renderChunkAt(sub, pos);
            if (section != null)
                return section;
        }
        // The chunk is no longer part of a (chunked) Sable plot - behave exactly like the vanilla handler.
        return RenderingLevelHandler.VANILLA.getRenderChunk(level, pos);
    }

    @Override
    public BlockPos standardOffset(final Level level, final SectionPos pos) {
        return pos.origin();
    }
}

package com.facet.client;

import java.util.Map;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verify vanilla Mixin target descriptors against the preview's loaded classes. */
final class FacetPreviewHooksTest {
	@Test
	void hologramLinesUseTheOitCompatibleType() {
		assertSame(RenderTypes.linesTranslucentNoDepthWrite(),
				FacetMcBridge.oitSurfaceRenderType(RenderTypes.linesTranslucent()));
		assertSame(RenderTypes.translucentMovingBlock(),
				FacetMcBridge.oitSurfaceRenderType(RenderTypes.translucentMovingBlock()));
	}

	@Test
	void vanillaModelAndTerrainMixinTargetsMatchPreviewClasses() throws ReflectiveOperationException {
		assertEquals(SpriteLoader.Preparations.class,
				MaterialBaker.class.getDeclaredField("blockAtlas").getType());
		assertEquals(void.class,
				MaterialBaker.class.getDeclaredMethod("logMissingTextures").getReturnType());

		assertEquals(Map.class,
				ModelManager.class.getDeclaredMethod("createBlockStateToModelDispatch",
						Map.class, BlockStateModel.class).getReturnType());

		assertEquals(SectionCompiler.Results.class,
				SectionCompiler.class.getDeclaredMethod("compile",
						SectionPos.class, RenderSectionRegion.class, VertexSorting.class,
						SectionBufferBuilderPack.class).getReturnType());

		assertEquals(BlockModelLighter.class, ModelBlockRenderer.class.getDeclaredField("lighter").getType());
		assertEquals(boolean.class, ModelBlockRenderer.class.getDeclaredField("ambientOcclusion").getType());
		assertEquals(BlockPos.MutableBlockPos.class, ModelBlockRenderer.class.getDeclaredField("scratchPos").getType());
		assertEquals(QuadInstance.class, ModelBlockRenderer.class.getDeclaredField("quadInstance").getType());
		assertEquals(boolean.class,
				ModelBlockRenderer.class.getDeclaredMethod("shouldRenderFace",
						BlockAndTintGetter.class, BlockState.class, Direction.class, BlockPos.class).getReturnType());
		assertEquals(void.class,
				ModelBlockRenderer.class.getDeclaredMethod("tesselateBlock",
						BlockQuadOutput.class, float.class, float.class, float.class,
						BlockAndTintGetter.class, BlockPos.class, BlockState.class,
						BlockStateModel.class, long.class).getReturnType());
	}
}

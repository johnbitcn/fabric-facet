package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Version-neutral view of the NeoForge render stage that supplies Facet's custom geometry.
 *
 * <p>26.1/26.2 draw immediately from within {@code AfterTranslucentBlocks}. 26.3 cannot upload
 * vertex buffers inside an active render pass, so it collects geometry during
 * {@code PrepareRenderBuffersEvent}, uploads it there, and only issues the draws later.
 */
interface FacetNeoForgeFrameContext {
	PoseStack poseStack();

	LevelRenderState levelRenderState();

	void draw(RenderType renderType, FacetNeoForgePlatform.Geometry geometry);

	void draw(RenderType firstType, RenderType secondType, FacetNeoForgePlatform.DualGeometry geometry);
}

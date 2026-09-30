package com.facet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

final class FacetNeoForgePlatform {
	// Separate fallback buffers keep requesting the outline material from ending the model batch.
	private static final MultiBufferSource.BufferSource AFTER_TERRAIN_PRIMARY_SOURCE =
			MultiBufferSource.immediate(new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));
	private static final MultiBufferSource.BufferSource AFTER_TERRAIN_SECONDARY_SOURCE =
			MultiBufferSource.immediate(new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));

	interface Geometry {
		void render(PoseStack poseStack, VertexConsumer consumer);
	}

	interface DualGeometry {
		void render(PoseStack poseStack, VertexConsumer first, VertexConsumer second);
	}

	private FacetNeoForgePlatform() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYSYM;
	}

	static void rotate(PoseStack poseStack, Quaternionf rotation) {
		poseStack.mulPose(rotation);
	}

	static MutableQuad setSprite(MutableQuad quad, TextureAtlasSprite sprite, boolean cutout) {
		if (cutout) {
			return quad.setSprite(sprite, ChunkSectionLayer.CUTOUT, Sheets.cutoutBlockItemSheet());
		}
		return quad.setSprite(sprite, ChunkSectionLayer.TRANSLUCENT, Sheets.translucentBlockItemSheet());
	}

	static void setShade(MutableQuad quad, boolean shade) {
		quad.setShade(shade);
	}

	static Camera mainCamera(Minecraft minecraft) {
		return minecraft.gameRenderer.getMainCamera();
	}

	static void showScreen(Minecraft minecraft, Screen screen) {
		minecraft.setScreen(screen);
	}

	static void rebuildChunks(Minecraft minecraft) {
		if (minecraft.level != null) {
			minecraft.levelRenderer.allChanged();
		}
	}

	static void render(RenderLevelStageEvent.AfterTranslucentBlocks event, RenderType renderType, Geometry geometry) {
		try {
			VertexConsumer consumer = AFTER_TERRAIN_PRIMARY_SOURCE.getBuffer(renderType);
			geometry.render(event.getPoseStack(), consumer);
		} finally {
			AFTER_TERRAIN_PRIMARY_SOURCE.endBatch(renderType);
		}
	}

	static void render(RenderLevelStageEvent.AfterTranslucentBlocks event, RenderType firstType, RenderType secondType, DualGeometry geometry) {
		try {
			VertexConsumer first = AFTER_TERRAIN_PRIMARY_SOURCE.getBuffer(firstType);
			VertexConsumer second = AFTER_TERRAIN_SECONDARY_SOURCE.getBuffer(secondType);
			geometry.render(event.getPoseStack(), first, second);
		} finally {
			AFTER_TERRAIN_PRIMARY_SOURCE.endBatch(firstType);
			AFTER_TERRAIN_SECONDARY_SOURCE.endBatch(secondType);
		}
	}

	static void registerRenderListeners() {
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> {
			FacetNeoForgeFrameContext context = new FacetNeoForgeFrameContext() {
				@Override
				public PoseStack poseStack() {
					return event.getPoseStack();
				}

				@Override
				public LevelRenderState levelRenderState() {
					return event.getLevelRenderState();
				}

				@Override
				public void draw(RenderType renderType, Geometry geometry) {
					FacetNeoForgePlatform.render(event, renderType, geometry);
				}

				@Override
				public void draw(RenderType firstType, RenderType secondType, DualGeometry geometry) {
					FacetNeoForgePlatform.render(event, firstType, secondType, geometry);
				}
			};
			FacetNeoForgeHoverOutline.renderDistant(context);
			FacetNeoForgeDistanceHud.renderPath(context);
			FacetNeoForgePlacementPreview.render(context);
		});
	}
}

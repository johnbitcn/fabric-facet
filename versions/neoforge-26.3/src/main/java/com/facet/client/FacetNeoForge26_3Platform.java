package com.facet.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

final class FacetNeoForgePlatform {
	interface Geometry {
		void render(PoseStack poseStack, VertexConsumer consumer);
	}

	interface DualGeometry {
		void render(PoseStack poseStack, VertexConsumer first, VertexConsumer second);
	}

	private static final StagedVertexBuffer AFTER_TERRAIN_PRIMARY_BUFFER =
			new StagedVertexBuffer(() -> "Facet NeoForge 26.3 after translucent terrain primary", RenderType.TRANSIENT_BUFFER_SIZE);
	private static final StagedVertexBuffer AFTER_TERRAIN_SECONDARY_BUFFER =
			new StagedVertexBuffer(() -> "Facet NeoForge 26.3 after translucent terrain secondary", RenderType.TRANSIENT_BUFFER_SIZE);
	private static final List<PendingDraw> PENDING_DRAWS = new ArrayList<>();
	private static final List<ReadyDraw> READY_DRAWS = new ArrayList<>();

	private FacetNeoForgePlatform() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYBOARD;
	}

	static void rotate(PoseStack poseStack, Quaternionf rotation) {
		poseStack.rotate(rotation);
	}

	static MutableQuad setSprite(MutableQuad quad, TextureAtlasSprite sprite, boolean cutout) {
		if (cutout) {
			return quad.setSprite(sprite, ChunkSectionLayer.CUTOUT,
					Sheets.cutoutBlockItemSheet(), Sheets.cutoutBlockItemGlintSheet(), Sheets.cutoutBlockItemGlintSpecialSheet());
		}
		return quad.setSprite(sprite, ChunkSectionLayer.TRANSLUCENT,
				Sheets.translucentBlockItemSheet(), Sheets.translucentBlockItemGlintSheet(), Sheets.translucentBlockItemGlintSpecialSheet());
	}

	static void setShade(MutableQuad quad, boolean shade) {
		quad.setShadeOverride(shade ? null : Direction.UP);
	}

	static Camera mainCamera(Minecraft minecraft) {
		return minecraft.gameRenderer.mainCamera();
	}

	static void showScreen(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	static void rebuildChunks(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		var cameraSection = net.minecraft.core.SectionPos.of(mainCamera(minecraft).position());
		int distance = minecraft.options.getEffectiveRenderDistance();
		minecraft.level.setSectionRangeDirty(cameraSection.x() - distance, minecraft.level.getMinSectionY(), cameraSection.z() - distance,
				cameraSection.x() + distance, minecraft.level.getMaxSectionY(), cameraSection.z() + distance);
	}

	/**
	 * Buffer uploads are illegal inside an active render pass on 26.3, so geometry is collected
	 * and uploaded during {@link PrepareRenderBuffersEvent} and only drawn in the stage event.
	 */
	static void registerRenderListeners() {
		NeoForge.EVENT_BUS.addListener((PrepareRenderBuffersEvent event) -> prepareFrame(event));
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> renderFrame(event.getRenderPass()));
	}

	private static void prepareFrame(PrepareRenderBuffersEvent event) {
		AFTER_TERRAIN_PRIMARY_BUFFER.endFrame();
		AFTER_TERRAIN_SECONDARY_BUFFER.endFrame();
		PENDING_DRAWS.clear();
		READY_DRAWS.clear();

		CollectingContext context = new CollectingContext(event.getLevelRenderState());
		FacetNeoForgeHoverOutline.renderDistant(context);
		FacetNeoForgeDistanceHud.renderPath(context);
		FacetNeoForgePlacementPreview.render(context);
		context.finish();
	}

	private static void renderFrame(RenderPass renderPass) {
		for (ReadyDraw ready : READY_DRAWS) {
			PreparedRenderType prepared = ready.renderType().prepare();
			prepared.drawFromBuffer(ready.info(), renderPass);
		}
		READY_DRAWS.clear();
		AFTER_TERRAIN_PRIMARY_BUFFER.endDraw();
		AFTER_TERRAIN_SECONDARY_BUFFER.endDraw();
	}

	private static StagedVertexBuffer.Draw append(StagedVertexBuffer buffer, RenderType renderType) {
		VertexSorting sorting = renderType.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null;
		return buffer.appendDraw(renderType.format(), renderType.primitiveTopology(), sorting);
	}

	private record PendingDraw(RenderType renderType, StagedVertexBuffer buffer, StagedVertexBuffer.Draw draw) {
	}

	private record ReadyDraw(RenderType renderType, StagedVertexBuffer.ExecuteInfo info) {
	}

	private static final class CollectingContext implements FacetNeoForgeFrameContext {
		private final PoseStack poseStack = new PoseStack();
		private final LevelRenderState levelRenderState;

		private CollectingContext(LevelRenderState levelRenderState) {
			this.levelRenderState = levelRenderState;
		}

		@Override
		public PoseStack poseStack() {
			return poseStack;
		}

		@Override
		public LevelRenderState levelRenderState() {
			return levelRenderState;
		}

		@Override
		public void draw(RenderType renderType, Geometry geometry) {
			StagedVertexBuffer.Draw draw = append(AFTER_TERRAIN_PRIMARY_BUFFER, renderType);
			geometry.render(poseStack, AFTER_TERRAIN_PRIMARY_BUFFER.getVertexBuilder(draw));
			PENDING_DRAWS.add(new PendingDraw(renderType, AFTER_TERRAIN_PRIMARY_BUFFER, draw));
		}

		@Override
		public void draw(RenderType firstType, RenderType secondType, DualGeometry geometry) {
			StagedVertexBuffer.Draw firstDraw = append(AFTER_TERRAIN_PRIMARY_BUFFER, firstType);
			StagedVertexBuffer.Draw secondDraw = append(AFTER_TERRAIN_SECONDARY_BUFFER, secondType);
			geometry.render(poseStack, AFTER_TERRAIN_PRIMARY_BUFFER.getVertexBuilder(firstDraw),
					AFTER_TERRAIN_SECONDARY_BUFFER.getVertexBuilder(secondDraw));
			PENDING_DRAWS.add(new PendingDraw(firstType, AFTER_TERRAIN_PRIMARY_BUFFER, firstDraw));
			PENDING_DRAWS.add(new PendingDraw(secondType, AFTER_TERRAIN_SECONDARY_BUFFER, secondDraw));
		}

		private void finish() {
			AFTER_TERRAIN_PRIMARY_BUFFER.upload();
			AFTER_TERRAIN_SECONDARY_BUFFER.upload();
			for (PendingDraw pending : PENDING_DRAWS) {
				StagedVertexBuffer.ExecuteInfo info = pending.buffer().getExecuteInfo(pending.draw());
				if (info != null) {
					READY_DRAWS.add(new ReadyDraw(pending.renderType(), info));
				}
			}
			PENDING_DRAWS.clear();
		}
	}
}

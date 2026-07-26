package com.facet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class FacetNeoForgePlatform {
	interface Geometry {
		void render(PoseStack poseStack, VertexConsumer consumer);
	}

	interface DualGeometry {
		void render(PoseStack poseStack, VertexConsumer first, VertexConsumer second);
	}

	private static final StagedVertexBuffer AFTER_TERRAIN_PRIMARY_BUFFER =
			new StagedVertexBuffer(() -> "Facet NeoForge 26.2 after translucent terrain primary", RenderType.TRANSIENT_BUFFER_SIZE);
	private static final StagedVertexBuffer AFTER_TERRAIN_SECONDARY_BUFFER =
			new StagedVertexBuffer(() -> "Facet NeoForge 26.2 after translucent terrain secondary", RenderType.TRANSIENT_BUFFER_SIZE);

	private FacetNeoForgePlatform() {
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

	static void render(RenderLevelStageEvent.AfterTranslucentBlocks event, RenderType renderType, Geometry geometry) {
		try {
			StagedVertexBuffer.Draw draw = append(AFTER_TERRAIN_PRIMARY_BUFFER, renderType);
			geometry.render(event.getPoseStack(), AFTER_TERRAIN_PRIMARY_BUFFER.getVertexBuilder(draw));
			AFTER_TERRAIN_PRIMARY_BUFFER.upload();
			execute(AFTER_TERRAIN_PRIMARY_BUFFER, renderType, draw);
		} finally {
			AFTER_TERRAIN_PRIMARY_BUFFER.endFrame();
		}
	}

	static void render(RenderLevelStageEvent.AfterTranslucentBlocks event, RenderType firstType, RenderType secondType, DualGeometry geometry) {
		try {
			StagedVertexBuffer.Draw firstDraw = append(AFTER_TERRAIN_PRIMARY_BUFFER, firstType);
			StagedVertexBuffer.Draw secondDraw = append(AFTER_TERRAIN_SECONDARY_BUFFER, secondType);
			geometry.render(event.getPoseStack(), AFTER_TERRAIN_PRIMARY_BUFFER.getVertexBuilder(firstDraw),
					AFTER_TERRAIN_SECONDARY_BUFFER.getVertexBuilder(secondDraw));
			AFTER_TERRAIN_PRIMARY_BUFFER.upload();
			AFTER_TERRAIN_SECONDARY_BUFFER.upload();
			execute(AFTER_TERRAIN_PRIMARY_BUFFER, firstType, firstDraw);
			execute(AFTER_TERRAIN_SECONDARY_BUFFER, secondType, secondDraw);
		} finally {
			AFTER_TERRAIN_PRIMARY_BUFFER.endFrame();
			AFTER_TERRAIN_SECONDARY_BUFFER.endFrame();
		}
	}

	private static StagedVertexBuffer.Draw append(StagedVertexBuffer buffer, RenderType renderType) {
		VertexSorting sorting = renderType.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null;
		return buffer.appendDraw(renderType.format(), renderType.primitiveTopology(), sorting);
	}

	private static void execute(StagedVertexBuffer buffer, RenderType renderType, StagedVertexBuffer.Draw draw) {
		StagedVertexBuffer.ExecuteInfo info = buffer.getExecuteInfo(draw);
		if (info != null) {
			PreparedRenderType prepared = renderType.prepare();
			prepared.drawFromBuffer(info);
		}
	}
}

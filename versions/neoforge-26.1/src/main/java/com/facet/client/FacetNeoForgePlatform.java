package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class FacetNeoForgePlatform {
	interface Geometry {
		void render(PoseStack poseStack, VertexConsumer consumer);
	}

	interface DualGeometry {
		void render(PoseStack poseStack, VertexConsumer first, VertexConsumer second);
	}

	private FacetNeoForgePlatform() {
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
		Minecraft minecraft = Minecraft.getInstance();
		VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(renderType);
		geometry.render(event.getPoseStack(), consumer);
		minecraft.renderBuffers().bufferSource().endBatch(renderType);
	}

	static void render(RenderLevelStageEvent.AfterTranslucentBlocks event, RenderType firstType, RenderType secondType, DualGeometry geometry) {
		Minecraft minecraft = Minecraft.getInstance();
		VertexConsumer first = minecraft.renderBuffers().bufferSource().getBuffer(firstType);
		VertexConsumer second = minecraft.renderBuffers().bufferSource().getBuffer(secondType);
		geometry.render(event.getPoseStack(), first, second);
		minecraft.renderBuffers().bufferSource().endBatch(firstType);
		minecraft.renderBuffers().bufferSource().endBatch(secondType);
	}
}

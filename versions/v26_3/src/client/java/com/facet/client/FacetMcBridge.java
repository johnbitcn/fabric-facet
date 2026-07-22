package com.facet.client;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

final class FacetMcBridge {
	private FacetMcBridge() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYBOARD;
	}

	static void applyShade(QuadEmitter emitter, boolean shade) {
		emitter.shadeDirectionOverride(shade ? null : Direction.UP);
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

		SectionPos cameraSection = SectionPos.of(mainCamera(minecraft).position());
		int renderDistance = minecraft.options.getEffectiveRenderDistance();
		minecraft.level.setSectionRangeDirty(
				cameraSection.x() - renderDistance,
				minecraft.level.getMinSectionY(),
				cameraSection.z() - renderDistance,
				cameraSection.x() + renderDistance,
				minecraft.level.getMaxSectionY(),
				cameraSection.z() + renderDistance);
	}

	static void rebuildBlockSection(Minecraft minecraft, BlockPos pos) {
		if (minecraft.level == null) {
			return;
		}

		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionY = SectionPos.blockToSectionCoord(pos.getY());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
		minecraft.level.setSectionRangeDirty(sectionX, sectionY, sectionZ, sectionX, sectionY, sectionZ);
	}

	static void renderAfterTranslucentTerrain(LevelRenderContext context, Consumer<FacetRenderSink> renderer) {
		renderer.accept((poseStack, renderType, geometry) ->
				context.submitNodeCollector().submitCustomGeometry(poseStack, renderType, geometry::render));
	}

	static String worldScope(Minecraft minecraft, ClientLevel level) {
		if (minecraft.getCurrentServer() != null) {
			return "server:" + minecraft.getCurrentServer().ip;
		}

		if (minecraft.getSingleplayerServer() != null) {
			return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
		}

		return "unknown";
	}
}

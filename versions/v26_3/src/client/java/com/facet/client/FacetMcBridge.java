package com.facet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

final class FacetMcBridge {
	private FacetMcBridge() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYBOARD;
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

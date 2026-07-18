package com.facet.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

final class FacetMcBridge {
	private FacetMcBridge() {
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

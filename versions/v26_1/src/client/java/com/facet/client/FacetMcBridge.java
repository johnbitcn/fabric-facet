package com.facet.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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
}

package com.facet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

interface GraffitiClientAccess {
	void showScreen(Minecraft minecraft, Screen screen);

	void rebuildBlockSection(Minecraft minecraft, BlockPos pos);

	String worldScope(Minecraft minecraft, ClientLevel level);
}

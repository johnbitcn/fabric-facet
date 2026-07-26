package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

final class FacetMcBridge {
	private static final StagedVertexBuffer AFTER_TERRAIN_BUFFER =
			new StagedVertexBuffer(() -> "Facet after translucent terrain", RenderType.TRANSIENT_BUFFER_SIZE);

	private FacetMcBridge() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYSYM;
	}

	static void applyShade(QuadEmitter emitter, boolean shade) {
		emitter.diffuseShade(shade);
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
		List<ImmediateDraw> draws = new ArrayList<>();

		try {
			renderer.accept((poseStack, renderType, geometry) -> {
				PreparedRenderType preparedRenderType = renderType.prepare();
				VertexSorting sorting = renderType.sortOnUpload()
						? RenderSystem.getProjectionType().vertexSorting()
						: null;
				StagedVertexBuffer.Draw draw = AFTER_TERRAIN_BUFFER.appendDraw(
						renderType.format(),
						renderType.primitiveTopology(),
						sorting);
				geometry.render(poseStack.last(), AFTER_TERRAIN_BUFFER.getVertexBuilder(draw));
				draws.add(new ImmediateDraw(preparedRenderType, draw));
			});

			if (draws.isEmpty()) {
				return;
			}

			AFTER_TERRAIN_BUFFER.upload();

			for (ImmediateDraw draw : draws) {
				StagedVertexBuffer.ExecuteInfo executeInfo =
						AFTER_TERRAIN_BUFFER.getExecuteInfo(draw.draw());

				if (executeInfo != null) {
					draw.renderType().drawFromBuffer(executeInfo);
				}
			}
		} finally {
			AFTER_TERRAIN_BUFFER.endFrame();
		}
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

	private record ImmediateDraw(PreparedRenderType renderType, StagedVertexBuffer.Draw draw) {
	}
}

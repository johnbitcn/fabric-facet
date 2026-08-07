package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

public final class FacetMcBridge {
	private static final StagedVertexBuffer AFTER_TERRAIN_BUFFER =
			new StagedVertexBuffer(() -> "Facet after translucent terrain", RenderType.TRANSIENT_BUFFER_SIZE);
	private static final List<ImmediateDraw> AFTER_TERRAIN_DRAWS = new ArrayList<>();
	private static RenderPass activeClassicTransparencyPass;
	private static boolean afterTerrainBufferFramePending;
	private FacetMcBridge() {
	}

	static InputConstants.Type keyboardType() {
		return InputConstants.Type.KEYBOARD;
	}

	static boolean placementRotationPrototypeEnabled() {
		return true;
	}

	/** Cutout terrain, no translucency sort. Pending in-world validation on 26.3 (see
	 *  FacetBlockOverlay: MultiDraw cutout depth fighting risk). */
	static ChunkSectionLayer outlineChunkLayer() {
		return ChunkSectionLayer.CUTOUT;
	}

	/** Same as 26.2: shared default surface bias. */
	static double outlineSurfaceBias() {
		return FacetOutlineRules.SURFACE_BIAS;
	}

	/** Same as 26.2: material-average colors, no extra darken/boost. */
	static int prepareOutlineColor(int argb) {
		return argb;
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

	static void collectSurfaceEffects(LevelRenderContext context, Consumer<FacetRenderSink> renderer) {
		if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
			renderer.accept((poseStack, renderType, geometry) ->
					context.submitNodeCollector().submitCustomGeometry(poseStack, renderType, geometry::render));
			return;
		}

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
			AFTER_TERRAIN_DRAWS.add(new ImmediateDraw(preparedRenderType, draw));
		});

		if (!AFTER_TERRAIN_DRAWS.isEmpty()) {
			AFTER_TERRAIN_BUFFER.upload();
			afterTerrainBufferFramePending = true;
		}
	}

	static void renderAfterTranslucentTerrain(LevelRenderContext context, Consumer<FacetRenderSink> renderer) {
		if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()
				|| activeClassicTransparencyPass == null) {
			return;
		}

		for (ImmediateDraw draw : AFTER_TERRAIN_DRAWS) {
			StagedVertexBuffer.ExecuteInfo executeInfo = AFTER_TERRAIN_BUFFER.getExecuteInfo(draw.draw());

			if (executeInfo != null) {
				draw.renderType().drawFromBuffer(executeInfo, activeClassicTransparencyPass);
			}
		}
	}

	static void endSurfaceEffectsFrame() {
		if (afterTerrainBufferFramePending) {
			AFTER_TERRAIN_BUFFER.endFrame();
			afterTerrainBufferFramePending = false;
			AFTER_TERRAIN_DRAWS.clear();
		}
	}

	public static void beginClassicTransparency(RenderPass renderPass) {
		activeClassicTransparencyPass = renderPass;
	}

	public static void endClassicTransparency() {
		activeClassicTransparencyPass = null;
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

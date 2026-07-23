package com.facet.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;

final class FacetBlockOverlay {
	private static final double SURFACE_BIAS = 1.0 / 1024.0;
	private static final double GRAFFITI_SURFACE_BIAS = 1.0 / 512.0;
	private static final double GRAFFITI_FACE_SIZE = 0.785;
	private static final double GRAFFITI_FACE_INSET = (1.0 - GRAFFITI_FACE_SIZE) / 2.0;
	private static final float OUTLINE_UV = 0.5f;
	private static final Material OUTLINE_TEXTURE = new Material(Identifier.withDefaultNamespace("block/white_concrete"), true);
	private static final ModelDebugName OUTLINE_DEBUG_NAME = () -> "facet:outline";

	private FacetBlockOverlay() {
	}

	static void initialize() {
		if (!FacetClient.usesExperimentalLineOutlines()) {
			ModelLoadingPlugin.register(FacetBlockOverlay::registerModelModifiers);
		}
	}

	private static void registerModelModifiers(ModelLoadingPlugin.Context context) {
		context.modifyBlockModelAfterBake().register((model, modifierContext) -> {
			BlockState state = modifierContext.state();

			if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || model instanceof OutlineBlockStateModel) {
				return model;
			}

			Material.Baked outlineMaterial = modifierContext.baker().materials().get(OUTLINE_TEXTURE, OUTLINE_DEBUG_NAME);
			Map<GraffitiType, Material.Baked> graffitiMaterials = new EnumMap<>(GraffitiType.class);

			for (GraffitiType type : GraffitiType.values()) {
				Material texture = new Material(type.materialId(), true);
				ModelDebugName debugName = () -> "facet:graffiti/" + type.id();
				graffitiMaterials.put(type, modifierContext.baker().materials().get(texture, debugName));
			}

			return new OutlineBlockStateModel(model, outlineMaterial, graffitiMaterials);
		});
	}

	private static final class OutlineBlockStateModel extends WrapperBlockStateModel {
		private final Material.Baked outlineMaterial;
		private final Map<GraffitiType, Material.Baked> graffitiMaterials;

		private OutlineBlockStateModel(BlockStateModel wrapped, Material.Baked outlineMaterial,
				Map<GraffitiType, Material.Baked> graffitiMaterials) {
			super(wrapped);
			this.outlineMaterial = outlineMaterial;
			this.graffitiMaterials = graffitiMaterials;
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			emitOutlineQuads(emitter, level, pos, state, cullTest);
			emitGraffitiQuads(emitter, level, pos, state, cullTest);
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return null;
		}

		private void emitOutlineQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, Predicate<Direction> cullTest) {
			if (FacetClient.usesExperimentalLineOutlines()
					|| !FacetConfig.enabled()
					|| !FacetClient.shouldRenderOutline(level, pos, state)) {
				return;
			}

			VoxelShape shape = state.getShape(level, pos);

			if (shape.isEmpty()) {
				return;
			}

			int color = FacetClient.outlineColor(level, pos, state);
			boolean isCarpet = state.getBlock() instanceof CarpetBlock;

			FacetShapeEdges.forEachSurfaceStrip(shape, FacetConfig.effectiveEdgeWidth(),
					(direction, minX, minY, minZ, maxX, maxY, maxZ) -> {
				if ((isCarpet && direction != Direction.UP)
						|| (touchesBlockBoundary(direction, minX, minY, minZ, maxX, maxY, maxZ)
						&& cullTest.test(direction))) {
					return;
				}

				emitSurfaceStrip(emitter, direction, color, minX, minY, minZ, maxX, maxY, maxZ);
			});
		}

		private void emitGraffitiQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, Predicate<Direction> cullTest) {
			VoxelShape shape = state.getShape(level, pos);

			if (shape.isEmpty()) {
				return;
			}

			for (Direction direction : Direction.values()) {
				GraffitiType type = GraffitiStore.getType(pos, direction);

				if (type == null || cullTest.test(direction)
						|| GraffitiEligibility.evaluate(level, pos, state, direction) != GraffitiEligibility.Result.ALLOWED) {
					continue;
				}

				emitGraffitiFace(emitter, direction, GraffitiEligibility.facePlane(shape, direction), graffitiMaterials.get(type));
			}
		}

		private void emitGraffitiFace(QuadEmitter emitter, Direction face, double plane, Material.Baked graffitiMaterial) {
			double biasedPlane = plane + GRAFFITI_SURFACE_BIAS * face.getAxisDirection().getStep();
			double min = GRAFFITI_FACE_INSET;
			double max = 1.0 - GRAFFITI_FACE_INSET;

			switch (face) {
				case DOWN, UP -> emitGraffitiQuad(emitter, face, graffitiMaterial,
						min, biasedPlane, min, max, biasedPlane, min, max, biasedPlane, max, min, biasedPlane, max);
				case NORTH, SOUTH -> emitGraffitiQuad(emitter, face, graffitiMaterial,
						min, min, biasedPlane, max, min, biasedPlane, max, max, biasedPlane, min, max, biasedPlane);
				case WEST, EAST -> emitGraffitiQuad(emitter, face, graffitiMaterial,
						biasedPlane, min, min, biasedPlane, min, max, biasedPlane, max, max, biasedPlane, max, min);
			}
		}

		private void emitGraffitiQuad(QuadEmitter emitter, Direction face, Material.Baked graffitiMaterial,
				double x1, double y1, double z1,
				double x2, double y2, double z2,
				double x3, double y3, double z3,
				double x4, double y4, double z4) {
			float normalX = face.getStepX();
			float normalY = face.getStepY();
			float normalZ = face.getStepZ();
			boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;

			emitter.clear()
					.pos(0, (float) x1, (float) y1, (float) z1)
					.pos(1, reverseWinding ? (float) x4 : (float) x2, reverseWinding ? (float) y4 : (float) y2, reverseWinding ? (float) z4 : (float) z2)
					.pos(2, (float) x3, (float) y3, (float) z3)
					.pos(3, reverseWinding ? (float) x2 : (float) x4, reverseWinding ? (float) y2 : (float) y4, reverseWinding ? (float) z2 : (float) z4)
					.color(0, -1).color(1, -1).color(2, -1).color(3, -1)
					.uv(0, 0.0f, 1.0f)
					.uv(1, reverseWinding ? 0.0f : 1.0f, reverseWinding ? 0.0f : 1.0f)
					.uv(2, 1.0f, 0.0f)
					.uv(3, reverseWinding ? 1.0f : 0.0f, reverseWinding ? 1.0f : 0.0f)
					.materialBake(graffitiMaterial, MutableQuadView.BAKE_NORMALIZED)
					.normal(0, normalX, normalY, normalZ).normal(1, normalX, normalY, normalZ)
					.normal(2, normalX, normalY, normalZ).normal(3, normalX, normalY, normalZ)
					.nominalFace(face)
					.chunkLayer(ChunkSectionLayer.TRANSLUCENT)
					.emissive(false);
			FacetMcBridge.applyShade(emitter, true);
			emitter.ambientOcclusion(TriState.FALSE)
					.shadeMode(ShadeMode.VANILLA)
					.tintIndex(-1)
					.emit();
		}

		private boolean touchesBlockBoundary(Direction direction,
				double minX, double minY, double minZ,
				double maxX, double maxY, double maxZ) {
			return switch (direction) {
				case DOWN -> Math.abs(minY) <= FacetShapeEdges.AXIS_EPSILON;
				case UP -> Math.abs(maxY - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
				case NORTH -> Math.abs(minZ) <= FacetShapeEdges.AXIS_EPSILON;
				case SOUTH -> Math.abs(maxZ - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
				case WEST -> Math.abs(minX) <= FacetShapeEdges.AXIS_EPSILON;
				case EAST -> Math.abs(maxX - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
			};
		}

		private void emitSurfaceStrip(QuadEmitter emitter, Direction face, int color,
				double minX, double minY, double minZ,
				double maxX, double maxY, double maxZ) {
			double bias = SURFACE_BIAS * face.getAxisDirection().getStep();

			switch (face) {
				case DOWN, UP -> emitQuad(emitter, face, color,
						minX, minY + bias, minZ, maxX, minY + bias, minZ,
						maxX, minY + bias, maxZ, minX, minY + bias, maxZ);
				case NORTH, SOUTH -> emitQuad(emitter, face, color,
						minX, minY, minZ + bias, maxX, minY, minZ + bias,
						maxX, maxY, minZ + bias, minX, maxY, minZ + bias);
				case WEST, EAST -> emitQuad(emitter, face, color,
						minX + bias, minY, minZ, minX + bias, minY, maxZ,
						minX + bias, maxY, maxZ, minX + bias, maxY, minZ);
			}
		}

		private void emitQuad(QuadEmitter emitter, Direction face, int color,
				double x1, double y1, double z1,
				double x2, double y2, double z2,
				double x3, double y3, double z3,
				double x4, double y4, double z4) {
			float normalX = face.getStepX();
			float normalY = face.getStepY();
			float normalZ = face.getStepZ();
			boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;

			emitter.clear()
					.pos(0, (float) x1, (float) y1, (float) z1)
					.pos(1, reverseWinding ? (float) x4 : (float) x2, reverseWinding ? (float) y4 : (float) y2, reverseWinding ? (float) z4 : (float) z2)
					.pos(2, (float) x3, (float) y3, (float) z3)
					.pos(3, reverseWinding ? (float) x2 : (float) x4, reverseWinding ? (float) y2 : (float) y4, reverseWinding ? (float) z2 : (float) z4)
					.color(0, color).color(1, color).color(2, color).color(3, color)
					.uv(0, OUTLINE_UV, OUTLINE_UV).uv(1, OUTLINE_UV, OUTLINE_UV)
					.uv(2, OUTLINE_UV, OUTLINE_UV).uv(3, OUTLINE_UV, OUTLINE_UV)
					.materialBake(outlineMaterial, MutableQuadView.BAKE_NORMALIZED)
					.normal(0, normalX, normalY, normalZ).normal(1, normalX, normalY, normalZ)
					.normal(2, normalX, normalY, normalZ).normal(3, normalX, normalY, normalZ)
					.nominalFace(face)
					.chunkLayer(ChunkSectionLayer.TRANSLUCENT)
					.emissive(false);
			FacetMcBridge.applyShade(emitter, true);
			emitter.ambientOcclusion(TriState.DEFAULT)
					.shadeMode(ShadeMode.VANILLA)
					.tintIndex(-1)
					.emit();
		}
	}
}

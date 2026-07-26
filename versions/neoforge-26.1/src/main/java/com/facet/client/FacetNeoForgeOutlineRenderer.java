package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * NeoForge's core block-outline renderer. Product features remain loader-local
 * until they receive their own parity and in-world validation.
 */
final class FacetNeoForgeOutlineRenderer {
	private static final float OUTLINE_UV = 0.5f;
	private static final double GRAFFITI_SURFACE_BIAS = 1.0 / 512.0;
	private static final double GRAFFITI_FACE_SIZE = 0.785;
	private static final double GRAFFITI_FACE_INSET = (1.0 - GRAFFITI_FACE_SIZE) / 2.0;
	private static final AtomicBoolean LOGGED_FIRST_GEOMETRY = new AtomicBoolean();

	private FacetNeoForgeOutlineRenderer() {
	}

	static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
		TextureAtlasSprite sprite = event.getTextureGetter().apply(Identifier.withDefaultNamespace("block/white_concrete"));
		Material.Baked material = new Material.Baked(sprite, true);
		Map<GraffitiType, Material.Baked> graffitiMaterials = new EnumMap<>(GraffitiType.class);
		for (GraffitiType type : GraffitiType.values()) {
			graffitiMaterials.put(type, new Material.Baked(event.getTextureGetter().apply(type.materialId()), true));
		}
		int wrapped = 0;

		for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
			BlockState state = entry.getKey();
			BlockStateModel model = entry.getValue();
			if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || model instanceof OutlineModel) {
				continue;
			}
			entry.setValue(new OutlineModel(model, material, graffitiMaterials));
			wrapped++;
		}

		FacetNeoForgeOutline.LOGGER.info("Wrapped {} Minecraft 26.1 block-state models for the Facet outline renderer", wrapped);
	}

	private static final class OutlineModel extends DelegateBlockStateModel {
		private final Material.Baked material;
		private final Map<GraffitiType, Material.Baked> graffitiMaterials;

		private OutlineModel(BlockStateModel delegate, Material.Baked material, Map<GraffitiType, Material.Baked> graffitiMaterials) {
			super(delegate);
			this.material = material;
			this.graffitiMaterials = graffitiMaterials;
		}

		@Override
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				List<BlockStateModelPart> parts) {
			super.collectParts(level, pos, state, random, parts);
			emitOutlineParts(level, pos, state, parts);
			emitGraffitiParts(level, pos, state, parts);
		}

		private void emitOutlineParts(BlockAndTintGetter level, BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
			if (!FacetNeoForgeOutlineConfig.enabled() || !FacetOutlineRules.shouldRender(level, pos, state)) {
				return;
			}

			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty()) {
				return;
			}

			Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
			for (Direction direction : Direction.values()) {
				culled.put(direction, new ArrayList<>());
			}
			List<BakedQuad> unculled = new ArrayList<>();
			int color = FacetOutlineRules.outlineColor(level, pos, state);
			boolean carpet = state.getBlock() instanceof CarpetBlock;

			FacetShapeEdges.forEachSurfaceStrip(shape, FacetNeoForgeOutlineConfig.edgeWidth(),
					(face, minX, minY, minZ, maxX, maxY, maxZ) -> {
						if (carpet && face != Direction.UP) {
							return;
						}
						BakedQuad quad = createOutlineQuad(face, color, minX, minY, minZ, maxX, maxY, maxZ);
						if (FacetOutlineRules.touchesBlockBoundary(face, minX, minY, minZ, maxX, maxY, maxZ)) {
							culled.get(face).add(quad);
						} else {
							unculled.add(quad);
						}
					});

			if (unculled.isEmpty() && culled.values().stream().allMatch(List::isEmpty)) {
				return;
			}
			parts.add(new OutlinePart(unculled, culled, material));
			if (LOGGED_FIRST_GEOMETRY.compareAndSet(false, true)) {
				int quadCount = unculled.size() + culled.values().stream().mapToInt(List::size).sum();
				FacetNeoForgeOutline.LOGGER.info("First Facet NeoForge outline geometry contained {} quads", quadCount);
			}
		}

		private void emitGraffitiParts(BlockAndTintGetter level, BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty()) {
				return;
			}

			Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
			for (Direction direction : Direction.values()) {
				culled.put(direction, new ArrayList<>());
				GraffitiType type = GraffitiStore.getType(pos, direction, state);
				if (type == null || GraffitiEligibility.evaluate(level, pos, state, direction) != GraffitiEligibility.Result.ALLOWED) {
					continue;
				}
				culled.get(direction).add(createGraffitiQuad(direction, GraffitiEligibility.facePlane(shape, direction),
						graffitiMaterials.get(type)));
			}

			if (culled.values().stream().allMatch(List::isEmpty)) {
				return;
			}
			parts.add(new OutlinePart(List.of(), culled, material));
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return null;
		}

		private BakedQuad createOutlineQuad(Direction face, int color,
				double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			double bias = FacetOutlineRules.SURFACE_BIAS * face.getAxisDirection().getStep();
			return switch (face) {
				case DOWN, UP -> bakeQuad(material, face, color, false, true,
						minX, minY + bias, minZ, maxX, minY + bias, minZ,
						maxX, minY + bias, maxZ, minX, minY + bias, maxZ);
				case NORTH, SOUTH -> bakeQuad(material, face, color, false, true,
						minX, minY, minZ + bias, maxX, minY, minZ + bias,
						maxX, maxY, minZ + bias, minX, maxY, minZ + bias);
				case WEST, EAST -> bakeQuad(material, face, color, false, true,
						minX + bias, minY, minZ, minX + bias, minY, maxZ,
						minX + bias, maxY, maxZ, minX + bias, maxY, minZ);
			};
		}

		private BakedQuad createGraffitiQuad(Direction face, double plane, Material.Baked graffitiMaterial) {
			double biasedPlane = plane + GRAFFITI_SURFACE_BIAS * face.getAxisDirection().getStep();
			double min = GRAFFITI_FACE_INSET;
			double max = 1.0 - GRAFFITI_FACE_INSET;
			return switch (face) {
				case DOWN, UP -> bakeQuad(graffitiMaterial, face, -1, true, false,
						min, biasedPlane, min, max, biasedPlane, min, max, biasedPlane, max, min, biasedPlane, max);
				case NORTH, SOUTH -> bakeQuad(graffitiMaterial, face, -1, true, false,
						min, min, biasedPlane, max, min, biasedPlane, max, max, biasedPlane, min, max, biasedPlane);
				case WEST, EAST -> bakeQuad(graffitiMaterial, face, -1, true, false,
						biasedPlane, min, min, biasedPlane, min, max, biasedPlane, max, max, biasedPlane, max, min);
			};
		}

		private BakedQuad bakeQuad(Material.Baked material, Direction face, int color, boolean fullSprite, boolean ambientOcclusion,
				double x1, double y1, double z1, double x2, double y2, double z2,
				double x3, double y3, double z3, double x4, double y4, double z4) {
			boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;
			MutableQuad quad = new MutableQuad()
					.setSprite(material.sprite(), ChunkSectionLayer.TRANSLUCENT, Sheets.translucentBlockItemSheet())
					.setDirection(face)
					.setTintIndex(-1)
					.setShade(true)
					.setLightEmission(0)
					.setAmbientOcclusion(ambientOcclusion);
			quad.setPosition(0, (float) x1, (float) y1, (float) z1);
			quad.setPosition(1, reverseWinding ? (float) x4 : (float) x2, reverseWinding ? (float) y4 : (float) y2,
					reverseWinding ? (float) z4 : (float) z2);
			quad.setPosition(2, (float) x3, (float) y3, (float) z3);
			quad.setPosition(3, reverseWinding ? (float) x2 : (float) x4, reverseWinding ? (float) y2 : (float) y4,
					reverseWinding ? (float) z2 : (float) z4);
			quad.setColor(color);
			if (fullSprite) {
				quad.setUvFromSprite(0, 0.0f, 1.0f);
				quad.setUvFromSprite(1, reverseWinding ? 0.0f : 1.0f, reverseWinding ? 0.0f : 1.0f);
				quad.setUvFromSprite(2, 1.0f, 0.0f);
				quad.setUvFromSprite(3, reverseWinding ? 1.0f : 0.0f, reverseWinding ? 1.0f : 0.0f);
			} else {
				for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
					quad.setUvFromSprite(index, OUTLINE_UV, OUTLINE_UV);
				}
			}
			for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
				quad.setNormal(index, face.getStepX(), face.getStepY(), face.getStepZ());
			}
			return quad.toBakedQuad();
		}
	}

	private record OutlinePart(List<BakedQuad> unculled, Map<Direction, List<BakedQuad>> culled, Material.Baked material)
			implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(Direction direction) {
			return direction == null ? unculled : culled.get(direction);
		}

		@Override
		public boolean useAmbientOcclusion() {
			return true;
		}

		@Override
		public Material.Baked particleMaterial() {
			return material;
		}

		@Override
		public int materialFlags() {
			return BakedQuad.FLAG_TRANSLUCENT;
		}
	}

}

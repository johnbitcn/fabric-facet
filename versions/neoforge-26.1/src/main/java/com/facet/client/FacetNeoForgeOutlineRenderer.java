package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * NeoForge's core block-outline renderer. Product features remain loader-local
 * until they receive their own parity and in-world validation.
 */
final class FacetNeoForgeOutlineRenderer {
	private static final float OUTLINE_UV = 0.5f;
	private static final int RAINBOW_COLOR = FacetOutlineRules.withOutlineAlpha(0xFFFFFFFF);
	private static final double GRAFFITI_SURFACE_BIAS = 1.0 / 512.0;
	private static final double GRAFFITI_FACE_SIZE = 0.785;
	private static final double GRAFFITI_FACE_INSET = (1.0 - GRAFFITI_FACE_SIZE) / 2.0;
	private static final AtomicBoolean LOGGED_FIRST_GEOMETRY = new AtomicBoolean();

	private FacetNeoForgeOutlineRenderer() {
	}

	static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
		FacetOutlineColor.clearCache();
		TextureAtlasSprite sprite = event.getTextureGetter().apply(Identifier.withDefaultNamespace("block/white_concrete"));
		Material.Baked material = new Material.Baked(sprite, true);
		Material.Baked rainbowMaterial = new Material.Baked(event.getTextureGetter().apply(
				Identifier.fromNamespaceAndPath("facet", "block/rainbow_outline")), false);
		Material.Baked pastelMaterial = new Material.Baked(event.getTextureGetter().apply(
				Identifier.fromNamespaceAndPath("facet", "block/rainbow_outline_pastel")), false);
		Material.Baked netherrackMaterial = new Material.Baked(event.getTextureGetter().apply(
				Identifier.fromNamespaceAndPath("facet", "block/netherrack_average")), false);
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
			boolean rainbowOutline = FacetOutlineRules.usesRainbowOutline(state);
			if (!rainbowOutline) {
				FacetOutlineColor.analyze(state, model);
			}
			Material.Baked outlineMaterial = state.is(Blocks.POWDER_SNOW) ? pastelMaterial
					: rainbowOutline ? rainbowMaterial : material;
			entry.setValue(new OutlineModel(model, outlineMaterial, graffitiMaterials,
					rainbowOutline, state.is(Blocks.NETHERRACK) ? netherrackMaterial : null));
			wrapped++;
		}

		FacetNeoForgeOutline.LOGGER.info("Wrapped {} Minecraft 26.1 block-state models for the Facet outline renderer", wrapped);
	}

	static final class OutlineModel extends DelegateBlockStateModel {
		private final Material.Baked material;
		private final Map<GraffitiType, Material.Baked> graffitiMaterials;
		private final boolean rainbowOutline;
		private final Material.Baked netherrackMaterial;

		OutlineModel(BlockStateModel delegate, Material.Baked material, Map<GraffitiType, Material.Baked> graffitiMaterials,
				boolean rainbowOutline, Material.Baked netherrackMaterial) {
			super(delegate);
			this.material = material;
			this.graffitiMaterials = graffitiMaterials;
			this.rainbowOutline = rainbowOutline;
			this.netherrackMaterial = netherrackMaterial;
		}

		@Override
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				List<BlockStateModelPart> parts) {
			int firstPart = parts.size();
			super.collectParts(level, pos, state, random, parts);
			if (netherrackMaterial != null && FacetNeoForgeOutlineConfig.enabled()) {
				for (int part = firstPart; part < parts.size(); part++) {
					parts.set(part, new RetexturedPart(parts.get(part), netherrackMaterial));
				}
			}
			boolean outline = FacetNeoForgeOutlineConfig.enabled()
					&& (rainbowOutline || FacetOutlineRules.shouldRender(level, pos, state));
			GraffitiType[] types = GraffitiStore.getTypes(pos, state);
			if (!outline && types == null) {
				return;
			}
			VoxelShape shape = state.getShape(level, pos);
			if (outline) {
				emitOutlineParts(level, pos, state, parts, shape);
			}
			if (types != null) {
				emitGraffitiParts(level, pos, state, parts, shape, types);
			}
		}

		private void emitOutlineParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
				List<BlockStateModelPart> parts, VoxelShape shape) {
			if (shape.isEmpty()) {
				return;
			}

			FacetOutlineColor.FaceColors faceColors = rainbowOutline ? null : FacetOutlineColor.resolve(level, pos, state);
			double width = FacetOutlineRules.edgeWidth(state, FacetNeoForgeOutlineConfig.edgeWidth());
			if (FacetShapeEdges.isFullCube(shape)) {
				parts.add(new FullCubeOutlinePart(faceColors, width));
				return;
			}
			CulledQuads culled = new CulledQuads();
			List<BakedQuad> unculled = new ArrayList<>();
			boolean carpet = state.getBlock() instanceof CarpetBlock;

			FacetShapeEdges.forEachSurfaceStrip(shape, width,
					(face, minX, minY, minZ, maxX, maxY, maxZ) -> {
						if (carpet && face != Direction.UP) {
							return;
						}
						BakedQuad quad = createOutlineQuad(face, rainbowOutline ? RAINBOW_COLOR : faceColors.color(face),
								minX, minY, minZ, maxX, maxY, maxZ);
						if (FacetOutlineRules.touchesBlockBoundary(face, minX, minY, minZ, maxX, maxY, maxZ)) {
							if (culled.byDirection == null) {
								culled.byDirection = new EnumMap<>(Direction.class);
							}
							culled.byDirection.computeIfAbsent(face, unused -> new ArrayList<>()).add(quad);
						} else {
							unculled.add(quad);
						}
					});

			if (unculled.isEmpty() && culled.byDirection == null) {
				return;
			}
			// Cutout outlines are not translucent; materialFlags 0 keeps them off sorted terrain.
			parts.add(new OutlinePart(unculled, culled, material, rainbowOutline ? BakedQuad.FLAG_ANIMATED : 0));
			if (LOGGED_FIRST_GEOMETRY.compareAndSet(false, true)) {
				int quadCount = unculled.size() + (culled.byDirection == null
						? 0
						: culled.byDirection.values().stream().mapToInt(List::size).sum());
				FacetNeoForgeOutline.LOGGER.info("First Facet NeoForge outline geometry contained {} quads", quadCount);
			}
		}

		private void emitGraffitiParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
				List<BlockStateModelPart> parts, VoxelShape shape, GraffitiType[] types) {
			if (shape.isEmpty()) {
				return;
			}

			if (GraffitiEligibility.baseResult(level, pos, state) != GraffitiEligibility.Result.ALLOWED) {
				return;
			}

			Map<Direction, List<BakedQuad>> culled = null;

			for (Direction direction : Direction.values()) {
				GraffitiType type = types[direction.ordinal()];

				if (type == null
						|| GraffitiEligibility.evaluateFace(level, pos, state, direction, shape)
						!= GraffitiEligibility.Result.ALLOWED) {
					continue;
				}

				if (culled == null) {
					culled = new EnumMap<>(Direction.class);

					for (Direction other : Direction.values()) {
						culled.put(other, new ArrayList<>());
					}
				}

				culled.get(direction).add(createGraffitiQuad(direction, GraffitiEligibility.facePlane(shape, direction),
						graffitiMaterials.get(type)));
			}

			if (culled == null) {
				return;
			}
			CulledQuads culledQuads = new CulledQuads();
			culledQuads.byDirection = culled;
			parts.add(new OutlinePart(List.of(), culledQuads, material, BakedQuad.FLAG_TRANSLUCENT));
		}

		/** The terrain renderer requests only visible faces; do not repeat its culling rules. */
		class FullCubeOutlinePart implements BlockStateModelPart {
			private final FacetOutlineColor.FaceColors colors;
			private final double width;
			private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);

			FullCubeOutlinePart(FacetOutlineColor.FaceColors colors, double width) {
				this.colors = colors;
				this.width = width;
			}

			@Override
			public List<BakedQuad> getQuads(Direction face) {
				if (face == null) {
					return List.of();
				}
				return faces.computeIfAbsent(face, this::bakeFace);
			}

			List<BakedQuad> bakeFace(Direction direction) {
				List<BakedQuad> quads = new ArrayList<>(4);
				int color = rainbowOutline ? RAINBOW_COLOR : colors.color(direction);
				FacetShapeEdges.forEachSurfaceStrip(Shapes.block(), width, 63 & ~(1 << direction.ordinal()),
						(stripFace, x1, y1, z1, x2, y2, z2) ->
								quads.add(createOutlineQuad(stripFace, color, x1, y1, z1, x2, y2, z2)));
				if (LOGGED_FIRST_GEOMETRY.compareAndSet(false, true)) {
					FacetNeoForgeOutline.LOGGER.info("First Facet NeoForge outline face contained {} quads", quads.size());
				}
				return List.copyOf(quads);
			}

			int generatedFaceCount() {
				return faces.size();
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
				return rainbowOutline ? BakedQuad.FLAG_ANIMATED : 0;
			}
		}

		private BakedQuad createOutlineQuad(Direction face, int color,
				double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			// NeoForge keeps CUTOUT; slightly larger shared SURFACE_BIAS reduces coplanar z-fight.
			double bias = FacetOutlineRules.SURFACE_BIAS * face.getAxisDirection().getStep();
			return switch (face) {
				case DOWN, UP -> bakeQuad(material, face, color, false, true, true,
						minX, minY + bias, minZ, maxX, minY + bias, minZ,
						maxX, minY + bias, maxZ, minX, minY + bias, maxZ);
				case NORTH, SOUTH -> bakeQuad(material, face, color, false, true, true,
						minX, minY, minZ + bias, maxX, minY, minZ + bias,
						maxX, maxY, minZ + bias, minX, maxY, minZ + bias);
				case WEST, EAST -> bakeQuad(material, face, color, false, true, true,
						minX + bias, minY, minZ, minX + bias, minY, maxZ,
						minX + bias, maxY, maxZ, minX + bias, maxY, minZ);
			};
		}

		private BakedQuad createGraffitiQuad(Direction face, double plane, Material.Baked graffitiMaterial) {
			double biasedPlane = plane + GRAFFITI_SURFACE_BIAS * face.getAxisDirection().getStep();
			double min = GRAFFITI_FACE_INSET;
			double max = 1.0 - GRAFFITI_FACE_INSET;
			return switch (face) {
				case DOWN, UP -> bakeQuad(graffitiMaterial, face, -1, true, false, false,
						min, biasedPlane, min, max, biasedPlane, min, max, biasedPlane, max, min, biasedPlane, max);
				case NORTH, SOUTH -> bakeQuad(graffitiMaterial, face, -1, true, false, false,
						min, min, biasedPlane, max, min, biasedPlane, max, max, biasedPlane, min, max, biasedPlane);
				case WEST, EAST -> bakeQuad(graffitiMaterial, face, -1, true, false, false,
						biasedPlane, min, min, biasedPlane, min, max, biasedPlane, max, max, biasedPlane, max, min);
			};
		}

		private BakedQuad bakeQuad(Material.Baked material, Direction face, int color, boolean fullSprite,
				boolean ambientOcclusion, boolean outlineCutout,
				double x1, double y1, double z1, double x2, double y2, double z2,
				double x3, double y3, double z3, double x4, double y4, double z4) {
			boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;
			MutableQuad quad = FacetNeoForgePlatform.setSprite(new MutableQuad(), material.sprite(), outlineCutout)
					.setDirection(face)
					.setTintIndex(-1)
					.setLightEmission(0)
					.setAmbientOcclusion(ambientOcclusion);
			FacetNeoForgePlatform.setShade(quad, true);
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
					float u = rainbowOutline && outlineCutout
							? Math.clamp((quad.x(index) + quad.y(index) + quad.z(index)) / 3.0f, 0.0f, 1.0f)
							: OUTLINE_UV;
					quad.setUvFromSprite(index, u, OUTLINE_UV);
				}
			}
			for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
				quad.setNormal(index, face.getStepX(), face.getStepY(), face.getStepZ());
			}
			return quad.toBakedQuad();
		}
	}

	/** Retexture only faces requested by terrain rendering; preserve the original geometry and shading. */
	static final class RetexturedPart implements BlockStateModelPart {
		private final BlockStateModelPart delegate;
		private final Material.Baked material;
		private final Map<Direction, List<BakedQuad>> faces = new HashMap<>();

		RetexturedPart(BlockStateModelPart delegate, Material.Baked material) {
			this.delegate = delegate;
			this.material = material;
		}

		@Override
		public List<BakedQuad> getQuads(Direction face) {
			return faces.computeIfAbsent(face, direction -> delegate.getQuads(direction).stream().map(original -> {
				MutableQuad quad = new MutableQuad().setFrom(original)
						.setSprite(material, material.sprite().transparency());
				for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
					quad.setUvFromSprite(vertex, OUTLINE_UV, OUTLINE_UV);
				}
				return quad.toBakedQuad();
			}).toList());
		}

		@Override
		public boolean useAmbientOcclusion() {
			return delegate.useAmbientOcclusion();
		}

		@Override
		public Material.Baked particleMaterial() {
			return material;
		}

		@Override
		public int materialFlags() {
			return delegate.materialFlags();
		}
	}

	private static final class CulledQuads {
		private Map<Direction, List<BakedQuad>> byDirection;
	}

	private record OutlinePart(List<BakedQuad> unculled, CulledQuads culled, Material.Baked material, int materialFlags)
			implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(Direction direction) {
			return direction == null ? unculled
					: culled.byDirection == null ? List.of()
					: culled.byDirection.getOrDefault(direction, List.of());
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
			return materialFlags;
		}
	}

}

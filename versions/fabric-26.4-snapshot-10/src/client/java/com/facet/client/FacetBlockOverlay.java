package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FacetBlockOverlay {
	private static final Logger LOGGER = LoggerFactory.getLogger("Facet");
	private static final double GRAFFITI_SURFACE_BIAS = 1.0 / 512.0;
	private static final double GRAFFITI_FACE_SIZE = 0.785;
	private static final float OUTLINE_UV = 0.5f;
	private static final int RAINBOW_COLOR = FacetOutlineRules.withOutlineAlpha(0xFFFFFFFF);
	private static final int MAX_STRIP_CACHE_ENTRIES = 128;
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Material OUTLINE_TEXTURE = new Material(Identifier.withDefaultNamespace("block/white_concrete"), true);
	private static final ModelDebugName OUTLINE_DEBUG_NAME = () -> "facet:outline";
	private static final Identifier RAINBOW_TEXTURE_ID =
			Identifier.fromNamespaceAndPath("facet", "block/rainbow_outline");
	private static final Material RAINBOW_TEXTURE = new Material(RAINBOW_TEXTURE_ID, false);
	private static final Material PASTEL_RAINBOW_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/rainbow_outline_pastel"), false);
	private static final Material[] BRITTLE_ICE_TEXTURES = {
			new Material(Identifier.withDefaultNamespace("block/brittle_ice_0"), false),
			new Material(Identifier.withDefaultNamespace("block/brittle_ice_1"), false),
			new Material(Identifier.withDefaultNamespace("block/brittle_ice_2"), false),
			new Material(Identifier.withDefaultNamespace("block/brittle_ice_3"), false)
	};
	private static final Material[] BRITTLE_ICE_OUTLINE_TEXTURES = {
			new Material(Identifier.fromNamespaceAndPath("facet", "block/brittle_ice_outline_0"), false),
			new Material(Identifier.fromNamespaceAndPath("facet", "block/brittle_ice_outline_1"), false),
			new Material(Identifier.fromNamespaceAndPath("facet", "block/brittle_ice_outline_2"), false),
			new Material(Identifier.fromNamespaceAndPath("facet", "block/brittle_ice_outline_3"), false)
	};
	private static final Material WARNING_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/warning_outline"), false);
	private static final Material INFESTED_WARNING_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/infested_warning_outline"), false);
	private static final Material SNOWFLAKE_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/rainbow_snowflake"), false);
	private static final Material BRUSH_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/rainbow_brush"), false);
	private static final Material BUG_TEXTURE = new Material(
			Identifier.fromNamespaceAndPath("facet", "block/warning_bug"), false);
	private static final Material NETHERRACK_TEXTURE =
			new Material(Identifier.fromNamespaceAndPath("facet", "block/netherrack_average"), false);

	private static final ThreadLocal<ReloadContext> RELOAD_CONTEXT = new ThreadLocal<>();
	private static final ThreadLocal<BlockQuadOutput> LAYER_OUTPUT = new ThreadLocal<>();
	private static final ConcurrentHashMap<FullCubeStripKey, Map<Direction, List<BakedQuad>>> FULL_CUBE_STRIP_CACHE =
			new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<FaceSymbolKey, Map<Direction, BakedQuad>> FACE_SYMBOL_CACHE =
			new ConcurrentHashMap<>();
	private static volatile Map<GraffitiType, Map<Direction, BakedQuad>> fullCubeGraffitiQuads = Map.of();
	private static volatile boolean initialized;

	private FacetBlockOverlay() {
	}

	static void initialize() {
		initialized = !FacetClient.usesExperimentalLineOutlines();
	}

	public static void beginModelReload(MaterialBaker baker, SpriteLoader.Preparations blockAtlas) {
		if (!initialized) {
			RELOAD_CONTEXT.remove();
			return;
		}
		FacetOutlineColor.clearCache();
		FULL_CUBE_STRIP_CACHE.clear();
		FACE_SYMBOL_CACHE.clear();

		Material.Baked outline = bakeBlockMaterial(baker, blockAtlas, OUTLINE_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked rainbow = bakeBlockMaterial(baker, blockAtlas, RAINBOW_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked pastelRainbow = bakeBlockMaterial(baker, blockAtlas, PASTEL_RAINBOW_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked warning = bakeBlockMaterial(baker, blockAtlas, WARNING_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked infestedWarning = bakeBlockMaterial(baker, blockAtlas, INFESTED_WARNING_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked snowflake = bakeBlockMaterial(baker, blockAtlas, SNOWFLAKE_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked brush = bakeBlockMaterial(baker, blockAtlas, BRUSH_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked bug = bakeBlockMaterial(baker, blockAtlas, BUG_TEXTURE, OUTLINE_DEBUG_NAME);
		Material.Baked netherrack = bakeBlockMaterial(baker, blockAtlas, NETHERRACK_TEXTURE, OUTLINE_DEBUG_NAME);

		Material.Baked[] brittleIceSources = new Material.Baked[BRITTLE_ICE_TEXTURES.length];
		Material.Baked[] brittleIceOutlines = new Material.Baked[BRITTLE_ICE_OUTLINE_TEXTURES.length];
		for (int i = 0; i < BRITTLE_ICE_TEXTURES.length; i++) {
			brittleIceSources[i] = bakeBlockMaterial(baker, blockAtlas, BRITTLE_ICE_TEXTURES[i], OUTLINE_DEBUG_NAME);
			brittleIceOutlines[i] = bakeBlockMaterial(baker, blockAtlas, BRITTLE_ICE_OUTLINE_TEXTURES[i], OUTLINE_DEBUG_NAME);
		}

		Map<GraffitiType, Material.Baked> graffitiMaterials = new EnumMap<>(GraffitiType.class);
		Map<GraffitiType, Map<Direction, BakedQuad>> prebakedGraffiti = new EnumMap<>(GraffitiType.class);
		for (GraffitiType type : GraffitiType.values()) {
			Material texture = new Material(type.materialId(), true);
			ModelDebugName debugName = () -> "facet:graffiti/" + type.id();
			Material.Baked bakedMaterial = bakeBlockMaterial(baker, blockAtlas, texture, debugName);
			graffitiMaterials.put(type, bakedMaterial);
			Map<Direction, BakedQuad> byFace = new EnumMap<>(Direction.class);
			for (Direction face : DIRECTIONS) {
				double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
				byFace.put(face, createFaceDecal(face, plane, bakedMaterial, GRAFFITI_FACE_SIZE, ChunkSectionLayer.TRANSLUCENT));
			}
			prebakedGraffiti.put(type, Map.copyOf(byFace));
		}
		fullCubeGraffitiQuads = Map.copyOf(prebakedGraffiti);

		RELOAD_CONTEXT.set(new ReloadContext(baker, blockAtlas, outline, rainbow, pastelRainbow, warning,
				infestedWarning, snowflake, brush, bug, netherrack, brittleIceSources, brittleIceOutlines,
				Map.copyOf(graffitiMaterials)));
	}

	public static void wrapBlockStateModels(Map<BlockState, BlockStateModel> models) {
		ReloadContext context = RELOAD_CONTEXT.get();
		RELOAD_CONTEXT.remove();
		if (context == null || !initialized) {
			return;
		}

		int wrappedCount = 0;
		int bakedCount = 0;
		for (Map.Entry<BlockState, BlockStateModel> entry : models.entrySet()) {
			BlockState state = entry.getKey();
			BlockStateModel model = entry.getValue();
			if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || model instanceof OutlineBlockStateModel) {
				continue;
			}

			boolean brittleIce = FacetBrittleIce.isBrittleIce(state);
			boolean pastelRainbow = FacetOutlineRules.usesPastelOutline(state);
			boolean rainbowOutline = pastelRainbow || FacetOutlineRules.usesRainbowOutline(state);
			if (!rainbowOutline && !brittleIce) {
				FacetOutlineColor.analyze(state, model);
			}

			boolean warningOutline = FacetOutlineRules.usesWarningOutline(state);
			Material.Baked outlineMaterial = brittleIce ? null
					: FacetOutlineRules.isInfested(state) ? context.infestedWarning
					: warningOutline ? warningMaterial(context)
					: pastelRainbow ? context.pastelRainbow
					: rainbowOutline ? context.rainbow : context.outline;
			Material.Baked symbolMaterial = state.is(Blocks.POWDER_SNOW) ? context.snowflake
					: FacetOutlineRules.usesBrushSymbol(state) ? context.brush
					: FacetOutlineRules.isInfested(state) ? context.bug : null;

			int brittleIceStage = brittleIce ? FacetBrittleIce.brittleIceStage(state) : -1;
			BlockStateModel brittleIceModel = brittleIceStage < 0 ? null
					: createBrittleIceModel(model, context.brittleIceSources[brittleIceStage],
							context.brittleIceOutlines[brittleIceStage]);

			Material.Baked netherrackMaterial = state.is(Blocks.NETHERRACK) ? context.netherrack : null;
			BlockStateModel netherrackModel = netherrackMaterial == null ? null
					: createNetherrackModel(model, netherrackMaterial);
			BlockStateModel bakedModel = brittleIce ? null
					: FacetBakedOutlineModel.create(state, model, netherrackMaterial,
							id -> bakeBlockMaterial(context.baker, context.blockAtlas, new Material(id, false), OUTLINE_DEBUG_NAME),
							FacetBakedOutlineModel::remapQuad);
			if (bakedModel != null) {
				bakedCount++;
			}

			entry.setValue(new OutlineBlockStateModel(model, outlineMaterial, symbolMaterial,
					context.graffitiMaterials, brittleIce, brittleIceModel, netherrackModel,
					rainbowOutline, bakedModel));
			wrappedCount++;
		}
		LOGGER.info("Wrapped {} Minecraft 26.4 Snapshot 10 block-state models ({} pre-baked full-cube models)",
				wrappedCount, bakedCount);
	}

	private static Material.Baked warningMaterial(ReloadContext context) {
		return context.warning;
	}

	public static void pushLayerOutput(BlockQuadOutput layerOutput) {
		LAYER_OUTPUT.set(layerOutput);
	}

	public static void popLayerOutput() {
		LAYER_OUTPUT.remove();
	}

	public static BlockQuadOutput overlayOutput(BlockQuadOutput fallback) {
		BlockQuadOutput layerOutput = LAYER_OUTPUT.get();
		return layerOutput != null ? layerOutput : fallback;
	}

	private static Material.Baked bakeBlockMaterial(MaterialBaker baker, SpriteLoader.Preparations blockAtlas,
			Material material, ModelDebugName debugName) {
		TextureAtlasSprite sprite = blockAtlas.getSprite(material.sprite());
		if (sprite != null) {
			return new Material.Baked(sprite, material.forceTranslucent());
		}
		return baker.get(material, debugName);
	}

	private static BlockStateModel createBrittleIceModel(BlockStateModel original,
			Material.Baked sourceMaterial, Material.Baked outlineMaterial) {
		TextureAtlasSprite sourceSprite = sourceMaterial.sprite();
		float sourceU0 = sourceSprite.getU0();
		float sourceV0 = sourceSprite.getV0();
		float inverseSourceU = 1.0f / (sourceSprite.getU1() - sourceU0);
		float inverseSourceV = 1.0f / (sourceSprite.getV1() - sourceV0);
		TextureAtlasSprite targetSprite = outlineMaterial.sprite();
		return new RetexturedBlockStateModel(original, outlineMaterial, (quad, target) -> {
			long[] uvs = new long[BakedQuad.VERTEX_COUNT];
			for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
				float u = (UVPair.unpackU(quad.packedUV(vertex)) - sourceU0) * inverseSourceU;
				float v = (UVPair.unpackV(quad.packedUV(vertex)) - sourceV0) * inverseSourceV;
				uvs[vertex] = UVPair.pack(targetSprite.getU(u), targetSprite.getV(v));
			}
			return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
					uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(),
					FacetBakedOutlinePlatform.remapMaterial(quad, target));
		});
	}

	private static BlockStateModel createNetherrackModel(BlockStateModel original, Material.Baked netherrackMaterial) {
		TextureAtlasSprite targetSprite = netherrackMaterial.sprite();
		long centerUv = UVPair.pack(targetSprite.getU(OUTLINE_UV), targetSprite.getV(OUTLINE_UV));
		return new RetexturedBlockStateModel(original, netherrackMaterial, (quad, target) ->
				new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
						centerUv, centerUv, centerUv, centerUv, quad.direction(),
						FacetBakedOutlinePlatform.remapMaterial(quad, target)));
	}

	@FunctionalInterface
	public interface OverlayQuadConsumer {
		void accept(BakedQuad quad, int color, boolean useAmbientOcclusion);
	}

	public static final class OutlineBlockStateModel implements BlockStateModel {
		private final BlockStateModel wrapped;
		private final Material.Baked outlineMaterial;
		private final Material.Baked symbolMaterial;
		private final Map<GraffitiType, Material.Baked> graffitiMaterials;
		private final boolean brittleIce;
		private final BlockStateModel brittleIceModel;
		private final BlockStateModel netherrackModel;
		private final boolean rainbowOutline;
		private final BlockStateModel bakedModel;
		private final Boolean staticAmbientOcclusion;

		private OutlineBlockStateModel(BlockStateModel wrapped, Material.Baked outlineMaterial,
				Material.Baked symbolMaterial, Map<GraffitiType, Material.Baked> graffitiMaterials,
				boolean brittleIce, BlockStateModel brittleIceModel, BlockStateModel netherrackModel,
				boolean rainbowOutline, BlockStateModel bakedModel) {
			this.wrapped = wrapped;
			this.outlineMaterial = outlineMaterial;
			this.symbolMaterial = symbolMaterial;
			this.graffitiMaterials = graffitiMaterials;
			this.brittleIce = brittleIce;
			this.brittleIceModel = brittleIceModel;
			this.netherrackModel = netherrackModel;
			this.rainbowOutline = rainbowOutline;
			this.bakedModel = bakedModel;
			if (wrapped.getClass() == SingleVariant.class) {
				List<BlockStateModelPart> parts = new ArrayList<>(1);
				wrapped.collectParts(RandomSource.create(0L), parts);
				this.staticAmbientOcclusion = parts.isEmpty() ? Boolean.TRUE : parts.getFirst().useAmbientOcclusion();
			} else {
				this.staticAmbientOcclusion = null;
			}
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			wrapped.collectParts(random, parts);
		}

		@Override
		public Material.Baked particleMaterial() {
			return wrapped.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return wrapped.materialFlags();
		}

		@Override
		public boolean hasMaterialFlag(int flag) {
			return wrapped.hasMaterialFlag(flag);
		}

		public boolean brittleIce() {
			return brittleIce;
		}

		public boolean rainbowOutline() {
			return rainbowOutline;
		}

		public BlockStateModel selectBrittleIceModel(BlockState state) {
			return brittleIceModel != null && FacetBrittleIce.shouldUseBrittleIceOutline(state, FacetConfig.enabled())
					? brittleIceModel
					: wrapped;
		}

		public BlockStateModel selectBakedModel(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			if (!brittleIce && bakedModel != null && FacetConfig.enabled()
					&& FacetBakedOutlineTextures.matchesWidth(FacetConfig.effectiveEdgeWidth())
					&& FacetOutlineRules.shouldRender(level, pos, state)) {
				if (FacetOutlineStats.enabled()) {
					FacetOutlineStats.BAKED_BLOCKS_SERVED.incrementAndGet();
				}
				return bakedModel;
			}
			return null;
		}

		public BlockStateModel selectFallbackBaseModel() {
			return netherrackModel != null && FacetConfig.enabled() ? netherrackModel : wrapped;
		}

		public boolean shouldEmitOutline(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			return FacetConfig.enabled() && (rainbowOutline || FacetOutlineRules.shouldRender(level, pos, state));
		}

		public boolean hasGraffiti(BlockPos pos, BlockState state) {
			return GraffitiStore.getTypes(pos, state) != null;
		}

		public boolean useAmbientOcclusion(long seed) {
			if (staticAmbientOcclusion != null) {
				return staticAmbientOcclusion;
			}
			List<BlockStateModelPart> parts = new ArrayList<>(2);
			wrapped.collectParts(RandomSource.create(seed), parts);
			return parts.isEmpty() || parts.getFirst().useAmbientOcclusion();
		}

		public void emitOverlays(BlockAndTintGetter level, BlockPos pos, BlockState state,
				int culledFaces, boolean emitOutline, OverlayQuadConsumer consumer) {
			VoxelShape shape = null;
			if (emitOutline) {
				shape = state.getShape(level, pos);
				emitOutlineQuads(level, pos, state, culledFaces, shape, consumer);
			}
			GraffitiType[] graffitiTypes = level instanceof MovingBlockRenderState
					? null
					: GraffitiStore.getTypes(pos, state);
			if (graffitiTypes != null) {
				if (shape == null) {
					shape = state.getShape(level, pos);
				}
				emitGraffitiQuads(level, pos, state, culledFaces, shape, graffitiTypes, consumer);
			}
		}

		private void emitOutlineQuads(BlockAndTintGetter level, BlockPos pos, BlockState state,
				int culledFaces, VoxelShape shape, OverlayQuadConsumer consumer) {
			if (shape.isEmpty() || outlineMaterial == null) {
				return;
			}

			if (symbolMaterial != null) {
				Map<Direction, BakedQuad> symbols = faceSymbolQuads(symbolMaterial, FacetOutlineRules.faceSymbolSize(state));
				for (Direction face : DIRECTIONS) {
					if ((culledFaces & (1 << face.ordinal())) == 0) {
						consumer.accept(symbols.get(face), -1, false);
					}
				}
			}

			FacetOutlineColor.FaceColors faceColors = rainbowOutline ? null : FacetOutlineColor.resolve(level, pos, state);
			double width = rainbowOutline ? FacetOutlineRules.ANCIENT_DEBRIS_EDGE_WIDTH : FacetConfig.effectiveEdgeWidth();
			boolean isCarpet = state.getBlock() instanceof CarpetBlock;
			boolean stats = FacetOutlineStats.enabled();

			if (!isCarpet && FacetShapeEdges.isFullCube(shape)) {
				Map<Direction, List<BakedQuad>> stripsByFace = fullCubeStripQuads(outlineMaterial, rainbowOutline, width);
				for (Direction face : DIRECTIONS) {
					List<BakedQuad> quads = stripsByFace.get(face);
					if (stats) {
						FacetOutlineStats.FULL_CUBE_STRIPS_SERVED.addAndGet(quads.size());
					}
					if ((culledFaces & (1 << face.ordinal())) != 0) {
						if (stats) {
							FacetOutlineStats.STRIPS_SKIPPED_CULLED.addAndGet(quads.size());
						}
						continue;
					}
					if (stats) {
						FacetOutlineStats.STRIPS_EMITTED.addAndGet(quads.size());
					}
					int color = rainbowOutline ? RAINBOW_COLOR : faceColors.color(face);
					for (BakedQuad quad : quads) {
						consumer.accept(quad, color, true);
					}
				}
				return;
			}

			FacetShapeEdges.forEachSurfaceStrip(shape, width, culledFaces,
					(direction, minX, minY, minZ, maxX, maxY, maxZ) -> {
						if (isCarpet && direction != Direction.UP) {
							if (stats) {
								FacetOutlineStats.STRIPS_SKIPPED_CARPET.incrementAndGet();
							}
							return;
						}
						if (stats) {
							FacetOutlineStats.STRIPS_EMITTED.incrementAndGet();
						}
						int color = rainbowOutline ? RAINBOW_COLOR : faceColors.color(direction);
						BakedQuad quad = createSurfaceStripQuad(outlineMaterial, rainbowOutline, direction,
								minX, minY, minZ, maxX, maxY, maxZ);
						consumer.accept(quad, color, true);
					});
		}

		private void emitGraffitiQuads(BlockAndTintGetter level, BlockPos pos, BlockState state,
				int culledFaces, VoxelShape shape, GraffitiType[] types, OverlayQuadConsumer consumer) {
			if (shape.isEmpty() || GraffitiEligibility.baseResult(level, pos, state) != GraffitiEligibility.Result.ALLOWED) {
				return;
			}

			boolean fullCube = FacetShapeEdges.isFullCube(shape);
			Map<GraffitiType, Map<Direction, BakedQuad>> prebaked = fullCube ? fullCubeGraffitiQuads : Map.of();

			for (Direction direction : DIRECTIONS) {
				GraffitiType type = types[direction.ordinal()];
				if (type == null || (culledFaces & (1 << direction.ordinal())) != 0
						|| GraffitiEligibility.evaluateFace(level, pos, state, direction, shape)
						!= GraffitiEligibility.Result.ALLOWED) {
					continue;
				}

				Map<Direction, BakedQuad> byFace = prebaked.get(type);
				BakedQuad quad = byFace != null ? byFace.get(direction) : null;
				if (quad == null) {
					double plane = GraffitiEligibility.facePlane(shape, direction);
					quad = createFaceDecal(direction, plane, graffitiMaterials.get(type),
							GRAFFITI_FACE_SIZE, ChunkSectionLayer.TRANSLUCENT);
				}
				consumer.accept(quad, -1, false);
			}
		}
	}

	private static Map<Direction, BakedQuad> faceSymbolQuads(Material.Baked symbolMaterial, double size) {
		FaceSymbolKey key = new FaceSymbolKey(symbolMaterial, Double.doubleToLongBits(size));
		return FACE_SYMBOL_CACHE.computeIfAbsent(key, unused -> {
			Map<Direction, BakedQuad> byFace = new EnumMap<>(Direction.class);
			for (Direction face : DIRECTIONS) {
				double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
				byFace.put(face, createFaceDecal(face, plane, symbolMaterial, size, ChunkSectionLayer.TRANSLUCENT));
			}
			return Map.copyOf(byFace);
		});
	}

	private static Map<Direction, List<BakedQuad>> fullCubeStripQuads(Material.Baked outlineMaterial,
			boolean rainbowOutline, double width) {
		FullCubeStripKey key = new FullCubeStripKey(outlineMaterial, rainbowOutline, Double.doubleToLongBits(width));
		Map<Direction, List<BakedQuad>> cached = FULL_CUBE_STRIP_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		Map<Direction, List<BakedQuad>> built = buildFullCubeStripQuads(outlineMaterial, rainbowOutline, width);
		if (FULL_CUBE_STRIP_CACHE.size() < MAX_STRIP_CACHE_ENTRIES) {
			Map<Direction, List<BakedQuad>> raced = FULL_CUBE_STRIP_CACHE.putIfAbsent(key, built);
			return raced != null ? raced : built;
		}
		return built;
	}

	private static Map<Direction, List<BakedQuad>> buildFullCubeStripQuads(Material.Baked outlineMaterial,
			boolean rainbowOutline, double width) {
		Map<Direction, List<BakedQuad>> byFace = new EnumMap<>(Direction.class);
		for (Direction face : DIRECTIONS) {
			List<BakedQuad> quads = new ArrayList<>(4);
			FacetShapeEdges.forEachSurfaceStrip(Shapes.block(), width, 63 & ~(1 << face.ordinal()),
					(stripFace, minX, minY, minZ, maxX, maxY, maxZ) ->
							quads.add(createSurfaceStripQuad(outlineMaterial, rainbowOutline, stripFace,
									minX, minY, minZ, maxX, maxY, maxZ)));
			byFace.put(face, List.copyOf(quads));
		}
		return Map.copyOf(byFace);
	}

	private static BakedQuad createFaceDecal(Direction face, double plane, Material.Baked material,
			double size, ChunkSectionLayer layer) {
		double bias = layer == ChunkSectionLayer.TRANSLUCENT
				? GRAFFITI_SURFACE_BIAS : FacetMcBridge.outlineSurfaceBias();
		double biasedPlane = plane + bias * face.getAxisDirection().getStep();
		double min = (1.0 - size) / 2.0;
		double max = 1.0 - min;

		return switch (face) {
			case DOWN, UP -> createQuad(face, material, layer, true, false,
					min, biasedPlane, min, max, biasedPlane, min, max, biasedPlane, max, min, biasedPlane, max);
			case NORTH, SOUTH -> createQuad(face, material, layer, true, false,
					min, min, biasedPlane, max, min, biasedPlane, max, max, biasedPlane, min, max, biasedPlane);
			case WEST, EAST -> createQuad(face, material, layer, true, false,
					biasedPlane, min, min, biasedPlane, min, max, biasedPlane, max, max, biasedPlane, max, min);
		};
	}

	private static BakedQuad createSurfaceStripQuad(Material.Baked material, boolean rainbowOutline,
			Direction face, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double bias = FacetMcBridge.outlineSurfaceBias() * face.getAxisDirection().getStep();
		ChunkSectionLayer layer = FacetMcBridge.outlineChunkLayer();
		return switch (face) {
			case DOWN, UP -> createQuad(face, material, layer, false, rainbowOutline,
					minX, minY + bias, minZ, maxX, minY + bias, minZ,
					maxX, minY + bias, maxZ, minX, minY + bias, maxZ);
			case NORTH, SOUTH -> createQuad(face, material, layer, false, rainbowOutline,
					minX, minY, minZ + bias, maxX, minY, minZ + bias,
					maxX, maxY, minZ + bias, minX, maxY, minZ + bias);
			case WEST, EAST -> createQuad(face, material, layer, false, rainbowOutline,
					minX + bias, minY, minZ, minX + bias, minY, maxZ,
					minX + bias, maxY, maxZ, minX + bias, maxY, minZ);
		};
	}

	private static BakedQuad createQuad(Direction face, Material.Baked material, ChunkSectionLayer layer,
			boolean fullSprite, boolean rainbowOutline,
			double x1, double y1, double z1,
			double x2, double y2, double z2,
			double x3, double y3, double z3,
			double x4, double y4, double z4) {
		boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;
		Vector3f p0 = new Vector3f((float) x1, (float) y1, (float) z1);
		Vector3f p1 = reverseWinding
				? new Vector3f((float) x4, (float) y4, (float) z4)
				: new Vector3f((float) x2, (float) y2, (float) z2);
		Vector3f p2 = new Vector3f((float) x3, (float) y3, (float) z3);
		Vector3f p3 = reverseWinding
				? new Vector3f((float) x2, (float) y2, (float) z2)
				: new Vector3f((float) x4, (float) y4, (float) z4);
		Vector3f[] positions = { p0, p1, p2, p3 };

		TextureAtlasSprite sprite = material.sprite();
		long uv0;
		long uv1;
		long uv2;
		long uv3;
		if (fullSprite) {
			uv0 = UVPair.pack(sprite.getU(0.0f), sprite.getV(1.0f));
			uv1 = UVPair.pack(sprite.getU(reverseWinding ? 0.0f : 1.0f), sprite.getV(reverseWinding ? 0.0f : 1.0f));
			uv2 = UVPair.pack(sprite.getU(1.0f), sprite.getV(0.0f));
			uv3 = UVPair.pack(sprite.getU(reverseWinding ? 1.0f : 0.0f), sprite.getV(reverseWinding ? 1.0f : 0.0f));
		} else {
			long[] uvs = new long[BakedQuad.VERTEX_COUNT];
			float v = sprite.getV(OUTLINE_UV);
			for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
				float localU = rainbowOutline
						? Math.clamp((positions[vertex].x + positions[vertex].y + positions[vertex].z) / 3.0f, 0.0f, 1.0f)
						: OUTLINE_UV;
				uvs[vertex] = UVPair.pack(sprite.getU(localU), v);
			}
			uv0 = uvs[0];
			uv1 = uvs[1];
			uv2 = uvs[2];
			uv3 = uvs[3];
		}

		boolean translucent = layer == ChunkSectionLayer.TRANSLUCENT;
		BakedQuad.MaterialInfo materialInfo = new BakedQuad.MaterialInfo(
				sprite,
				layer,
				translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
				translucent ? Sheets.translucentBlockItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet(),
				translucent ? Sheets.translucentBlockItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet(),
				-1,
				null,
				0);
		return new BakedQuad(p0, p1, p2, p3, uv0, uv1, uv2, uv3, face, materialInfo);
	}

	private static final class RetexturedBlockStateModel implements BlockStateModel {
		private final BlockStateModel delegate;
		private final Material.Baked material;
		private final BiFunction<BakedQuad, Material.Baked, BakedQuad> quadMapper;
		private final ConcurrentHashMap<BlockStateModelPart, BlockStateModelPart> partCache = new ConcurrentHashMap<>();

		private RetexturedBlockStateModel(BlockStateModel delegate, Material.Baked material,
				BiFunction<BakedQuad, Material.Baked, BakedQuad> quadMapper) {
			this.delegate = delegate;
			this.material = material;
			this.quadMapper = quadMapper;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			int start = parts.size();
			delegate.collectParts(random, parts);
			for (int i = start; i < parts.size(); i++) {
				parts.set(i, partCache.computeIfAbsent(parts.get(i),
						part -> new RetexturedPart(part, material, quadMapper)));
			}
		}

		@Override
		public Material.Baked particleMaterial() {
			return material;
		}

		@Override
		public int materialFlags() {
			return delegate.materialFlags();
		}

		@Override
		public boolean hasMaterialFlag(int flag) {
			return delegate.hasMaterialFlag(flag);
		}
	}

	private static final class RetexturedPart implements FacetAmbientOcclusionPart {
		private final BlockStateModelPart delegate;
		private final Material.Baked material;
		private final BiFunction<BakedQuad, Material.Baked, BakedQuad> quadMapper;
		private final ConcurrentHashMap<Direction, List<BakedQuad>> culled = new ConcurrentHashMap<>();
		private volatile List<BakedQuad> unculled;

		private RetexturedPart(BlockStateModelPart delegate, Material.Baked material,
				BiFunction<BakedQuad, Material.Baked, BakedQuad> quadMapper) {
			this.delegate = delegate;
			this.material = material;
			this.quadMapper = quadMapper;
		}

		@Override
		public List<BakedQuad> getQuads(Direction face) {
			if (face == null) {
				List<BakedQuad> cached = unculled;
				if (cached == null) {
					cached = remapQuads(delegate.getQuads(null));
					unculled = cached;
				}
				return cached;
			}
			return culled.computeIfAbsent(face, direction -> remapQuads(delegate.getQuads(direction)));
		}

		private List<BakedQuad> remapQuads(List<BakedQuad> originals) {
			if (originals.isEmpty()) {
				return List.of();
			}
			List<BakedQuad> mapped = new ArrayList<>(originals.size());
			for (BakedQuad quad : originals) {
				mapped.add(quadMapper.apply(quad, material));
			}
			return List.copyOf(mapped);
		}

		@Override
		public BlockStateModelPart original() {
			return delegate;
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

	private record ReloadContext(
			MaterialBaker baker,
			SpriteLoader.Preparations blockAtlas,
			Material.Baked outline,
			Material.Baked rainbow,
			Material.Baked pastelRainbow,
			Material.Baked warning,
			Material.Baked infestedWarning,
			Material.Baked snowflake,
			Material.Baked brush,
			Material.Baked bug,
			Material.Baked netherrack,
			Material.Baked[] brittleIceSources,
			Material.Baked[] brittleIceOutlines,
			Map<GraffitiType, Material.Baked> graffitiMaterials) {
	}

	private record FullCubeStripKey(Material.Baked material, boolean rainbow, long widthBits) {
	}

	private record FaceSymbolKey(Material.Baked material, long sizeBits) {
	}
}

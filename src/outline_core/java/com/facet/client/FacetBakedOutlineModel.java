package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.facet.client.mixin.WeightedVariantsAccessor;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/** Only vanilla, untinted, single-layer full-cube models can opt out of geometry. */
final class FacetBakedOutlineModel {
	private static final float EPSILON = 1.0e-4f;

	private FacetBakedOutlineModel() {
	}

	static BlockStateModel create(BlockState state, BlockStateModel model, Material.Baked replacement,
			Function<Identifier, Material.Baked> materials, QuadMapper mapper) {
		if ((model.getClass() != SingleVariant.class && model.getClass() != WeightedVariants.class)
				|| !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
				|| FacetOutlineRules.usesRainbowOutline(state) || !FacetOutlineRules.shouldAnalyze(state)
				|| !FacetShapeEdges.isFullCube(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
			return null;
		}
		return bakeModel(model, replacement, materials, mapper);
	}

	private static BlockStateModel bakeModel(BlockStateModel model, Material.Baked replacement,
			Function<Identifier, Material.Baked> materials, QuadMapper mapper) {
		if (model.getClass() == SingleVariant.class) {
			List<BlockStateModelPart> parts = new ArrayList<>();
			((SingleVariant) model).collectParts(RandomSource.create(0), parts);
			if (parts.size() != 1 || !parts.getFirst().getClass().getName().startsWith("net.minecraft.")) {
				return null;
			}
			BlockStateModelPart baked = bakePart(parts.getFirst(), replacement, materials, mapper);
			return baked == null ? null : new SingleVariant(baked);
		}
		if (model.getClass() == WeightedVariants.class && model instanceof WeightedVariantsAccessor accessor) {
			return bakeVariants(accessor.facet$getVariants(), replacement, materials, mapper);
		}
		// Multipart, custom, position-dependent and Fabric-specific models retain the old path.
		return null;
	}

	static BlockStateModel bakeVariants(WeightedList<BlockStateModel> variants, Material.Baked replacement,
			Function<Identifier, Material.Baked> materials, QuadMapper mapper) {
		List<Weighted<BlockStateModel>> mapped = new ArrayList<>();
		for (Weighted<BlockStateModel> entry : variants.unwrap()) {
			BlockStateModel baked = bakeModel(entry.value(), replacement, materials, mapper);
			if (baked == null) {
				return null; // Never drop a random variant's outline.
			}
			mapped.add(new Weighted<>(baked, entry.weight()));
		}
		return new WeightedVariants(WeightedList.of(mapped));
	}

	static BlockStateModelPart bakePart(BlockStateModelPart part, Material.Baked replacement,
			Function<Identifier, Material.Baked> materials, QuadMapper mapper) {
		if (!part.getQuads(null).isEmpty()) {
			return null;
		}
		Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
		for (Direction face : Direction.values()) {
			List<BakedQuad> quads = part.getQuads(face);
			if (quads.size() != 1 || !safeQuad(quads.getFirst(), face)) {
				return null;
			}
			BakedQuad original = quads.getFirst();
			Identifier source = replacement == null ? original.materialInfo().sprite().contents().name()
					: replacement.sprite().contents().name();
			if (!FacetBakedOutlineTextures.available(source)) {
				return null;
			}
			Material.Baked target = materials.apply(FacetBakedOutlineTextures.variantId(source));
			if (target == null || !target.sprite().contents().name().equals(FacetBakedOutlineTextures.variantId(source))
					|| !target.sprite().transparency().isOpaque()) {
				return null;
			}
			faces.put(face, List.of(mapper.map(original, target)));
		}
		// These quads are baked once, not allocated again for every block/section rebuild.
		return new BakedPart(part, Map.copyOf(faces));
	}

	static boolean safeQuad(BakedQuad quad, Direction face) {
		var info = quad.materialInfo();
		if (quad.direction() != face || info.tintIndex() != -1 || info.lightEmission() != 0
				|| !FacetBakedOutlinePlatform.defaultShade(info) || info.layer() != ChunkSectionLayer.SOLID
				|| !info.sprite().transparency().isOpaque() || !FacetBakedOutlinePlatform.safeExtraData(quad)) {
			return false;
		}
		int corners = 0;
		int uvCorners = 0;
		for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
			Vector3fc pos = quad.position(vertex);
			int x = endpoint(pos.x()), y = endpoint(pos.y()), z = endpoint(pos.z());
			if (x < 0 || y < 0 || z < 0) {
				return false;
			}
			int plane = switch (face.getAxis()) {
				case X -> x;
				case Y -> y;
				case Z -> z;
			};
			if (plane != (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) {
				return false;
			}
			corners |= 1 << (x * 4 + y * 2 + z);
			int u = endpoint(normalizedU(quad, vertex)), v = endpoint(normalizedV(quad, vertex));
			if (u < 0 || v < 0) {
				return false;
			}
			uvCorners |= 1 << (u * 2 + v);
		}
		return Integer.bitCount(corners) == 4 && uvCorners == 15;
	}

	private static int endpoint(float value) {
		return Math.abs(value) < EPSILON ? 0 : Math.abs(value - 1) < EPSILON ? 1 : -1;
	}

	static float normalizedU(BakedQuad quad, int vertex) {
		var sprite = quad.materialInfo().sprite();
		return (UVPair.unpackU(quad.packedUV(vertex)) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
	}

	static float normalizedV(BakedQuad quad, int vertex) {
		var sprite = quad.materialInfo().sprite();
		return (UVPair.unpackV(quad.packedUV(vertex)) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
	}

	static BakedQuad remapQuad(BakedQuad original, Material.Baked target) {
		long[] uvs = new long[BakedQuad.VERTEX_COUNT];
		for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
			uvs[vertex] = UVPair.pack(target.sprite().getU(normalizedU(original, vertex)),
					target.sprite().getV(normalizedV(original, vertex)));
		}
		return new BakedQuad(original.position0(), original.position1(), original.position2(), original.position3(),
				uvs[0], uvs[1], uvs[2], uvs[3], original.direction(),
				FacetBakedOutlinePlatform.remapMaterial(original, target));
	}

	@FunctionalInterface
	interface QuadMapper {
		BakedQuad map(BakedQuad original, Material.Baked target);
	}

	private record BakedPart(BlockStateModelPart original, Map<Direction, List<BakedQuad>> faces)
			implements FacetAmbientOcclusionPart {
		@Override
		public List<BakedQuad> getQuads(Direction face) {
			return face == null ? List.of() : faces.get(face);
		}


		@Override
		public Material.Baked particleMaterial() {
			return original.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return original.materialFlags();
		}
	}
}

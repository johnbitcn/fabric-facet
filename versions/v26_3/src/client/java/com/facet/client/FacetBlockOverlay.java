package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.vertex.QuadInstance;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

public final class FacetBlockOverlay {
	private static final double MIN_FACE_SIZE = 0.01;
	private static final double SURFACE_BIAS = 1.0 / 1024.0;
	private static final double GRAFFITI_SURFACE_BIAS = 1.0 / 512.0;
	private static final double GRAFFITI_FACE_SIZE = 0.785;
	private static final double GRAFFITI_FACE_INSET = (1.0 - GRAFFITI_FACE_SIZE) / 2.0;
	private static final float OUTLINE_UV = 0.5f;
	private static final Identifier RELOADER_ID = Identifier.fromNamespaceAndPath("facet", "block_overlay_materials");
	private static final Identifier OUTLINE_MATERIAL_ID = Identifier.withDefaultNamespace("block/white_concrete");
	private static final ThreadLocal<IdentityHashMap<BakedQuad, Integer>> OUTLINE_COLORS =
			ThreadLocal.withInitial(IdentityHashMap::new);
	private static volatile Materials materials;

	private FacetBlockOverlay() {
	}

	static void initialize() {
		ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
		loader.registerReloadListener(RELOADER_ID, new SimpleReloadListener<Void>() {
			@Override
			protected Void prepare(PreparableReloadListener.SharedState sharedState) {
				return null;
			}

			@Override
			protected void apply(Void prepared, PreparableReloadListener.SharedState sharedState) {
				materials = null;
			}
		});
		loader.addListenerOrdering(ResourceReloaderKeys.Client.ATLAS, RELOADER_ID);
	}

	public static void beginBlock() {
		OUTLINE_COLORS.get().clear();
	}

	public static void finishBlock() {
		OUTLINE_COLORS.get().clear();
	}

	public static void applyOutlineColor(BakedQuad quad, QuadInstance instance) {
		Integer color = OUTLINE_COLORS.get().get(quad);

		if (color != null) {
			instance.setColor(color);
		}
	}

	public static void appendParts(List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (FacetClient.usesExperimentalLineOutlines()) {
			return;
		}

		Materials resolved = materials();
		EnumMap<Direction, List<BakedQuad>> directional = new EnumMap<>(Direction.class);
		List<BakedQuad> unculled = new ArrayList<>();

		for (Direction direction : Direction.values()) {
			directional.put(direction, new ArrayList<>());
		}

		appendOutlineQuads(directional, unculled, level, pos, state, resolved.outline());
		appendGraffitiQuads(directional, level, pos, state, resolved.graffiti());

		if (!unculled.isEmpty() || directional.values().stream().anyMatch(list -> !list.isEmpty())) {
			parts.add(new FacetPart(directional, unculled, resolved.outline()));
		}
	}

	private static Materials materials() {
		Materials current = materials;

		if (current != null) {
			return current;
		}

		synchronized (FacetBlockOverlay.class) {
			current = materials;

			if (current == null) {
				Minecraft minecraft = Minecraft.getInstance();
				Material.Baked outline = bakedMaterial(minecraft, OUTLINE_MATERIAL_ID);
				EnumMap<GraffitiType, Material.Baked> graffiti = new EnumMap<>(GraffitiType.class);

				for (GraffitiType type : GraffitiType.values()) {
					graffiti.put(type, bakedMaterial(minecraft, type.materialId()));
				}

				FacetConfig.setTextureResolution(outline.sprite().contents().width());
				current = new Materials(outline, graffiti);
				materials = current;
			}
		}

		return current;
	}

	private static Material.Baked bakedMaterial(Minecraft minecraft, Identifier materialId) {
		return new Material.Baked(
				minecraft.getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, materialId)),
				true);
	}

	private static void appendOutlineQuads(Map<Direction, List<BakedQuad>> directional, List<BakedQuad> unculled,
			BlockAndTintGetter level, BlockPos pos, BlockState state, Material.Baked material) {
		if (!FacetConfig.enabled() || !FacetClient.shouldRenderOutline(level, pos, state)) {
			return;
		}

		VoxelShape shape = state.getShape(level, pos);

		if (shape.isEmpty()) {
			return;
		}

		AABB bounds = shape.bounds();
		int color = FacetClient.outlineColor(level, pos, state);

		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

			for (Direction direction : Direction.values()) {
				boolean isCarpet = state.getBlock() instanceof CarpetBlock;
				boolean isCarpetTop = isCarpet && direction == Direction.UP;

				if ((isCarpet && !isCarpetTop) || !FacetClient.touchesExteriorFace(box, bounds, direction)) {
					continue;
				}

				List<BakedQuad> output = isCarpetTop ? unculled : directional.get(direction);
				emitFaceBorder(output, box, direction, color, material);
			}
		});
	}

	private static void appendGraffitiQuads(Map<Direction, List<BakedQuad>> directional,
			BlockAndTintGetter level, BlockPos pos, BlockState state, Map<GraffitiType, Material.Baked> graffitiMaterials) {
		VoxelShape shape = state.getShape(level, pos);

		if (shape.isEmpty()) {
			return;
		}

		for (Direction direction : Direction.values()) {
			GraffitiType type = GraffitiStore.getType(pos, direction);

			if (type == null
					|| GraffitiEligibility.evaluate(level, pos, state, direction) != GraffitiEligibility.Result.ALLOWED) {
				continue;
			}

			emitGraffitiFace(directional.get(direction), direction,
					GraffitiEligibility.facePlane(shape, direction), graffitiMaterials.get(type));
		}
	}

	private static void emitGraffitiFace(List<BakedQuad> output, Direction face, double plane, Material.Baked material) {
		double biasedPlane = plane + GRAFFITI_SURFACE_BIAS * face.getAxisDirection().getStep();
		double min = GRAFFITI_FACE_INSET;
		double max = 1.0 - GRAFFITI_FACE_INSET;

		switch (face) {
			case DOWN, UP -> output.add(createQuad(face, material, false, 0,
					min, biasedPlane, min, max, biasedPlane, min, max, biasedPlane, max, min, biasedPlane, max,
					0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
			case NORTH, SOUTH -> output.add(createQuad(face, material, false, 0,
					min, min, biasedPlane, max, min, biasedPlane, max, max, biasedPlane, min, max, biasedPlane,
					0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
			case WEST, EAST -> output.add(createQuad(face, material, false, 0,
					biasedPlane, min, min, biasedPlane, min, max, biasedPlane, max, max, biasedPlane, max, min,
					0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
		}
	}

	private static void emitFaceBorder(List<BakedQuad> output, AABB box, Direction direction, int color, Material.Baked material) {
		switch (direction) {
			case DOWN -> emitHorizontalFaceBorder(output, box, box.minY - SURFACE_BIAS, Direction.DOWN, color, material);
			case UP -> emitHorizontalFaceBorder(output, box, box.maxY + SURFACE_BIAS, Direction.UP, color, material);
			case NORTH -> emitZFaceBorder(output, box, box.minZ - SURFACE_BIAS, Direction.NORTH, color, material);
			case SOUTH -> emitZFaceBorder(output, box, box.maxZ + SURFACE_BIAS, Direction.SOUTH, color, material);
			case WEST -> emitXFaceBorder(output, box, box.minX - SURFACE_BIAS, Direction.WEST, color, material);
			case EAST -> emitXFaceBorder(output, box, box.maxX + SURFACE_BIAS, Direction.EAST, color, material);
		}
	}

	private static void emitHorizontalFaceBorder(List<BakedQuad> output, AABB box, double y, Direction face, int color, Material.Baked material) {
		if (box.maxX - box.minX < MIN_FACE_SIZE || box.maxZ - box.minZ < MIN_FACE_SIZE) {
			return;
		}

		double widthX = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxX - box.minX);
		double widthZ = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxZ - box.minZ);
		emitOutlineQuad(output, face, color, material, box.minX, y, box.minZ, box.maxX, y, box.minZ, box.maxX, y, box.minZ + widthZ, box.minX, y, box.minZ + widthZ);
		emitOutlineQuad(output, face, color, material, box.minX, y, box.maxZ - widthZ, box.maxX, y, box.maxZ - widthZ, box.maxX, y, box.maxZ, box.minX, y, box.maxZ);
		emitOutlineQuad(output, face, color, material, box.minX, y, box.minZ, box.minX + widthX, y, box.minZ, box.minX + widthX, y, box.maxZ, box.minX, y, box.maxZ);
		emitOutlineQuad(output, face, color, material, box.maxX - widthX, y, box.minZ, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, box.maxX - widthX, y, box.maxZ);
	}

	private static void emitZFaceBorder(List<BakedQuad> output, AABB box, double z, Direction face, int color, Material.Baked material) {
		if (box.maxX - box.minX < MIN_FACE_SIZE || box.maxY - box.minY < MIN_FACE_SIZE) {
			return;
		}

		double widthX = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxX - box.minX);
		double widthY = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxY - box.minY);
		emitOutlineQuad(output, face, color, material, box.minX, box.minY, z, box.maxX, box.minY, z, box.maxX, box.minY + widthY, z, box.minX, box.minY + widthY, z);
		emitOutlineQuad(output, face, color, material, box.minX, box.maxY - widthY, z, box.maxX, box.maxY - widthY, z, box.maxX, box.maxY, z, box.minX, box.maxY, z);
		emitOutlineQuad(output, face, color, material, box.minX, box.minY, z, box.minX + widthX, box.minY, z, box.minX + widthX, box.maxY, z, box.minX, box.maxY, z);
		emitOutlineQuad(output, face, color, material, box.maxX - widthX, box.minY, z, box.maxX, box.minY, z, box.maxX, box.maxY, z, box.maxX - widthX, box.maxY, z);
	}

	private static void emitXFaceBorder(List<BakedQuad> output, AABB box, double x, Direction face, int color, Material.Baked material) {
		if (box.maxZ - box.minZ < MIN_FACE_SIZE || box.maxY - box.minY < MIN_FACE_SIZE) {
			return;
		}

		double widthZ = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxZ - box.minZ);
		double widthY = Math.min(FacetConfig.effectiveEdgeWidth(), box.maxY - box.minY);
		emitOutlineQuad(output, face, color, material, x, box.minY, box.minZ, x, box.minY, box.maxZ, x, box.minY + widthY, box.maxZ, x, box.minY + widthY, box.minZ);
		emitOutlineQuad(output, face, color, material, x, box.maxY - widthY, box.minZ, x, box.maxY - widthY, box.maxZ, x, box.maxY, box.maxZ, x, box.maxY, box.minZ);
		emitOutlineQuad(output, face, color, material, x, box.minY, box.minZ, x, box.minY, box.minZ + widthZ, x, box.maxY, box.minZ + widthZ, x, box.maxY, box.minZ);
		emitOutlineQuad(output, face, color, material, x, box.minY, box.maxZ - widthZ, x, box.minY, box.maxZ, x, box.maxY, box.maxZ, x, box.maxY, box.maxZ - widthZ);
	}

	private static void emitOutlineQuad(List<BakedQuad> output, Direction face, int color, Material.Baked material,
			double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, double x4, double y4, double z4) {
		BakedQuad quad = createQuad(face, material, true, 15,
				x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
				OUTLINE_UV, OUTLINE_UV, OUTLINE_UV, OUTLINE_UV,
				OUTLINE_UV, OUTLINE_UV, OUTLINE_UV, OUTLINE_UV);
		OUTLINE_COLORS.get().put(quad, color);
		output.add(quad);
	}

	private static BakedQuad createQuad(Direction face, Material.Baked material, boolean outline, int lightEmission,
			double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, double x4, double y4, double z4,
			float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4) {
		boolean reverseWinding = face == Direction.UP || face == Direction.NORTH || face == Direction.EAST;
		Vector3f p1 = new Vector3f((float) x1, (float) y1, (float) z1);
		Vector3f p2 = new Vector3f((float) (reverseWinding ? x4 : x2), (float) (reverseWinding ? y4 : y2), (float) (reverseWinding ? z4 : z2));
		Vector3f p3 = new Vector3f((float) x3, (float) y3, (float) z3);
		Vector3f p4 = new Vector3f((float) (reverseWinding ? x2 : x4), (float) (reverseWinding ? y2 : y4), (float) (reverseWinding ? z2 : z4));
		long uv1 = packedUv(material, u1, v1);
		long uv2 = packedUv(material, reverseWinding ? u4 : u2, reverseWinding ? v4 : v2);
		long uv3 = packedUv(material, u3, v3);
		long uv4 = packedUv(material, reverseWinding ? u2 : u4, reverseWinding ? v2 : v4);
		BakedQuad.MaterialInfo materialInfo = BakedQuad.MaterialInfo.of(
				material, Transparency.TRANSLUCENT, -1, outline ? null : face, lightEmission);

		return new BakedQuad(p1, p2, p3, p4, uv1, uv2, uv3, uv4, face, materialInfo);
	}

	private static long packedUv(Material.Baked material, float u, float v) {
		return UVPair.pack(material.sprite().getU(u), material.sprite().getV(v));
	}

	private record Materials(Material.Baked outline, Map<GraffitiType, Material.Baked> graffiti) {
	}

	private record FacetPart(Map<Direction, List<BakedQuad>> directional, List<BakedQuad> unculled,
			Material.Baked particleMaterial) implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(Direction direction) {
			return direction == null ? unculled : directional.get(direction);
		}

		@Override
		public boolean useAmbientOcclusion() {
			return false;
		}

		@Override
		public int materialFlags() {
			int flags = 0;

			for (List<BakedQuad> quads : directional.values()) {
				for (BakedQuad quad : quads) {
					flags |= quad.materialInfo().flags();
				}
			}

			for (BakedQuad quad : unculled) {
				flags |= quad.materialInfo().flags();
			}

			return flags;
		}
	}
}

package com.facet.client;

import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Static fluid direction textures; vanilla controls UV rotation, geometry, lighting and gameplay. */
public final class LavaGel {
	private static final Material STILL = new Material(Identifier.fromNamespaceAndPath("facet", "block/lava_gel_still"));
	private static final Material FLOW = new Material(Identifier.fromNamespaceAndPath("facet", "block/lava_gel_flow"));
	private static final Material WATER_STILL = new Material(Identifier.fromNamespaceAndPath("facet", "block/water_direction_still"));
	private static final Material WATER_FLOW = new Material(Identifier.fromNamespaceAndPath("facet", "block/water_direction_flow"));
	private static final Material LAVA_SIDE = new Material(Identifier.fromNamespaceAndPath("facet", "block/lava_gel_side"));
	private static final Material WATER_SIDE = new Material(Identifier.fromNamespaceAndPath("facet", "block/water_direction_side"));
	private static volatile Material.Baked lavaSide;
	private static volatile Material.Baked waterSide;
	private static volatile BooleanSupplier outlinesEnabled = () -> false;
	private static volatile FluidModel gelModel;
	private static volatile FluidModel waterModel;

	private LavaGel() {
	}

	public static void initialize(BooleanSupplier enabled) {
		outlinesEnabled = enabled;
	}

	public static void softenFog(FogData fog) {
		if (!outlinesEnabled.getAsBoolean()) {
			return;
		}
		fog.environmentalStart = Math.max(fog.environmentalStart, 4.0F);
		fog.environmentalEnd = Math.max(fog.environmentalEnd, 24.0F);
		fog.skyEnd = fog.environmentalEnd;
		fog.cloudEnd = fog.environmentalEnd;
	}

	public static void bake(MaterialBaker materials, Map<Fluid, FluidModel> originals) {
		lavaSide = materials.get(LAVA_SIDE, () -> "Facet lava side direction");
		waterSide = materials.get(WATER_SIDE, () -> "Facet water side direction");
		gelModel = new FluidModel(ChunkSectionLayer.TRANSLUCENT,
				materials.get(STILL, () -> "Facet still lava"),
				materials.get(FLOW, () -> "Facet lava direction"), null, (BlockTintSource) null);
		var waterStill = materials.get(WATER_STILL, () -> "Facet still water");
		waterModel = new FluidModel(ChunkSectionLayer.TRANSLUCENT, waterStill,
				materials.get(WATER_FLOW, () -> "Facet water direction"), waterStill,
				originals.get(Fluids.WATER).tintSource());
	}

	/** Side UVs use [0, 0.5], unlike the rotated top-face UVs centered at 0.5. */
	public static Material.Baked sideMaterial(FluidModel model, Material.Baked original) {
		Material.Baked side = model == gelModel ? lavaSide : model == waterModel ? waterSide : null;
		return side != null ? side : original;
	}

	public static FluidModel modelFor(FluidState state, FluidModel original) {
		return selectModel(outlinesEnabled.getAsBoolean(), state, original, gelModel, waterModel);
	}

	static FluidModel selectModel(boolean enabled, FluidState state, FluidModel original,
			FluidModel gel, FluidModel water) {
		if (!enabled) return original;
		Fluid type = state.getType();
		FluidModel replacement = type == Fluids.LAVA || type == Fluids.FLOWING_LAVA ? gel
				: type == Fluids.WATER || type == Fluids.FLOWING_WATER ? water : null;
		return replacement != null ? replacement : original;
	}
}

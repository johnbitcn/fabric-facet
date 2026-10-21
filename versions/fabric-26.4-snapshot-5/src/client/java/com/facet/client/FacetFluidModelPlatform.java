package com.facet.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;

/** Fabric 26.4 retains the vanilla BlockTintSource constructor. */
final class FacetFluidModelPlatform {
	private FacetFluidModelPlatform() {}

	static FluidModel retexture(FluidModel original, Material.Baked still,
			Material.Baked flowing, Material.Baked overlay) {
		return new FluidModel(ChunkSectionLayer.TRANSLUCENT, still, flowing, overlay,
				original == null ? null : original.tintSource());
	}
}

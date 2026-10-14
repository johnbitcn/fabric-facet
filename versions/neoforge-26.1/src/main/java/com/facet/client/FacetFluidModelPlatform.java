package com.facet.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;

/** All four supported NeoForge versions expose FluidTintSource and CustomFluidRenderer. */
final class FacetFluidModelPlatform {
	private FacetFluidModelPlatform() {}

	static FluidModel retexture(FluidModel original, Material.Baked still,
			Material.Baked flowing, Material.Baked overlay) {
		return new FluidModel(ChunkSectionLayer.TRANSLUCENT, still, flowing, overlay,
				original == null ? null : original.fluidTintSource(),
				original == null ? null : original.customRenderer());
	}
}

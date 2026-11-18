package com.facet.client;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;

/** Fabric's model-part API uses the vanilla boolean AO contract. */
interface FacetAmbientOcclusionPart extends BlockStateModelPart {
	BlockStateModelPart original();

	@Override
	default boolean useAmbientOcclusion() {
		return original().useAmbientOcclusion();
	}
}

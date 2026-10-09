package com.facet.client;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.util.TriState;

/** All four supported NeoForge versions use the same three-state AO contract. */
interface FacetAmbientOcclusionPart extends BlockStateModelPart {
	BlockStateModelPart original();

	@Override
	default TriState ambientOcclusion() {
		return original().ambientOcclusion();
	}

	// Required by the legacy abstract interface. Modern terrain rendering uses ambientOcclusion().
	@Deprecated
	@Override
	default boolean useAmbientOcclusion() {
		return ambientOcclusion() != TriState.FALSE;
	}
}

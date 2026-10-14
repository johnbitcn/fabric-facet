package com.facet.client;

import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacetAmbientOcclusionTest {
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void vanillaBooleanIsForwardedWithoutChangingItsMeaning(boolean enabled) {
		BlockStateModelPart original = new BlockStateModelPart() {
			@Override public boolean useAmbientOcclusion() { return enabled; }
			@Override public List<BakedQuad> getQuads(Direction face) { return List.of(); }
			@Override public Material.Baked particleMaterial() { return null; }
			@Override public int materialFlags() { return 0; }
		};
		FacetAmbientOcclusionPart wrapper = new FacetAmbientOcclusionPart() {
			@Override public BlockStateModelPart original() { return original; }
			@Override public List<BakedQuad> getQuads(Direction face) { return List.of(); }
			@Override public Material.Baked particleMaterial() { return null; }
			@Override public int materialFlags() { return 0; }
		};
		assertEquals(enabled, wrapper.useAmbientOcclusion());
	}
}

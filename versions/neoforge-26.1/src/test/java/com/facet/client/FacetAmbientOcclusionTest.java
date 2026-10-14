package com.facet.client;

import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class FacetAmbientOcclusionTest {
	@ParameterizedTest
	@EnumSource(TriState.class)
	void retexturingPreservesAllThreeStatesWithoutCallingTheLegacyBoolean(TriState ao) {
		var original = new AmbientPart(null, ao);
		var retextured = new FacetNeoForgeOutlineRenderer.RetexturedPart(original, null);
		assertEquals(ao, retextured.ambientOcclusion());
		assertSame(original, retextured.original());
	}

	@ParameterizedTest
	@EnumSource(TriState.class)
	void bakedOutlinesPreserveAllThreeStatesWithoutCallingTheLegacyBoolean(TriState ao) {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var source = fixtures.material("minecraft:block/stone", 16, 0xFF808080, 0);
			var id = FacetBakedOutlineTextures.variantId(source.sprite().contents().name());
			var target = fixtures.material(id.toString(), 32, 0xFF707070, 128);
			FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(
					List.of(), List.of(), Set.of(id), 0));
			var original = new AmbientPart(BakedOutlineTestFixtures.cube(source), ao);
			var baked = FacetBakedOutlineModel.bakePart(original, null,
					ignored -> target, FacetBakedOutlineModel::remapQuad);
			assertNotNull(baked);
			assertEquals(ao, baked.ambientOcclusion());
		} finally {
			FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(
					List.of(), List.of(), Set.of(), 0));
		}
	}

	private record AmbientPart(BlockStateModelPart delegate, TriState ao) implements BlockStateModelPart {
		@Override public TriState ambientOcclusion() { return ao; }

		// Required by Neo's abstract legacy contract; modern wrappers must never call this.
		@Deprecated
		@Override public boolean useAmbientOcclusion() { throw new AssertionError("Legacy AO was queried"); }

		@Override public List<BakedQuad> getQuads(Direction face) { return delegate.getQuads(face); }
		@Override public Material.Baked particleMaterial() { return delegate.particleMaterial(); }
		@Override public int materialFlags() { return delegate.materialFlags(); }
	}
}

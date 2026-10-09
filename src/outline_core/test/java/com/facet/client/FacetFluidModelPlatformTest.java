package com.facet.client;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetFluidModelPlatformTest {
	@Test
	void replacementTexturesAndOverlayKeepTheTranslucentLayer() {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var still = fixtures.material("facet:block/water_direction_still", 16, 0xB4808080, 0);
			var flowing = fixtures.material("facet:block/water_direction_flow", 16, 0xB4808080, 32);
			var model = FacetFluidModelPlatform.retexture(null, still, flowing, still);
			assertSame(still, model.stillMaterial());
			assertSame(flowing, model.flowingMaterial());
			assertSame(still, model.overlayMaterial());
			assertEquals(ChunkSectionLayer.TRANSLUCENT, model.layer());
			assertEquals(0xB4808080, ((BakedOutlineTestFixtures.TestContents) still.sprite().contents())
					.facet$getOriginalImage().getPixel(0, 0));
		}
	}

	@Test
	void lavaCanKeepANullOverlay() {
		var model = FacetFluidModelPlatform.retexture(null, null, null, null);
		assertNull(model.overlayMaterial());
		assertEquals(ChunkSectionLayer.TRANSLUCENT, model.layer());
	}
}

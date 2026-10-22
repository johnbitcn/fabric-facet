package com.facet.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class FacetModelBakingTexturesTest {
	@Test
	void blockAtlasWinsWhenItemAtlasHasTheSameTexture() {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var id = Identifier.parse("facet:block/test");
			var block = fixtures.material(id.toString(), 1, 0xFFFFFFFF, 0).sprite();
			var item = fixtures.material(id.toString(), 1, 0xFFFFFFFF, 1).sprite();
			var blocks = new SpriteLoader.Preparations(512, 512, 0, block, Map.of(id, block),
					CompletableFuture.completedFuture(null));
			var items = new SpriteLoader.Preparations(512, 512, 0, item, Map.of(id, item),
					CompletableFuture.completedFuture(null));
			var event = new ModelEvent.ModifyBakingResult(null, null, blocks, items, null);

			assertSame(block, FacetNeoForgePlatform.blockTextureGetter(event).apply(id));
		}
	}

	@Test
	void absentBlockTextureUsesMissingSpriteEvenIfItemAtlasHasIt() {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var id = Identifier.parse("facet:block/item_only");
			var missing = fixtures.material("facet:block/missing", 1, 0xFFFFFFFF, 0).sprite();
			var item = fixtures.material(id.toString(), 1, 0xFFFFFFFF, 1).sprite();
			var blocks = new SpriteLoader.Preparations(512, 512, 0, missing, Map.of(),
					CompletableFuture.completedFuture(null));
			var items = new SpriteLoader.Preparations(512, 512, 0, item, Map.of(id, item),
					CompletableFuture.completedFuture(null));
			var event = new ModelEvent.ModifyBakingResult(null, null, blocks, items, null);
			var getter = FacetNeoForgePlatform.blockTextureGetter(event);

			assertSame(missing, getter.apply(id));
			assertSame(missing, getter.apply(Identifier.parse("facet:block/absent")));
		}
	}
}

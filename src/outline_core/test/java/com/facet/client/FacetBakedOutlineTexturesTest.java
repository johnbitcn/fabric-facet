package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetBakedOutlineTexturesTest {
	private final BakedOutlineTestFixtures fixtures = new BakedOutlineTestFixtures();

	@AfterEach
	void close() {
		fixtures.close();
		FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(), Set.of(), 0));
	}

	@Test
	void defaultWidthOnlyAndNamespaceSafeVariantNames() {
		assertTrue(FacetBakedOutlineTextures.matchesWidth(1.0 / 32));
		assertFalse(FacetBakedOutlineTextures.matchesWidth(1.0 / 64));
		assertFalse(FacetBakedOutlineTextures.matchesWidth(3.0 / 32));
		assertNotEquals(FacetBakedOutlineTextures.variantId(Identifier.parse("minecraft:block/stone")),
				FacetBakedOutlineTextures.variantId(Identifier.parse("custom:block/stone")));
	}

	@Test
	void sixteenPixelArtGetsOnePixelBorderAtThirtyTwoWithoutMutatingSource() {
		var source = fixtures.contents("minecraft:block/stone", 16, 0xFF808080);
		source.facet$getOriginalImage().setPixel(8, 8, 0xFFABCDEF);
		try (NativeImage baked = FacetBakedOutlineTextures.bakeImage(source.facet$getOriginalImage(), 0xFFDCDCDC)) {
			assertEquals(32, baked.getWidth());
			assertEquals(32, baked.getHeight());
			int mean = FacetOutlineRules.boostDarkTextureLightness(
					FacetOutlineColor.meanTextureColor(source.facet$getOriginalImage()));
			int edge = ARGB.color(255, Math.round(ARGB.red(mean) * 220 / 255.0f),
					Math.round(ARGB.green(mean) * 220 / 255.0f), Math.round(ARGB.blue(mean) * 220 / 255.0f));
			for (int y = 0; y < 32; y++) {
				for (int x = 0; x < 32; x++) {
					boolean border = x == 0 || y == 0 || x == 31 || y == 31;
					assertEquals(border ? edge : source.facet$getOriginalImage().getPixel(x / 2, y / 2),
							baked.getPixel(x, y));
					assertEquals(255, ARGB.alpha(baked.getPixel(x, y)));
				}
			}
			assertEquals(0xFFABCDEF, source.facet$getOriginalImage().getPixel(8, 8));
			assertEquals(0xFF808080, source.facet$getOriginalImage().getPixel(0, 0));
		}
	}

	@Test
	void higherResolutionKeepsResolutionAndPhysicalBorderWidth() {
		var source = fixtures.contents("minecraft:block/stone", 64, 0xFF909090);
		try (NativeImage baked = FacetBakedOutlineTextures.bakeImage(source.facet$getOriginalImage(), 0xFFDCDCDC)) {
			assertEquals(64, baked.getWidth());
			assertNotEquals(0xFF909090, baked.getPixel(1, 32));
			assertEquals(0xFF909090, baked.getPixel(2, 32));
			assertEquals(0xFF909090, baked.getPixel(61, 32));
			assertNotEquals(0xFF909090, baked.getPixel(62, 32));
		}
	}

	@Test
	void transparentTranslucentOversizedAndNonSquareImagesFallBack() {
		for (int color : new int[] {0x00808080, 0xFE808080, 0x80808080}) {
			var source = fixtures.contents("minecraft:block/test", 16, color);
			assertNull(FacetBakedOutlineTextures.bakeImage(source.facet$getOriginalImage(), -1));
		}
		var large = fixtures.contents("minecraft:block/large", 512, 0xFF808080);
		assertNull(FacetBakedOutlineTextures.bakeImage(large.facet$getOriginalImage(), -1));
		var sheet = fixtures.contents("minecraft:block/sheet", 16, 16, 32, 0xFF808080, 0);
		assertNull(FacetBakedOutlineTextures.bakeImage(sheet.facet$getOriginalImage(), -1));
		assertFalse(FacetBakedOutlineTextures.supportedSize(24));
	}

	@Test
	void atlasVariantsDoNotReplaceOriginalsAndReloadClearsAvailability() {
		var white = fixtures.contents("minecraft:block/white_concrete", 16, 0xFFDCDCDC);
		var stone = fixtures.contents("custom:block/stone", 16, 0xFF808080);
		var special = fixtures.contents("facet:block/rainbow_outline", 64, 0xFF808080);
		var sheet = fixtures.contents("minecraft:block/animated", 16, 16, 32, 0xFF808080, 0);
		var batch = FacetBakedOutlineTextures.prepare(List.of(white, stone, special, sheet));
		try {
			assertEquals(2, batch.generated().size());
			assertTrue(batch.sprites().contains(stone));
			assertTrue(batch.sprites().contains(special));
			FacetBakedOutlineTextures.publish(batch);
			assertTrue(FacetBakedOutlineTextures.available(stone.name()));
			assertFalse(FacetBakedOutlineTextures.available(special.name()));
			assertFalse(FacetBakedOutlineTextures.available(sheet.name()));
			FacetBakedOutlineTextures.publish(FacetBakedOutlineTextures.prepare(List.of(stone)));
			assertFalse(FacetBakedOutlineTextures.available(stone.name()));
		} finally {
			batch.closeGenerated();
		}
	}

	@Test
	void spriteMetadataIsRetained() throws ReflectiveOperationException {
		var white = fixtures.contents("minecraft:block/white_concrete", 16, 0xFFDCDCDC);
		var stone = fixtures.contents("minecraft:block/stone", 16, 16, 16, 0xFF808080, 0.125f);
		var batch = FacetBakedOutlineTextures.prepare(List.of(white, stone));
		try {
			assertEquals(2, batch.generated().size());
			assertTrue(batch.generated().stream().allMatch(sprite -> sprite.transparency().isOpaque()));
			var variant = batch.generated().stream().filter(sprite ->
					sprite.name().equals(FacetBakedOutlineTextures.variantId(stone.name()))).findFirst().orElseThrow();
			var field = SpriteContents.class.getDeclaredField("alphaCutoffBias");
			field.setAccessible(true);
			assertEquals(0.125f, field.getFloat(variant));
		} finally {
			batch.closeGenerated();
		}
	}

	@Test
	void atlasMemoryBudgetIsBoundedAndLargeImagesDoNotStarveSmallSprites() {
		List<SpriteContents> sprites = new ArrayList<>();
		sprites.add(fixtures.contents("minecraft:block/white_concrete", 16, 0xFFDCDCDC));
		sprites.add(fixtures.contents("minecraft:block/small", 16, 0xFF808080));
		for (int i = 0; i < 540; i++) {
			sprites.add(fixtures.contents("minecraft:block/large_" + i, 64, 0xFF808080));
		}
		var batch = FacetBakedOutlineTextures.prepare(sprites);
		try {
			assertTrue(batch.pixelBytes() <= FacetBakedOutlineTextures.MAX_PIXEL_BYTES);
			assertTrue(batch.generated().size() < sprites.size());
			assertTrue(batch.names().contains(FacetBakedOutlineTextures.variantId(Identifier.parse("minecraft:block/small"))));
		} finally {
			batch.closeGenerated();
		}
	}

	@Test
	void existingVariantIdsAreNotOverwritten() {
		var white = fixtures.contents("minecraft:block/white_concrete", 16, 0xFFDCDCDC);
		var stone = fixtures.contents("minecraft:block/stone", 16, 0xFF808080);
		var existing = fixtures.contents(FacetBakedOutlineTextures.variantId(stone.name()).toString(), 32, 0xFF123456);
		var batch = FacetBakedOutlineTextures.prepare(List.of(white, stone, existing));
		try {
			assertFalse(batch.names().contains(existing.name()));
			assertTrue(batch.sprites().contains(existing));
			assertEquals(0xFF123456, existing.facet$getOriginalImage().getPixel(0, 0));
		} finally {
			batch.closeGenerated();
		}
	}
}

package com.facet.client;

import java.io.IOException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RainbowOutlineTextureTest {
	@Test
	void faceSymbolsUse128PixelSilhouettesWithSmoothEdgesAndPastelAnimation() throws IOException {
		for (String name : new String[] {"rainbow_brush", "rainbow_snowflake"}) {
			try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/" + name + ".png");
					var paletteStream = getClass().getResourceAsStream("/assets/facet/textures/block/rainbow_outline_pastel.png");
					NativeImage image = NativeImage.read(stream);
					NativeImage palette = NativeImage.read(paletteStream)) {
				assertEquals(128, image.getWidth());
				assertEquals(128 * 48, image.getHeight());
				assertEquals(0, ARGB.alpha(image.getPixel(0, 0)));
				assertEquals(255, ARGB.alpha(image.getPixel(64, 20)));
				assertEquals(name.equals("rainbow_snowflake") ? 0 : 255, ARGB.alpha(image.getPixel(64, 64)));
				assertNotEquals(image.getPixel(64, 20), image.getPixel(64, 148));
				int opaque = 0, smooth = 0;
				for (int frame = 0; frame < 48; frame++) {
					for (int y = 0; y < 128; y++) {
						for (int x = 0; x < 128; x++) {
							int pixel = image.getPixel(x, frame * 128 + y);
							assertEquals(palette.getPixel((x + y) / 4, frame * 64) & 0xFFFFFF, pixel & 0xFFFFFF);
							assertEquals(ARGB.alpha(image.getPixel(x, y)), ARGB.alpha(pixel));
							if (frame == 0) {
								if (ARGB.alpha(pixel) == 255) opaque++;
								else if (ARGB.alpha(pixel) > 0) smooth++;
							}
						}
					}
				}
				org.junit.jupiter.api.Assertions.assertTrue(opaque > 512 && opaque < 8192);
				org.junit.jupiter.api.Assertions.assertTrue(smooth > 0);
			}
		}
	}

	@Test
	void referenceBugIsSmoothSolidRedWithoutAnOutline() throws IOException {
		org.junit.jupiter.api.Assertions.assertNull(getClass().getResource(
				"/assets/facet/textures/block/warning_bug.png.mcmeta"));
		try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/warning_bug.png");
				NativeImage image = NativeImage.read(stream)) {
			assertEquals(128, image.getWidth());
			assertEquals(128, image.getHeight());
			assertEquals(0, ARGB.alpha(image.getPixel(0, 0)));
			assertEquals(0xFFCB4154, image.getPixel(60, 43));
			int smooth = 0;
			for (int y = 0; y < 128; y++) {
				for (int x = 0; x < 128; x++) {
					int pixel = image.getPixel(x, y);
					assertEquals(0xCB4154, pixel & 0xFFFFFF, "No black or other outline colors");
					if (ARGB.alpha(pixel) > 0 && ARGB.alpha(pixel) < 255) smooth++;
				}
			}
			org.junit.jupiter.api.Assertions.assertTrue(smooth > 0);
		}
	}

	@Test
	void infestedWarningTapeUsesStaticRedWhiteStripes() throws IOException {
		org.junit.jupiter.api.Assertions.assertNull(getClass().getResource(
				"/assets/facet/textures/block/infested_warning_outline.png.mcmeta"));
		try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/infested_warning_outline.png");
				NativeImage image = NativeImage.read(stream)) {
			assertEquals(64, image.getWidth());
			assertEquals(64, image.getHeight());
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					assertEquals(((x + y) / 4) % 2 == 0 ? 0xFFCB4154 : 0xFFFFFFFF, image.getPixel(x, y));
				}
			}
		}
	}

	@Test
	void warningTextureIsStaticOpaqueYellowAndBlackDiagonalTape() throws IOException {
		org.junit.jupiter.api.Assertions.assertNull(getClass().getResource(
				"/assets/facet/textures/block/warning_outline.png.mcmeta"));
		try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/warning_outline.png");
				NativeImage image = NativeImage.read(stream)) {
			assertEquals(64, image.getWidth());
			assertEquals(64, image.getHeight());
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					assertEquals(((x + y) / 4) % 2 == 0 ? 0xFFFFD500 : 0xFF141414, image.getPixel(x, y));
				}
			}
		}
	}

	@Test
	void pastelTextureIsOpaqueThirtyPercentRainbowOnWhite() throws IOException {
		try (var originalStream = getClass().getResourceAsStream("/assets/facet/textures/block/rainbow_outline.png");
				var pastelStream = getClass().getResourceAsStream("/assets/facet/textures/block/rainbow_outline_pastel.png");
				NativeImage original = NativeImage.read(originalStream);
				NativeImage pastel = NativeImage.read(pastelStream)) {
			assertEquals(original.getWidth(), pastel.getWidth());
			assertEquals(original.getHeight(), pastel.getHeight());
			for (int y = 0; y < original.getHeight(); y += original.getWidth()) {
				for (int x = 0; x < original.getWidth(); x++) {
					int rgb = original.getPixel(x, y);
					int expected = ARGB.color(255, (int) Math.rint(ARGB.red(rgb) * 0.3 + 255 * 0.7),
							(int) Math.rint(ARGB.green(rgb) * 0.3 + 255 * 0.7),
							(int) Math.rint(ARGB.blue(rgb) * 0.3 + 255 * 0.7));
					assertEquals(expected, pastel.getPixel(x, y));
				}
			}
		}
	}

	@Test
	void stripesHaveDifferentColorsWithinAFrameAndMoveBetweenFrames() throws IOException {
		try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/rainbow_outline.png");
				NativeImage image = NativeImage.read(stream)) {
			assertEquals(64, image.getWidth());
			assertEquals(64 * 48, image.getHeight());
			assertNotEquals(image.getPixel(0, 0), image.getPixel(16, 0));
			assertNotEquals(image.getPixel(0, 0), image.getPixel(0, 64));
			assertEquals(image.getPixel(16, 0), image.getPixel(16, 63));
		}
	}
}

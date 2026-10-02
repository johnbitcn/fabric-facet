package com.facet.client;

import java.io.IOException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RainbowOutlineTextureTest {
	@Test
	void symbolsUseApproved128PixelShapesAndPastelAnimation() throws IOException {
		for (String name : new String[] {"rainbow_snowflake", "rainbow_brush"}) {
			try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/" + name + ".png");
					var paletteStream = getClass().getResourceAsStream("/assets/facet/textures/block/rainbow_outline_pastel.png");
					NativeImage image = NativeImage.read(stream);
					NativeImage palette = NativeImage.read(paletteStream)) {
				assertEquals(128, image.getWidth());
				assertEquals(128 * 48, image.getHeight());
				assertEquals(0, ARGB.alpha(image.getPixel(0, 0)));
				assertEquals(name.equals("rainbow_snowflake") ? 0 : 255, ARGB.alpha(image.getPixel(64, 64)));
				for (int frame = 0; frame < 48; frame++) {
					assertEquals(palette.getPixel(21, frame * 64) & 0xFFFFFF,
							image.getPixel(64, frame * 128 + 20) & 0xFFFFFF);
					assertEquals(255, ARGB.alpha(image.getPixel(64, frame * 128 + 20)));
				}
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
	void warningTapeIsStaticAndUsesYellowBlackStripes() throws IOException {
		org.junit.jupiter.api.Assertions.assertNull(getClass().getResource(
				"/assets/facet/textures/block/warning_outline.png.mcmeta"));
		try (var stream = getClass().getResourceAsStream("/assets/facet/textures/block/warning_outline.png");
				NativeImage image = NativeImage.read(stream)) {
			assertEquals(64, image.getWidth());
			assertEquals(64, image.getHeight());
			assertEquals(0xFFFFD500, image.getPixel(0, 0));
			assertEquals(0xFF141414, image.getPixel(4, 0));
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

package com.facet.client;

import java.io.IOException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RainbowOutlineTextureTest {
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

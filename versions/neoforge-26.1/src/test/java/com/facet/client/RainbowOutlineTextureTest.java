package com.facet.client;

import java.io.IOException;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RainbowOutlineTextureTest {
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

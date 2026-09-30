package com.facet.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherrackTextureTest {
	@Test
	void replacesTextureWithLinearMeanAndPreservesAlpha() {
		try (NativeImage image = new NativeImage(3, 1, false)) {
			image.setPixel(0, 0, 0xFF000000);
			image.setPixel(1, 0, 0xFFFFFFFF);
			image.setPixel(2, 0, 0x00FF0000);
			FacetOutlineColor.flattenTexture(image);
			assertEquals(0xFFBCBCBC, image.getPixel(0, 0));
			assertEquals(image.getPixel(0, 0), image.getPixel(1, 0));
			assertEquals(0x00BCBCBC, image.getPixel(2, 0));
			FacetOutlineColor.flattenTexture(image);
			assertEquals(0xFFBCBCBC, image.getPixel(0, 0));
		}
	}
}

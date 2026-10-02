package com.facet.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LavaGelTest {
	@Test
	void transparencyPreservesOriginalLavaColorsAndTransparentPixels() {
		int[] original = {0xFFFF4500, 0xFFC83105, 0x80F04A09, 0x000C0201};
		try (NativeImage image = new NativeImage(2, 2, false)) {
			for (int i = 0; i < original.length; i++) image.setPixel(i % 2, i / 2, original[i]);
			LavaGel.applyTransparency(image);
			for (int i = 0; i < original.length; i++) {
				int actual = image.getPixel(i % 2, i / 2);
				assertEquals(original[i] & 0xFFFFFF, actual & 0xFFFFFF);
				assertEquals(ARGB.alpha(original[i]) * LavaGel.ALPHA / 255, ARGB.alpha(actual));
			}
			assertEquals(com.mojang.blaze3d.platform.Transparency.TRANSPARENT_AND_TRANSLUCENT, image.computeTransparency());
		}
	}

	@Test
	void lavaInteriorHasClearerFogOnlyWhileOutlinesAreEnabled() {
		try {
			for (boolean enabled : new boolean[] {false, true}) {
				LavaGel.initialize(() -> enabled);
				var fog = new net.minecraft.client.renderer.fog.FogData();
				fog.environmentalStart = 0.25F;
				fog.environmentalEnd = fog.skyEnd = fog.cloudEnd = 1.0F;
				fog.renderDistanceEnd = 120.0F;
				fog.color.set(0.6F, 0.1F, 0, 1);
				LavaGel.softenFog(fog);
				assertEquals(enabled ? 4.0F : 0.25F, fog.environmentalStart);
				assertEquals(enabled ? 24.0F : 1.0F, fog.environmentalEnd);
				assertEquals(fog.environmentalEnd, fog.skyEnd);
				assertEquals(fog.environmentalEnd, fog.cloudEnd);
				assertEquals(120.0F, fog.renderDistanceEnd);
				assertEquals(0.6F, fog.color.x);
				assertEquals(0.1F, fog.color.y);
			}
		} finally {
			LavaGel.initialize(() -> false);
		}
	}
}

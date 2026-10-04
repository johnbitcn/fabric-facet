package com.facet.client;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LavaGelTest {
	@Test
	void brightArrowsRetainFluidTransparency() throws Exception {
		for (String name : new String[] {"lava_gel", "water_direction"}) {
			var image = ImageIO.read(Path.of("../../src/shared/resources/assets/facet/textures/block",
					name + "_flow.png").toFile());
			int background = image.getRGB(0, 0);
			int arrow = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
			assertEquals(background >>> 24, arrow >>> 24);
			assertTrue((arrow >>> 24) > 0 && (arrow >>> 24) < 255);
			assertTrue(((arrow >> 16) & 255) > ((background >> 16) & 255));
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

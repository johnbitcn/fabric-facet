package com.facet.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

class LavaGelTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void bothFluidsChangeOnlyWhenEnabledAndBaked() {
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, (BlockTintSource) null);
		var gel = new FluidModel(ChunkSectionLayer.TRANSLUCENT, null, null, null, (BlockTintSource) null);
		var water = new FluidModel(ChunkSectionLayer.TRANSLUCENT, null, null, null, (BlockTintSource) null);
		assertFluidStates(Fluids.LAVA, gel, original, gel, water);
		assertFluidStates(Fluids.WATER, water, original, gel, water);
		assertSame(original, LavaGel.selectModel(true, Fluids.EMPTY.defaultFluidState(), original, gel, water));
	}

	private static void assertFluidStates(Fluid fluid, FluidModel expected, FluidModel original, FluidModel gel,
			FluidModel water) {
		for (var state : fluid.getStateDefinition().getPossibleStates()) {
			assertSame(expected, LavaGel.selectModel(true, state, original, gel, water));
			assertSame(original, LavaGel.selectModel(false, state, original, gel, water));
			assertSame(original, LavaGel.selectModel(true, state, original, null, null));
		}
	}

	@Test
	void brightRoadArrowsHaveSmoothEdgesAndUnchangedTransparency() throws Exception {
		Path textures = Path.of("../../src/shared/resources/assets/facet/textures/block");
		for (String name : new String[] {"lava_gel", "water_direction"}) {
			var still = ImageIO.read(textures.resolve(name + "_still.png").toFile());
			for (String face : new String[] {"flow", "side"}) {
				Path file = textures.resolve(name + "_" + face + ".png");
				assertFalse(Files.exists(Path.of(file + ".mcmeta")));
				var image = ImageIO.read(file.toFile());
				assertEquals(256, image.getWidth());
				assertEquals(256, image.getHeight());
				int scale = image.getWidth() / 32;
				int center = (face.equals("flow") ? 16 : 8) * scale;
				int background = image.getRGB(0, 0);
				int arrow = image.getRGB(center, center);
				assertEquals(still.getRGB(0, 0), background);
				assertEquals(name.equals("lava_gel") ? 110 : 180, background >>> 24);
				assertTrue(((arrow >> 16) & 255) > ((background >> 16) & 255));
				boolean smoothEdge = false;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int pixel = image.getRGB(x, y);
						assertEquals(background >>> 24, pixel >>> 24);
						if (pixel != arrow && pixel != background) smoothEdge = true;
					}
				}
				assertTrue(smoothEdge, "Diagonal edges must contain antialiased coverage");
				// A straight narrow shaft, broad shoulders and a tapering head (not just a triangle).
				assertEquals(arrow, image.getRGB(center, center - 6 * scale));
				assertEquals(background, image.getRGB(center + 3 * scale, center - 6 * scale));
				assertEquals(arrow, image.getRGB(center + 4 * scale, center + scale));
				assertEquals(background, image.getRGB(center + 4 * scale, center + 5 * scale));
				assertEquals(arrow, image.getRGB(center, center + 6 * scale));
				assertEquals(background, image.getRGB(center, center + 7 * scale));
				// The entire side arrow fits inside the UV quarter, symmetric about its midpoint.
				int start = face.equals("flow") ? 8 * scale : 0;
				int end = start + 16 * scale;
				for (int y = start; y < end; y++) {
					assertEquals(background, image.getRGB(start, y));
					assertEquals(background, image.getRGB(end - 1, y));
					for (int x = start; x < end; x++) {
						assertEquals(image.getRGB(x, y), image.getRGB(2 * center - 1 - x, y));
					}
				}
			}
		}
	}

	@Test
	void vanillaModelsKeepTheirSideMaterial() {
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, (BlockTintSource) null);
		assertNull(LavaGel.sideMaterial(original, original.flowingMaterial()));
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

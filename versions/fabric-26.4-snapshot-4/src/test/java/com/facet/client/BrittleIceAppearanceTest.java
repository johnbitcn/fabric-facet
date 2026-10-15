package com.facet.client;

import java.io.IOException;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrittleIceAppearanceTest {
	private static final int[] OUTLINE_COLORS = {
			0xFF0284C7, 0xFFCA8A04, 0xFFEA580C, 0xFFB91C1C
	};
	private static final int[] EDGE_COLORS = {
			0xFFE0F2FE, 0xFFFEF9C3, 0xFFFFEDD5, 0xFFFEE2E2
	};

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void brittleIceStagesUseTheOutlineOnlyWhenEnabledAndSkipGeometryOutlines() {
		var states = Blocks.BRITTLE_ICE.getStateDefinition().getPossibleStates();
		assertEquals(4, states.size());
		assertEquals(List.of(0, 1, 2, 3), states.stream()
				.map(FacetOutlineRules::brittleIceStage).sorted().toList());

		for (BlockState state : states) {
			assertTrue(FacetOutlineRules.isBrittleIce(state));
			assertFalse(FacetOutlineRules.usesRainbowOutline(state));
			assertFalse(FacetOutlineRules.usesPastelOutline(state));
			assertTrue(FacetOutlineRules.shouldUseBrittleIceOutline(state, true));
			assertFalse(FacetOutlineRules.shouldUseBrittleIceOutline(state, false));
		}

		BlockState powderSnow = Blocks.POWDER_SNOW.defaultBlockState();
		BlockState frostedIce = Blocks.FROSTED_ICE.defaultBlockState();
		assertTrue(FacetOutlineRules.usesPastelOutline(powderSnow));
		assertFalse(FacetOutlineRules.shouldUseBrittleIceOutline(powderSnow, true));
		assertFalse(FacetOutlineRules.isBrittleIce(frostedIce));
		assertEquals(-1, FacetOutlineRules.brittleIceStage(frostedIce));
	}

	@Test
	void outlineTexturesKeepTheVanillaCenterAndUseThreePixelStageColors() throws IOException {
		for (int age = 0; age < OUTLINE_COLORS.length; age++) {
			String vanillaPath = "assets/minecraft/textures/block/brittle_ice_" + age + ".png";
			String outlinePath = "/assets/facet/textures/block/brittle_ice_outline_" + age + ".png";
			var vanillaStream = getClass().getClassLoader().getResourceAsStream(vanillaPath);
			var outlineStream = getClass().getResourceAsStream(outlinePath);
			assertNotNull(vanillaStream, vanillaPath);
			assertNotNull(outlineStream, outlinePath);

			try (vanillaStream; outlineStream;
					NativeImage vanilla = NativeImage.read(vanillaStream);
					NativeImage outline = NativeImage.read(outlineStream)) {
				assertEquals(16, vanilla.getWidth());
				assertEquals(16, vanilla.getHeight());
				assertEquals(32, outline.getWidth());
				assertEquals(32, outline.getHeight());
				assertEquals(OUTLINE_COLORS[age], outline.getPixel(0, 0));
				assertEquals(EDGE_COLORS[age], outline.getPixel(6, 0));

				for (int y = 0; y < 32; y++) {
					for (int x = 0; x < 32; x++) {
						boolean edge = x < 3 || y < 3 || x >= 29 || y >= 29;
						if (edge) {
							assertEquals(255, ARGB.alpha(outline.getPixel(x, y)),
									"age=" + age + ", edge pixel=" + x + "," + y);
						} else {
							assertEquals(vanilla.getPixel(x / 2, y / 2), outline.getPixel(x, y),
									"age=" + age + ", center pixel=" + x + "," + y);
						}
					}
				}
			}
		}
	}

	@Test
	void meanTextureRgbaKeepsAlphaWeightedRgbAndMeanOpacity() {
		try (NativeImage image = new NativeImage(3, 1, false)) {
			image.setPixel(0, 0, 0xFFFF0000);
			image.setPixel(1, 0, 0x800000FF);
			image.setPixel(2, 0, 0x0000FF00);
			assertEquals(0x80D5009C, FacetOutlineColor.meanTextureRgba(image));
		}
	}
}

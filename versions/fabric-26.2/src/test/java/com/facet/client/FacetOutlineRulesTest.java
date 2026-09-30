package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class FacetOutlineRulesTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void darkTextureColorsGainFifteenLightnessPointsAtOrBelowFifteenPercentValue() {
		assertEquals(0xFF262626, FacetOutlineRules.boostDarkTextureLightness(0xFF000000));
		assertEquals(0xFF464646, FacetOutlineRules.boostDarkTextureLightness(0xFF202020));
		assertEquals(0xFF4C4C4C, FacetOutlineRules.boostDarkTextureLightness(0xFF262626));
		assertEquals(0xFF272727, FacetOutlineRules.boostDarkTextureLightness(0xFF272727));
	}

	@Test
	void cullingQueriesEachFaceExactlyOnce() {
		int[] calls = new int[6];
		int mask = FacetOutlineRules.culledFaces(face -> {
			calls[face.ordinal()]++;
			return face == Direction.UP || face == Direction.NORTH;
		});
		assertArrayEquals(new int[] {1, 1, 1, 1, 1, 1}, calls);
		assertEquals((1 << Direction.UP.ordinal()) | (1 << Direction.NORTH.ordinal()), mask);
	}

	@Test
	void preparedFaceColorsKeepTheOutlineAlphaOnEveryLookup() {
		FacetOutlineColor.clearCache();
		var colors = FacetOutlineColor.resolve(null, null, Blocks.STONE.defaultBlockState());
		for (Direction face : Direction.values()) {
			assertEquals(0xFEFFFFFF, colors.color(face));
			assertEquals(0xFEFFFFFF, colors.color(face));
		}
	}

	@Test
	void ancientDebrisWidthOverridesTheSettingWithoutChangingOtherBlocks() {
		for (double configured : new double[] {1.0 / 64.0, 1.0 / 32.0, 20.0 / 64.0}) {
			assertEquals(3.0 / 32.0,
					FacetOutlineRules.edgeWidth(Blocks.ANCIENT_DEBRIS.defaultBlockState(), configured));
			assertEquals(configured,
					FacetOutlineRules.edgeWidth(Blocks.NETHERRACK.defaultBlockState(), configured));
		}
	}

	@Test
	void outlineAlphaMarkerIsTwoHundredFiftyFour() {
		assertEquals(254, FacetOutlineRules.OUTLINE_SHADER_ALPHA);
		assertEquals(0xFE39FF14, FacetOutlineRules.withOutlineAlpha(0xFF39FF14));
		assertEquals(0xFE000000, FacetOutlineRules.withOutlineAlpha(0xFF000000));
	}

	@Test
	void everyRegisteredShulkerBoxStateIsExcluded() {
		var shulkerBoxes = BuiltInRegistries.BLOCK.stream()
				.filter(ShulkerBoxBlock.class::isInstance)
				.toList();

		assertFalse(shulkerBoxes.isEmpty());
		assertTrue(shulkerBoxes.stream()
				.flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
				.noneMatch(FacetOutlineRules::shouldAnalyze));
	}

	@Test
	void everyRegisteredSnowLayerStateIsIncluded() {
		var snowLayers = BuiltInRegistries.BLOCK.stream()
				.filter(SnowLayerBlock.class::isInstance)
				.toList();

		assertFalse(snowLayers.isEmpty());
		assertTrue(snowLayers.stream()
				.flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
				.allMatch(FacetOutlineRules::shouldAnalyze));
	}
}

package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
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

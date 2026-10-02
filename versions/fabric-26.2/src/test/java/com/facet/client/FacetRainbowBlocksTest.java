package com.facet.client;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FacetRainbowBlocksTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void allInfestedBlockVariantsUseWarningsAndFixedWidth() {
		for (var block : new net.minecraft.world.level.block.Block[] {
				Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS,
				Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
				Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_DEEPSLATE}) {
			var state = block.defaultBlockState();
			assertTrue(FacetOutlineRules.isInfested(state));
			assertEquals(0.35, FacetOutlineRules.faceSymbolSize(state));
			assertTrue(FacetOutlineRules.usesWarningOutline(state));
			assertTrue(FacetOutlineRules.usesRainbowOutline(state));
			assertFalse(FacetOutlineRules.usesPastelOutline(state));
			assertEquals(3.0 / 32.0, FacetOutlineRules.edgeWidth(state, 1.0 / 64.0));
		}
		assertFalse(FacetOutlineRules.isInfested(Blocks.STONE.defaultBlockState()));
		assertFalse(FacetOutlineRules.usesWarningOutline(Blocks.DEEPSLATE.defaultBlockState()));
	}

	@Test
	void allSpecialBlocksUseFullCubePatternedGeometryAndFixedWidth() {
		for (var block : new net.minecraft.world.level.block.Block[] {
				Blocks.ANCIENT_DEBRIS, Blocks.BUDDING_AMETHYST, Blocks.POWDER_SNOW,
				Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL}) {
			var state = block.defaultBlockState();
			assertTrue(FacetOutlineRules.usesRainbowOutline(state));
			assertEquals(0.70, FacetOutlineRules.faceSymbolSize(state));
			assertTrue(FacetShapeEdges.isFullCube(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
			for (double configured : new double[] {1.0 / 64.0, 1.0 / 32.0, 20.0 / 64.0}) {
				assertEquals(3.0 / 32.0, FacetOutlineRules.edgeWidth(state, configured));
			}
		}
		assertTrue(FacetOutlineRules.usesPastelOutline(Blocks.POWDER_SNOW.defaultBlockState()));
		assertTrue(FacetOutlineRules.usesPastelOutline(Blocks.SUSPICIOUS_SAND.defaultBlockState()));
		assertTrue(FacetOutlineRules.usesPastelOutline(Blocks.SUSPICIOUS_GRAVEL.defaultBlockState()));
		assertTrue(FacetOutlineRules.usesBrushSymbol(Blocks.SUSPICIOUS_GRAVEL.defaultBlockState()));
		assertFalse(FacetOutlineRules.usesBrushSymbol(Blocks.GRAVEL.defaultBlockState()));
		assertFalse(FacetOutlineRules.usesPastelOutline(Blocks.SAND.defaultBlockState()));
		assertFalse(FacetOutlineRules.usesRainbowOutline(Blocks.AMETHYST_BLOCK.defaultBlockState()));
		assertEquals(1.0 / 32.0, FacetOutlineRules.edgeWidth(Blocks.AMETHYST_BLOCK.defaultBlockState(), 1.0 / 32.0));
		assertFalse(FacetOutlineRules.shouldAnalyze(Blocks.POWDER_SNOW.defaultBlockState()),
				"Powder snow needs the rainbow scope override, not the collision-based normal scope");
	}
}

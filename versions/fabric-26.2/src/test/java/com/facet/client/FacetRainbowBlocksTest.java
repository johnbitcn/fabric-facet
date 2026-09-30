package com.facet.client;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
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
	void allThreeSpecialBlocksUseFullCubeRainbowGeometryAndFixedWidth() {
		for (var block : new net.minecraft.world.level.block.Block[] {
				Blocks.ANCIENT_DEBRIS, Blocks.BUDDING_AMETHYST, Blocks.POWDER_SNOW}) {
			var state = block.defaultBlockState();
			assertTrue(FacetOutlineRules.usesRainbowOutline(state));
			assertFalse(Shapes.joinIsNotEmpty(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
					Shapes.block(), BooleanOp.NOT_SAME));
			for (double configured : new double[] {1.0 / 64.0, 1.0 / 32.0, 20.0 / 64.0}) {
				assertEquals(3.0 / 32.0, FacetOutlineRules.edgeWidth(state, configured));
			}
		}
		assertFalse(FacetOutlineRules.usesRainbowOutline(Blocks.AMETHYST_BLOCK.defaultBlockState()));
		assertEquals(1.0 / 32.0, FacetOutlineRules.edgeWidth(Blocks.AMETHYST_BLOCK.defaultBlockState(), 1.0 / 32.0));
		assertFalse(FacetOutlineRules.shouldAnalyze(Blocks.POWDER_SNOW.defaultBlockState()),
				"Powder snow needs the rainbow scope override, not the collision-based normal scope");
	}
}

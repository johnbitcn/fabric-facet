package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GraffitiEligibilityTest {
	private static final BlockPos POS = BlockPos.ZERO;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void fullBlockIsAllowedOnEveryFace() {
		VoxelShape cube = Shapes.block();

		for (Direction direction : Direction.values()) {
			assertEquals(1.0, GraffitiEligibility.faceCoverage(cube, direction), 1.0e-9);
			assertEquals(GraffitiEligibility.Result.ALLOWED,
					GraffitiEligibility.evaluateFace(null, POS, STONE, direction, cube));
		}
	}

	@Test
	void halfSlabTopAndBottomAreAllowedButSidesAreIncomplete() {
		VoxelShape slab = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

		assertEquals(GraffitiEligibility.Result.ALLOWED,
				GraffitiEligibility.evaluateFace(null, POS, STONE, Direction.UP, slab));
		assertEquals(GraffitiEligibility.Result.ALLOWED,
				GraffitiEligibility.evaluateFace(null, POS, STONE, Direction.DOWN, slab));
		assertEquals(GraffitiEligibility.Result.INCOMPLETE_FACE,
				GraffitiEligibility.evaluateFace(null, POS, STONE, Direction.NORTH, slab));
	}

	@Test
	void splitBoxesUnionTheirFaceCoverage() {
		VoxelShape stair = Shapes.or(
				Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
				Shapes.box(0.0, 0.5, 0.5, 1.0, 1.0, 1.0));

		assertEquals(1.0, GraffitiEligibility.faceCoverage(stair, Direction.SOUTH), 1.0e-9);
		assertEquals(0.5, GraffitiEligibility.faceCoverage(stair, Direction.UP), 1.0e-9);
	}

	@Test
	void emptyShapeIsIncomplete() {
		assertEquals(GraffitiEligibility.Result.INCOMPLETE_FACE,
				GraffitiEligibility.evaluateFace(null, POS, STONE, Direction.UP, Shapes.empty()));
	}

	@Test
	void airIsNonSolidAndFunctionalBlocksAreRejected() {
		assertEquals(GraffitiEligibility.Result.NON_SOLID,
				GraffitiEligibility.evaluateFace(null, POS, Blocks.AIR.defaultBlockState(),
						Direction.UP, Shapes.block()));
		assertEquals(GraffitiEligibility.Result.FUNCTIONAL,
				GraffitiEligibility.evaluateFace(null, POS, Blocks.CRAFTING_TABLE.defaultBlockState(),
						Direction.UP, Shapes.block()));
	}
}

package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PlacementRotationRulesTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void directionalFamilyOnlyFlipsFacingByOneHundredEightyDegrees() {
		for (Direction facing : Direction.values()) {
			BlockState state = Blocks.OBSERVER.defaultBlockState()
					.setValue(DirectionalBlock.FACING, facing);
			PlacementRotationRules.Step step = PlacementRotationRules.rotateOnce(
					state, PlacementRotationRules.Kind.DIRECTIONAL, Direction.UP);

			assertEquals(facing.getOpposite(), PlacementRotationRules.facing(
					step.state(), PlacementRotationRules.Kind.DIRECTIONAL));
			assertEquals(180.0f, step.residualDegrees());
		}
	}

	@Test
	void parallelHitAxisUsesAPerpendicularAnimationAxisAndStillFlips() {
		BlockState east = Blocks.PISTON.defaultBlockState()
				.setValue(DirectionalBlock.FACING, Direction.EAST);
		PlacementRotationRules.Step step = PlacementRotationRules.rotateOnce(
				east, PlacementRotationRules.Kind.DIRECTIONAL, Direction.EAST);

		assertEquals(Direction.WEST, PlacementRotationRules.facing(
				step.state(), PlacementRotationRules.Kind.DIRECTIONAL));
		assertEquals(Direction.Axis.Y, step.axis());
		assertEquals(180.0f, step.residualDegrees());
	}

	@Test
	void horizontalFamilyOnlyFlipsFacing() {
		BlockState north = Blocks.LOOM.defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

		PlacementRotationRules.Step side = PlacementRotationRules.rotateOnce(
				north, PlacementRotationRules.Kind.HORIZONTAL, Direction.NORTH);
		PlacementRotationRules.Step top = PlacementRotationRules.rotateOnce(
				north, PlacementRotationRules.Kind.HORIZONTAL, Direction.UP);
		PlacementRotationRules.Step bottom = PlacementRotationRules.rotateOnce(
				north, PlacementRotationRules.Kind.HORIZONTAL, Direction.DOWN);

		assertEquals(Direction.SOUTH, PlacementRotationRules.facing(
				side.state(), PlacementRotationRules.Kind.HORIZONTAL));
		assertEquals(Direction.SOUTH, PlacementRotationRules.facing(
				top.state(), PlacementRotationRules.Kind.HORIZONTAL));
		assertEquals(Direction.SOUTH, PlacementRotationRules.facing(
				bottom.state(), PlacementRotationRules.Kind.HORIZONTAL));
		assertEquals(180.0f, top.residualDegrees());
		assertEquals(180.0f, bottom.residualDegrees());
	}

	@Test
	void hopperOnlyRotatesWhenAlreadySideFacing() {
		BlockState down = Blocks.HOPPER.defaultBlockState()
				.setValue(HopperBlock.FACING, Direction.DOWN);
		BlockState north = down.setValue(HopperBlock.FACING, Direction.NORTH);

		assertNull(PlacementRotationRules.rotateOnce(
				down, PlacementRotationRules.Kind.HOPPER, Direction.UP));
		assertEquals(Direction.SOUTH, PlacementRotationRules.facing(
				PlacementRotationRules.rotateOnce(north, PlacementRotationRules.Kind.HOPPER, Direction.UP).state(),
				PlacementRotationRules.Kind.HOPPER));
	}

	@Test
	void eligibilityMatchesCarpetRotatorFamilies() {
		assertEquals(PlacementRotationRules.Kind.DIRECTIONAL,
				PlacementRotationRules.kind(Blocks.OBSERVER.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.DISPENSER,
				PlacementRotationRules.kind(Blocks.DROPPER.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HORIZONTAL,
				PlacementRotationRules.kind(Blocks.LOOM.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HOPPER,
				PlacementRotationRules.kind(Blocks.HOPPER.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.CRAFTER,
				PlacementRotationRules.kind(Blocks.CRAFTER.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HORIZONTAL,
				PlacementRotationRules.kind(Blocks.FURNACE.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HORIZONTAL,
				PlacementRotationRules.kind(Blocks.BLAST_FURNACE.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HORIZONTAL,
				PlacementRotationRules.kind(Blocks.SMOKER.defaultBlockState()));
		assertEquals(PlacementRotationRules.Kind.HORIZONTAL,
				PlacementRotationRules.kind(Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
		assertNull(PlacementRotationRules.kind(Blocks.BED.red().defaultBlockState()));
		assertNull(PlacementRotationRules.kind(Blocks.OAK_STAIRS.defaultBlockState()));
		assertNull(PlacementRotationRules.kind(Blocks.STONE_SLAB.defaultBlockState()));
		assertNull(PlacementRotationRules.kind(Blocks.POWERED_RAIL.defaultBlockState()));
		assertNull(PlacementRotationRules.kind(Blocks.OAK_LOG.defaultBlockState()));
	}

	@Test
	void explicitAdditionalBlocksFlipFacingAndCrafterKeepsItsTop() {
		for (BlockState state : new BlockState[] {
				Blocks.FURNACE.defaultBlockState(),
				Blocks.BLAST_FURNACE.defaultBlockState(),
				Blocks.SMOKER.defaultBlockState(),
				Blocks.CHISELED_BOOKSHELF.defaultBlockState()
		}) {
			Direction facing = PlacementRotationRules.facing(state, PlacementRotationRules.Kind.HORIZONTAL);
			PlacementRotationRules.Step step = PlacementRotationRules.rotateOnce(
					state, PlacementRotationRules.Kind.HORIZONTAL, Direction.UP);
			assertEquals(facing.getOpposite(), PlacementRotationRules.facing(
					step.state(), PlacementRotationRules.Kind.HORIZONTAL));
		}

		BlockState crafter = Blocks.CRAFTER.defaultBlockState();
		Direction originalFront = PlacementRotationRules.facing(crafter, PlacementRotationRules.Kind.CRAFTER);
		Direction originalTop = crafter.getValue(BlockStateProperties.ORIENTATION).top();
		PlacementRotationRules.Step flipped = PlacementRotationRules.rotateOnce(
				crafter, PlacementRotationRules.Kind.CRAFTER, Direction.UP);

		assertEquals(originalFront.getOpposite(), PlacementRotationRules.facing(
				flipped.state(), PlacementRotationRules.Kind.CRAFTER));
		assertEquals(originalTop, flipped.state().getValue(BlockStateProperties.ORIENTATION).top());
		assertEquals(180.0f, flipped.residualDegrees());
	}

	@Test
	void invalidOppositeFacingProducesNoOperation() {
		BlockState north = Blocks.OBSERVER.defaultBlockState()
				.setValue(DirectionalBlock.FACING, Direction.NORTH);

		PlacementRotationController.AdvanceResult flipped = PlacementRotationController.advance(
				north,
				PlacementRotationRules.Kind.DIRECTIONAL,
				Direction.UP,
				state -> state.getValue(DirectionalBlock.FACING) == Direction.SOUTH);

		assertEquals(Direction.SOUTH, flipped.state().getValue(DirectionalBlock.FACING));
		assertEquals(Direction.Axis.Y, flipped.axis());
		assertEquals(180.0f, flipped.residualDegrees());
		assertNull(PlacementRotationController.advance(
				north,
				PlacementRotationRules.Kind.DIRECTIONAL,
				Direction.UP,
				state -> state.getValue(DirectionalBlock.FACING) == Direction.NORTH));
	}

	@Test
	void rapidAnimationInputContinuesFromTheVisibleAngle() {
		long start = 1_000_000_000L;
		PlacementRotationController.Animation first = PlacementRotationController.animationAfter(
				null, Direction.Axis.Y, 180.0f, start);
		long halfway = start + 160_000_000L;

		assertEquals(22.5f, first.residual(halfway), 0.0001f);
		assertEquals(320_000_000L, first.durationNanos());

		PlacementRotationController.Animation second = PlacementRotationController.animationAfter(
				first, Direction.Axis.Y, 180.0f, halfway);

		assertEquals(202.5f, second.residual(halfway), 0.0001f);
		assertEquals(360_000_000L, second.durationNanos());
	}

	@Test
	void movingToANewPlacementPositionResetsToVanillaFacing() {
		Identifier observer = Identifier.fromNamespaceAndPath("minecraft", "observer");
		Identifier piston = Identifier.fromNamespaceAndPath("minecraft", "piston");
		BlockPos firstPos = new BlockPos(1, 64, 1);
		BlockPos secondPos = firstPos.east();

		PlacementRotationController.Selection sameBlock = PlacementRotationController.selectionFor(
				observer, Direction.WEST, null, firstPos,
				observer, Direction.NORTH, null, firstPos);
		PlacementRotationController.Selection movedBlock = PlacementRotationController.selectionFor(
				observer, Direction.WEST, null, firstPos,
				observer, Direction.NORTH, null, secondPos);
		PlacementRotationController.Selection changedBlock = PlacementRotationController.selectionFor(
				observer, Direction.WEST, null, firstPos,
				piston, Direction.SOUTH, null, firstPos);

		assertEquals(Direction.WEST, sameBlock.facing());
		assertFalse(sameBlock.reset());
		assertEquals(Direction.NORTH, movedBlock.facing());
		assertTrue(movedBlock.reset());
		assertEquals(Direction.SOUTH, changedBlock.facing());
		assertTrue(changedBlock.reset());
	}

	@Test
	void crafterRetainsACompatibleTopWhenVanillaPredictionChangesAtTheSamePosition() {
		Identifier crafterId = Identifier.fromNamespaceAndPath("minecraft", "crafter");
		BlockPos placementPos = new BlockPos(2, 64, 2);
		PlacementRotationController.Selection selection = PlacementRotationController.selectionFor(
				crafterId, Direction.UP, Direction.EAST, placementPos,
				crafterId, Direction.NORTH, Direction.UP, placementPos);
		BlockState changedVanillaPrediction = Blocks.CRAFTER.defaultBlockState()
				.setValue(BlockStateProperties.ORIENTATION, FrontAndTop.NORTH_UP);

		BlockState retained = PlacementRotationRules.withFacing(
				changedVanillaPrediction,
				PlacementRotationRules.Kind.CRAFTER,
				selection.facing(),
				selection.top());

		assertNotNull(retained);
		assertEquals(FrontAndTop.UP_EAST, retained.getValue(BlockStateProperties.ORIENTATION));
		assertNull(PlacementRotationRules.withFacing(
				changedVanillaPrediction,
				PlacementRotationRules.Kind.CRAFTER,
				Direction.UP,
				Direction.UP));
	}

	@Test
	void placementRequestMatchesExactlyExpiresAndConsumesOnlyOnce() {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
		Identifier observer = Identifier.fromNamespaceAndPath("minecraft", "observer");
		BlockPos placementPos = new BlockPos(4, 64, -3);
		PlacementRotationController.CurrentPlacement placement =
				new PlacementRotationController.CurrentPlacement(
						playerId,
						dimension,
						InteractionHand.MAIN_HAND,
						observer,
						placementPos.below(),
						Direction.UP,
						placementPos,
						Direction.EAST,
						null,
						PlacementRotationRules.Kind.DIRECTIONAL);

		assertTrue(placement.matchesPlacement(
				playerId, dimension, InteractionHand.MAIN_HAND, observer, placementPos));
		assertFalse(placement.matchesPlacement(
				playerId, dimension, InteractionHand.OFF_HAND, observer, placementPos));

		long created = 5_000_000_000L;
		PlacementRotationController.PendingPlacement pending =
				new PlacementRotationController.PendingPlacement(placement, created);
		assertFalse(pending.isExpired(created + 2_000_000_000L));
		assertTrue(pending.isExpired(created + 2_000_000_001L));

		AtomicReference<PlacementRotationController.PendingPlacement> reference =
				new AtomicReference<>(pending);
		assertTrue(PlacementRotationController.consumePending(reference, pending));
		assertFalse(PlacementRotationController.consumePending(reference, pending));
		assertNull(reference.get());
	}
}

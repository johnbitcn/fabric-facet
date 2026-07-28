package com.facet.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PlacementRotationController {
	private static final long QUARTER_TURN_NANOS = 160_000_000L;
	private static final long PENDING_TIMEOUT_NANOS = 2_000_000_000L;
	private static final AtomicReference<PendingPlacement> PENDING = new AtomicReference<>();
	private static final AtomicReference<ExpectedPlacement> EXPECTED = new AtomicReference<>();
	private static Identifier selectedBlock;
	private static Direction selectedFacing;
	private static Direction selectedTop;
	private static TargetKey target;
	private static Animation animation;
	private static CurrentPlacement currentPlacement;

	private PlacementRotationController() {
	}

	static boolean isAvailable(Minecraft minecraft) {
		return FacetMcBridge.placementRotationPrototypeEnabled()
				&& minecraft.hasSingleplayerServer();
	}

	static synchronized Resolution resolve(Minecraft minecraft, BlockItem blockItem,
			InteractionHand hand, BlockHitResult hit, BlockPlaceContext context,
			BlockState baseState, Predicate<BlockState> canPlace, boolean advance) {
		if (!isAvailable(minecraft)) {
			clearCurrent();
			return new Resolution(baseState, RotationVisual.NONE);
		}

		PlacementRotationRules.Kind kind = PlacementRotationRules.kind(baseState);
		if (kind == null) {
			clearSelection();
			return new Resolution(baseState, RotationVisual.NONE);
		}

		Identifier blockId = BuiltInRegistries.BLOCK.getKey(baseState.getBlock());
		TargetKey nextTarget = new TargetKey(hit.getBlockPos().immutable(), hit.getDirection(),
				context.getClickedPos().immutable());
		Selection nextSelection = selectionFor(
				selectedBlock,
				selectedFacing,
				selectedTop,
				target == null ? null : target.placementPos(),
				blockId,
				PlacementRotationRules.facing(baseState, kind),
				PlacementRotationRules.top(baseState, kind),
				nextTarget.placementPos());

		selectedBlock = nextSelection.blockId();
		selectedFacing = nextSelection.facing();
		selectedTop = nextSelection.top();
		if (nextSelection.reset()) {
			target = null;
			animation = null;
		}

		if (!nextTarget.equals(target)) {
			target = nextTarget;
			animation = null;
		}

		BlockState selectedState = stateForFacing(baseState, kind, selectedFacing, selectedTop, canPlace);
		if (selectedState == null) {
			selectedState = findValidState(baseState, kind, hit.getDirection(), canPlace);
			if (selectedState == null) {
				clearCurrent();
				return new Resolution(baseState, RotationVisual.NONE);
			}
			selectedFacing = PlacementRotationRules.facing(selectedState, kind);
			selectedTop = PlacementRotationRules.top(selectedState, kind);
		}

		if (advance) {
			AdvanceResult result = advance(selectedState, kind, hit.getDirection(), canPlace);
			if (result != null) {
				selectedState = result.state();
				selectedFacing = PlacementRotationRules.facing(selectedState, kind);
				selectedTop = PlacementRotationRules.top(selectedState, kind);
				startAnimation(result.axis(), result.residualDegrees(), System.nanoTime());
			}
		}

		Player player = context.getPlayer();
		if (player != null) {
			currentPlacement = new CurrentPlacement(
					player.getUUID(),
					context.getLevel().dimension().identifier(),
					hand,
					blockId,
					hit.getBlockPos().immutable(),
					hit.getDirection(),
					context.getClickedPos().immutable(),
					selectedFacing,
					selectedTop,
					kind);
		}

		return new Resolution(selectedState, visual(System.nanoTime()));
	}

	static Selection selectionFor(Identifier currentBlock, Direction currentFacing, Direction currentTop,
			BlockPos currentPlacementPos, Identifier nextBlock, Direction baseFacing, Direction baseTop,
			BlockPos nextPlacementPos) {
		boolean reset = !nextBlock.equals(currentBlock)
				|| currentPlacementPos != null && !currentPlacementPos.equals(nextPlacementPos);
		return new Selection(nextBlock, reset ? baseFacing : currentFacing,
				reset ? baseTop : currentTop, reset);
	}

	private static BlockState stateForFacing(BlockState baseState, PlacementRotationRules.Kind kind,
			Direction facing, Direction top, Predicate<BlockState> canPlace) {
		BlockState state = PlacementRotationRules.withFacing(baseState, kind, facing, top);
		return state != null && canPlace.test(state) ? state : null;
	}

	private static BlockState findValidState(BlockState baseState, PlacementRotationRules.Kind kind,
			Direction hitFace, Predicate<BlockState> canPlace) {
		BlockState state = baseState;
		Direction original = PlacementRotationRules.facing(state, kind);

		for (int stepIndex = 0; stepIndex < 4; stepIndex++) {
			if (canPlace.test(state)) {
				return state;
			}
			PlacementRotationRules.Step step = PlacementRotationRules.rotateOnce(state, kind, hitFace);
			if (step == null || step.state() == null) {
				break;
			}
			state = step.state();
			if (PlacementRotationRules.facing(state, kind) == original) {
				break;
			}
		}

		return null;
	}

	static AdvanceResult advance(BlockState selectedState, PlacementRotationRules.Kind kind,
			Direction hitFace, Predicate<BlockState> canPlace) {
		BlockState state = selectedState;
		Direction original = PlacementRotationRules.facing(state, kind);
		float residualDegrees = 0.0f;
		Direction.Axis axis = null;

		for (int stepIndex = 0; stepIndex < 4; stepIndex++) {
			PlacementRotationRules.Step step = PlacementRotationRules.rotateOnce(state, kind, hitFace);
			if (step == null || step.state() == null) {
				return null;
			}
			state = step.state();
			axis = step.axis();
			residualDegrees += step.residualDegrees();

			Direction facing = PlacementRotationRules.facing(state, kind);
			if (facing == original) {
				return null;
			}
			if (canPlace.test(state)) {
				return new AdvanceResult(state, axis, residualDegrees);
			}
		}

		return null;
	}

	private static void startAnimation(Direction.Axis axis, float addedResidualDegrees, long now) {
		animation = animationAfter(animation, axis, addedResidualDegrees, now);
	}

	static Animation animationAfter(Animation current, Direction.Axis axis,
			float addedResidualDegrees, long now) {
		float currentResidual = current != null && current.axis() == axis
				? current.residual(now)
				: 0.0f;
		float startResidual = currentResidual + addedResidualDegrees;
		long duration = Math.max(QUARTER_TURN_NANOS,
				Math.round(Math.abs(startResidual) / 90.0f * QUARTER_TURN_NANOS));
		return new Animation(axis, startResidual, now, duration);
	}

	private static RotationVisual visual(long now) {
		if (animation == null) {
			return RotationVisual.NONE;
		}
		float residual = animation.residual(now);
		if (Math.abs(residual) < 0.01f) {
			animation = null;
			return RotationVisual.NONE;
		}
		return new RotationVisual(animation.axis(), residual, true);
	}

	public static void arm(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isAvailable(minecraft)) {
			return;
		}

		CurrentPlacement current;
		synchronized (PlacementRotationController.class) {
			current = currentPlacement;
		}

		if (current == null || !current.playerId().equals(player.getUUID())
				|| current.hand() != hand
				|| !current.hitPos().equals(hit.getBlockPos())
				|| current.hitFace() != hit.getDirection()) {
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem blockItem)
				|| !current.blockId().equals(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()))) {
			return;
		}

		PENDING.set(new PendingPlacement(current, System.nanoTime()));
	}

	public static BlockState applyPending(BlockItem blockItem, BlockState state,
			BlockPlaceContext context, Predicate<BlockState> canPlace) {
		if (!FacetMcBridge.placementRotationPrototypeEnabled()) {
			return state;
		}
		if (state == null) {
			return null;
		}

		PendingPlacement pending = PENDING.get();
		long now = System.nanoTime();
		if (pending == null) {
			return state;
		}
		if (pending.isExpired(now)) {
			consumePending(PENDING, pending);
			return state;
		}

		CurrentPlacement expected = pending.placement();
		Player player = context.getPlayer();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
		if (player == null || !expected.matchesPlacement(
				player.getUUID(),
				context.getLevel().dimension().identifier(),
				context.getHand(),
				blockId,
				context.getClickedPos())) {
			return state;
		}

		PlacementRotationRules.Kind kind = PlacementRotationRules.kind(state);
		if (kind != expected.kind()) {
			return state;
		}

		BlockState rotated = PlacementRotationRules.withFacing(state, kind, expected.facing(), expected.top());
		if (rotated == null || !canPlace.test(rotated)) {
			if (!context.getLevel().isClientSide()) {
				consumePending(PENDING, pending);
			}
			return null;
		}

		if (!context.getLevel().isClientSide() && consumePending(PENDING, pending)) {
			EXPECTED.set(new ExpectedPlacement(expected.dimension(), expected.placementPos(),
					expected.blockId(), expected.facing(), now));
		}

		return rotated;
	}

	static boolean consumePending(AtomicReference<PendingPlacement> pendingReference,
			PendingPlacement pending) {
		return pendingReference.compareAndSet(pending, null);
	}

	public static void reconcileConfirmed(ClientLevel level, BlockPos pos, BlockState state) {
		if (!FacetMcBridge.placementRotationPrototypeEnabled()) {
			return;
		}

		ExpectedPlacement expected = EXPECTED.get();
		if (expected == null || System.nanoTime() - expected.createdNanos() > PENDING_TIMEOUT_NANOS) {
			EXPECTED.compareAndSet(expected, null);
			return;
		}
		if (!expected.dimension().equals(level.dimension().identifier())
				|| !expected.pos().equals(pos)
				|| !expected.blockId().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
			return;
		}

		PlacementRotationRules.Kind kind = PlacementRotationRules.kind(state);
		if (kind != null) {
			synchronized (PlacementRotationController.class) {
				if (expected.blockId().equals(selectedBlock)) {
					selectedFacing = PlacementRotationRules.facing(state, kind);
					selectedTop = PlacementRotationRules.top(state, kind);
				}
			}
		}
		EXPECTED.compareAndSet(expected, null);
	}

	static synchronized void clearCurrent() {
		currentPlacement = null;
	}

	static synchronized void reset() {
		clearSelection();
		PENDING.set(null);
		EXPECTED.set(null);
	}

	private static void clearSelection() {
		selectedBlock = null;
		selectedFacing = null;
		selectedTop = null;
		target = null;
		animation = null;
		currentPlacement = null;
	}

	record Resolution(BlockState state, RotationVisual visual) {
	}

	record RotationVisual(Direction.Axis axis, float residualDegrees, boolean active) {
		private static final RotationVisual NONE = new RotationVisual(Direction.Axis.Y, 0.0f, false);
	}

	private record TargetKey(BlockPos hitPos, Direction hitFace, BlockPos placementPos) {
	}

	record Selection(Identifier blockId, Direction facing, Direction top, boolean reset) {
	}

	record AdvanceResult(BlockState state, Direction.Axis axis, float residualDegrees) {
	}

	record Animation(Direction.Axis axis, float startResidualDegrees, long startedNanos, long durationNanos) {
		float residual(long now) {
			float progress = Math.min(1.0f, Math.max(0.0f,
					(now - startedNanos) / (float) durationNanos));
			float remaining = 1.0f - progress;
			return startResidualDegrees * remaining * remaining * remaining;
		}
	}

	record CurrentPlacement(UUID playerId, Identifier dimension, InteractionHand hand,
			Identifier blockId, BlockPos hitPos, Direction hitFace, BlockPos placementPos,
			Direction facing, Direction top, PlacementRotationRules.Kind kind) {
		boolean matchesPlacement(UUID candidatePlayerId, Identifier candidateDimension,
				InteractionHand candidateHand, Identifier candidateBlockId, BlockPos candidatePlacementPos) {
			return playerId.equals(candidatePlayerId)
					&& dimension.equals(candidateDimension)
					&& hand == candidateHand
					&& blockId.equals(candidateBlockId)
					&& placementPos.equals(candidatePlacementPos);
		}
	}

	record PendingPlacement(CurrentPlacement placement, long createdNanos) {
		boolean isExpired(long now) {
			return now - createdNanos > PENDING_TIMEOUT_NANOS;
		}
	}

	private record ExpectedPlacement(Identifier dimension, BlockPos pos, Identifier blockId,
			Direction facing, long createdNanos) {
	}
}

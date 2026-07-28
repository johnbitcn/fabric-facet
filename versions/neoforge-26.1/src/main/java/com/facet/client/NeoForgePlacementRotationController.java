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

public final class NeoForgePlacementRotationController {
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

	private NeoForgePlacementRotationController() {
	}

	static boolean isAvailable(Minecraft minecraft) {
		return minecraft.hasSingleplayerServer();
	}

	static synchronized Resolution resolve(Minecraft minecraft, BlockItem blockItem, InteractionHand hand,
			BlockHitResult hit, BlockPlaceContext context, BlockState baseState,
			Predicate<BlockState> canPlace, boolean flip) {
		if (!isAvailable(minecraft)) {
			clearCurrent();
			return new Resolution(baseState, RotationVisual.NONE);
		}

		NeoForgePlacementRotationRules.Kind kind = NeoForgePlacementRotationRules.kind(baseState);
		if (kind == null) {
			clearSelection();
			return new Resolution(baseState, RotationVisual.NONE);
		}

		Identifier blockId = BuiltInRegistries.BLOCK.getKey(baseState.getBlock());
		TargetKey nextTarget = new TargetKey(hit.getBlockPos().immutable(), hit.getDirection(), context.getClickedPos().immutable());
		boolean reset = !blockId.equals(selectedBlock)
				|| target != null && !target.placementPos().equals(nextTarget.placementPos());
		if (reset) {
			selectedBlock = blockId;
			selectedFacing = NeoForgePlacementRotationRules.facing(baseState, kind);
			selectedTop = NeoForgePlacementRotationRules.top(baseState, kind);
			animation = null;
		}
		if (!nextTarget.equals(target)) {
			target = nextTarget;
			animation = null;
		}

		BlockState selectedState = stateForFacing(baseState, kind, selectedFacing, selectedTop, canPlace);
		if (selectedState == null) {
			selectedFacing = NeoForgePlacementRotationRules.facing(baseState, kind);
			selectedTop = NeoForgePlacementRotationRules.top(baseState, kind);
			selectedState = stateForFacing(baseState, kind, selectedFacing, selectedTop, canPlace);
			if (selectedState == null) {
				clearCurrent();
				return new Resolution(baseState, RotationVisual.NONE);
			}
		}

		if (flip) {
			NeoForgePlacementRotationRules.Step step = NeoForgePlacementRotationRules.flip(selectedState, kind, hit.getDirection());
			if (step != null && step.state() != null && canPlace.test(step.state())) {
				selectedState = step.state();
				selectedFacing = NeoForgePlacementRotationRules.facing(selectedState, kind);
				selectedTop = NeoForgePlacementRotationRules.top(selectedState, kind);
				startAnimation(step.axis(), step.residualDegrees(), System.nanoTime());
			}
		}

		Player player = context.getPlayer();
		if (player != null) {
			currentPlacement = new CurrentPlacement(player.getUUID(), context.getLevel().dimension().identifier(), hand,
					blockId, hit.getBlockPos().immutable(), hit.getDirection(), context.getClickedPos().immutable(),
					selectedFacing, selectedTop, kind);
		}
		return new Resolution(selectedState, visual(System.nanoTime()));
	}

	private static BlockState stateForFacing(BlockState baseState, NeoForgePlacementRotationRules.Kind kind,
			Direction facing, Direction top, Predicate<BlockState> canPlace) {
		BlockState state = NeoForgePlacementRotationRules.withFacing(baseState, kind, facing, top);
		return state != null && canPlace.test(state) ? state : null;
	}

	static void startAnimation(Direction.Axis axis, float addedResidualDegrees, long now) {
		float current = animation != null && animation.axis() == axis ? animation.residual(now) : 0.0f;
		float residual = current + addedResidualDegrees;
		long duration = Math.max(QUARTER_TURN_NANOS,
				Math.round(Math.abs(residual) / 90.0f * QUARTER_TURN_NANOS));
		animation = new Animation(axis, residual, now, duration);
	}

	private static RotationVisual visual(long now) {
		if (animation == null) return RotationVisual.NONE;
		float residual = animation.residual(now);
		if (Math.abs(residual) < 0.01f) {
			animation = null;
			return RotationVisual.NONE;
		}
		return new RotationVisual(animation.axis(), residual, true);
	}

	public static void arm(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isAvailable(minecraft)) return;
		CurrentPlacement current;
		synchronized (NeoForgePlacementRotationController.class) {
			current = currentPlacement;
		}
		if (current == null || !current.playerId().equals(player.getUUID()) || current.hand() != hand
				|| !current.hitPos().equals(hit.getBlockPos()) || current.hitFace() != hit.getDirection()) return;
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof BlockItem blockItem
				&& current.blockId().equals(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()))) {
			PENDING.set(new PendingPlacement(current, System.nanoTime()));
		}
	}

	public static BlockState applyPending(BlockItem blockItem, BlockState state, BlockPlaceContext context,
			Predicate<BlockState> canPlace) {
		if (state == null) return null;
		PendingPlacement pending = PENDING.get();
		long now = System.nanoTime();
		if (pending == null) return state;
		if (now - pending.createdNanos() > PENDING_TIMEOUT_NANOS) {
			PENDING.compareAndSet(pending, null);
			return state;
		}
		CurrentPlacement expected = pending.placement();
		Player player = context.getPlayer();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
		if (player == null || !expected.matches(player.getUUID(), context.getLevel().dimension().identifier(),
				context.getHand(), blockId, context.getClickedPos())) return state;
		NeoForgePlacementRotationRules.Kind kind = NeoForgePlacementRotationRules.kind(state);
		if (kind != expected.kind()) return state;
		BlockState flipped = NeoForgePlacementRotationRules.withFacing(state, kind, expected.facing(), expected.top());
		if (flipped == null || !canPlace.test(flipped)) {
			if (!context.getLevel().isClientSide()) PENDING.compareAndSet(pending, null);
			return null;
		}
		if (!context.getLevel().isClientSide() && PENDING.compareAndSet(pending, null)) {
			EXPECTED.set(new ExpectedPlacement(expected.dimension(), expected.placementPos(), expected.blockId(), now));
		}
		return flipped;
	}

	public static void reconcileConfirmed(ClientLevel level, BlockPos pos, BlockState state) {
		ExpectedPlacement expected = EXPECTED.get();
		if (expected == null || System.nanoTime() - expected.createdNanos() > PENDING_TIMEOUT_NANOS) {
			EXPECTED.compareAndSet(expected, null);
			return;
		}
		if (!expected.dimension().equals(level.dimension().identifier()) || !expected.pos().equals(pos)
				|| !expected.blockId().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return;
		NeoForgePlacementRotationRules.Kind kind = NeoForgePlacementRotationRules.kind(state);
		if (kind != null && expected.blockId().equals(selectedBlock)) {
			synchronized (NeoForgePlacementRotationController.class) {
				selectedFacing = NeoForgePlacementRotationRules.facing(state, kind);
				selectedTop = NeoForgePlacementRotationRules.top(state, kind);
			}
		}
		EXPECTED.compareAndSet(expected, null);
	}

	static synchronized void clearCurrent() { currentPlacement = null; }
	static synchronized void reset() { clearSelection(); PENDING.set(null); EXPECTED.set(null); }
	private static void clearSelection() {
		selectedBlock = null; selectedFacing = null; selectedTop = null; target = null; animation = null; currentPlacement = null;
	}

	record Resolution(BlockState state, RotationVisual visual) { }
	record RotationVisual(Direction.Axis axis, float residualDegrees, boolean active) {
		static final RotationVisual NONE = new RotationVisual(Direction.Axis.Y, 0.0f, false);
	}
	private record TargetKey(BlockPos hitPos, Direction hitFace, BlockPos placementPos) { }
	private record Animation(Direction.Axis axis, float residualDegrees, long startedNanos, long durationNanos) {
		private float residual(long now) {
			float progress = Math.min(1.0f, Math.max(0.0f, (now - startedNanos) / (float) durationNanos));
			float remaining = 1.0f - progress;
			return residualDegrees * remaining * remaining * remaining;
		}
	}
	private record CurrentPlacement(UUID playerId, Identifier dimension, InteractionHand hand, Identifier blockId,
			BlockPos hitPos, Direction hitFace, BlockPos placementPos, Direction facing, Direction top,
			NeoForgePlacementRotationRules.Kind kind) {
		private boolean matches(UUID playerId, Identifier dimension, InteractionHand hand, Identifier blockId, BlockPos placementPos) {
			return this.playerId.equals(playerId) && this.dimension.equals(dimension) && this.hand == hand
					&& this.blockId.equals(blockId) && this.placementPos.equals(placementPos);
		}
	}
	private record PendingPlacement(CurrentPlacement placement, long createdNanos) { }
	private record ExpectedPlacement(Identifier dimension, BlockPos pos, Identifier blockId, long createdNanos) { }
}

package com.facet.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class GraffitiEligibility {
	static final double MIN_FACE_COVERAGE = 0.90;
	private static final double PLANE_EPSILON = 1.0e-6;
	private static final Set<String> FUNCTIONAL_BLOCK_NAMES = Set.of(
			"beacon", "bell", "brewing_stand", "crafter", "crafting_table", "daylight_detector", "dispenser",
			"dropper", "enchanting_table", "end_portal_frame", "grindstone", "hopper", "jigsaw", "jukebox",
			"lectern", "lever", "loom", "note_block", "observer", "respawn_anchor", "spawner", "stonecutter",
			"structure_block", "target", "trial_spawner", "vault");
	private static final List<String> FUNCTIONAL_NAME_PARTS = List.of(
			"_button", "_chest", "_command_block", "_door", "_fence_gate", "_furnace", "_pressure_plate",
			"_shulker_box", "_trapdoor", "anvil", "barrel", "blast_furnace", "cartography_table", "comparator",
			"piston", "repeater", "smithing_table", "smoker");

	private GraffitiEligibility() {
	}

	static Result evaluate(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction direction) {
		if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || !state.getFluidState().isEmpty()) {
			return Result.NON_SOLID;
		}

		if (isFunctional(level, pos, state)) {
			return Result.FUNCTIONAL;
		}

		VoxelShape shape = state.getShape(level, pos);

		if (shape.isEmpty() || faceCoverage(shape, direction) <= MIN_FACE_COVERAGE) {
			return Result.INCOMPLETE_FACE;
		}

		return Result.ALLOWED;
	}

	static double facePlane(VoxelShape shape, Direction direction) {
		AABB bounds = shape.bounds();
		return switch (direction) {
			case DOWN -> bounds.minY;
			case UP -> bounds.maxY;
			case NORTH -> bounds.minZ;
			case SOUTH -> bounds.maxZ;
			case WEST -> bounds.minX;
			case EAST -> bounds.maxX;
		};
	}

	static double faceCoverage(VoxelShape shape, Direction direction) {
		double plane = facePlane(shape, direction);
		List<Rectangle> rectangles = new ArrayList<>();

		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

			if (touchesPlane(box, direction, plane)) {
				rectangles.add(project(box, direction));
			}
		});

		return unionArea(rectangles);
	}

	private static boolean isFunctional(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity()) {
			return true;
		}

		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

		if (FUNCTIONAL_BLOCK_NAMES.contains(path)) {
			return true;
		}

		for (String part : FUNCTIONAL_NAME_PARTS) {
			if (path.contains(part)) {
				return true;
			}
		}

		return false;
	}

	private static boolean touchesPlane(AABB box, Direction direction, double plane) {
		double coordinate = switch (direction) {
			case DOWN -> box.minY;
			case UP -> box.maxY;
			case NORTH -> box.minZ;
			case SOUTH -> box.maxZ;
			case WEST -> box.minX;
			case EAST -> box.maxX;
		};
		return Math.abs(coordinate - plane) <= PLANE_EPSILON;
	}

	private static Rectangle project(AABB box, Direction direction) {
		return switch (direction.getAxis()) {
			case X -> new Rectangle(box.minZ, box.minY, box.maxZ, box.maxY);
			case Y -> new Rectangle(box.minX, box.minZ, box.maxX, box.maxZ);
			case Z -> new Rectangle(box.minX, box.minY, box.maxX, box.maxY);
		};
	}

	private static double unionArea(List<Rectangle> rectangles) {
		Set<Double> xEdges = new HashSet<>();
		Set<Double> yEdges = new HashSet<>();

		for (Rectangle rectangle : rectangles) {
			xEdges.add(rectangle.minX());
			xEdges.add(rectangle.maxX());
			yEdges.add(rectangle.minY());
			yEdges.add(rectangle.maxY());
		}

		List<Double> xs = xEdges.stream().sorted().toList();
		List<Double> ys = yEdges.stream().sorted().toList();
		double area = 0.0;

		for (int x = 0; x + 1 < xs.size(); x++) {
			for (int y = 0; y + 1 < ys.size(); y++) {
				double centerX = (xs.get(x) + xs.get(x + 1)) * 0.5;
				double centerY = (ys.get(y) + ys.get(y + 1)) * 0.5;

				if (rectangles.stream().anyMatch(rectangle -> rectangle.contains(centerX, centerY))) {
					area += (xs.get(x + 1) - xs.get(x)) * (ys.get(y + 1) - ys.get(y));
				}
			}
		}

		return area;
	}

	enum Result {
		ALLOWED,
		NON_SOLID,
		FUNCTIONAL,
		INCOMPLETE_FACE
	}

	private record Rectangle(double minX, double minY, double maxX, double maxY) {
		private boolean contains(double x, double y) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY;
		}
	}
}

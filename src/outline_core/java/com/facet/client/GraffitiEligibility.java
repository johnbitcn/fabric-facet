package com.facet.client;

import java.util.Arrays;
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
		return evaluateFace(level, pos, state, direction, state.getShape(level, pos));
	}

	/**
	 * Per-face evaluation reusing a shape the caller has already resolved.
	 * State-level checks (air, fluid, functional blocks) are hoisted by
	 * {@link #baseResult} so a face loop pays them once per block instead of
	 * once per face.
	 */
	static Result evaluateFace(BlockAndTintGetter level, BlockPos pos, BlockState state,
			Direction direction, VoxelShape shape) {
		Result base = baseResult(level, pos, state);
		if (base != Result.ALLOWED) {
			return base;
		}

		if (shape.isEmpty() || faceCoverage(shape, direction) <= MIN_FACE_COVERAGE) {
			return Result.INCOMPLETE_FACE;
		}

		return Result.ALLOWED;
	}

	/** State-level graffiti checks that do not depend on the target face. */
	static Result baseResult(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || !state.getFluidState().isEmpty()) {
			return Result.NON_SOLID;
		}

		if (isFunctional(level, pos, state)) {
			return Result.FUNCTIONAL;
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
		int rectangleCount = countFaceRectangles(shape, direction, plane);

		if (rectangleCount == 0) {
			return 0.0;
		}

		double[] rectangles = new double[rectangleCount * 4];
		int[] index = {0};
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			if (touchesPlane(minX, minY, minZ, maxX, maxY, maxZ, direction, plane)) {
				project(minX, minY, minZ, maxX, maxY, maxZ, direction, rectangles, index[0] * 4);
				index[0]++;
			}
		});

		return unionArea(rectangles, rectangleCount);
	}

	private static int countFaceRectangles(VoxelShape shape, Direction direction, double plane) {
		int[] count = {0};
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			if (touchesPlane(minX, minY, minZ, maxX, maxY, maxZ, direction, plane)) {
				count[0]++;
			}
		});
		return count[0];
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

	private static boolean touchesPlane(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ, Direction direction, double plane) {
		double coordinate = switch (direction) {
			case DOWN -> minY;
			case UP -> maxY;
			case NORTH -> minZ;
			case SOUTH -> maxZ;
			case WEST -> minX;
			case EAST -> maxX;
		};
		return Math.abs(coordinate - plane) <= PLANE_EPSILON;
	}

	private static void project(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ, Direction direction,
			double[] rectangles, int offset) {
		switch (direction.getAxis()) {
			case X -> {
				rectangles[offset] = minZ;
				rectangles[offset + 1] = minY;
				rectangles[offset + 2] = maxZ;
				rectangles[offset + 3] = maxY;
			}
			case Y -> {
				rectangles[offset] = minX;
				rectangles[offset + 1] = minZ;
				rectangles[offset + 2] = maxX;
				rectangles[offset + 3] = maxZ;
			}
			case Z -> {
				rectangles[offset] = minX;
				rectangles[offset + 1] = minY;
				rectangles[offset + 2] = maxX;
				rectangles[offset + 3] = maxY;
			}
		}
	}

	private static double unionArea(double[] rectangles, int count) {
		double[] xs = sortedUniqueEdges(rectangles, count, true);
		double[] ys = sortedUniqueEdges(rectangles, count, false);
		double area = 0.0;

		for (int x = 0; x + 1 < xs.length; x++) {
			for (int y = 0; y + 1 < ys.length; y++) {
				double centerX = (xs[x] + xs[x + 1]) * 0.5;
				double centerY = (ys[y] + ys[y + 1]) * 0.5;

				if (containsRectangle(rectangles, count, centerX, centerY)) {
					area += (xs[x + 1] - xs[x]) * (ys[y + 1] - ys[y]);
				}
			}
		}

		return area;
	}

	private static double[] sortedUniqueEdges(double[] rectangles, int count, boolean xAxis) {
		double[] edges = new double[count * 2];

		for (int index = 0; index < count; index++) {
			int offset = index * 4;
			edges[index * 2] = xAxis ? rectangles[offset] : rectangles[offset + 1];
			edges[index * 2 + 1] = xAxis ? rectangles[offset + 2] : rectangles[offset + 3];
		}

		Arrays.sort(edges);
		int unique = 0;

		for (double edge : edges) {
			if (unique == 0 || edge != edges[unique - 1]) {
				edges[unique++] = edge;
			}
		}

		return unique == edges.length ? edges : Arrays.copyOf(edges, unique);
	}

	private static boolean containsRectangle(double[] rectangles, int count, double x, double y) {
		for (int index = 0; index < count; index++) {
			int offset = index * 4;

			if (x >= rectangles[offset] && x <= rectangles[offset + 2]
					&& y >= rectangles[offset + 1] && y <= rectangles[offset + 3]) {
				return true;
			}
		}

		return false;
	}

	enum Result {
		ALLOWED,
		NON_SOLID,
		FUNCTIONAL,
		INCOMPLETE_FACE
	}
}

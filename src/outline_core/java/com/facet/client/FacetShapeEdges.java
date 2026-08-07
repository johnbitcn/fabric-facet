package com.facet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class FacetShapeEdges {
	static final double AXIS_EPSILON = 1.0e-8;
	private static final double SAMPLE_OFFSET = 1.0e-7;
	private static final double WIDTH_UNIT = 1.0 / 64.0;
	private static final int MIN_WIDTH_UNITS = 1;
	private static final int MAX_WIDTH_UNITS = 20;
	/** Cached surface strips for the unit cube, keyed by width units (1/64 block). */
	private static final ConcurrentHashMap<Integer, List<Strip>> FULL_CUBE_STRIPS = new ConcurrentHashMap<>();
	private static final int PARTIAL_SHAPE_CACHE_CAPACITY = 512;
	/**
	 * Cached surface strips for non-full-cube shapes, keyed by (shape, width).
	 * Shapes returned by {@code BlockState.getShape} are shared singletons for static blocks,
	 * so identity-keyed entries hit across chunk recompiles; per-position shapes (fences,
	 * walls) miss and are bounded by the LRU capacity.
	 */
	private static final Map<PartialShapeKey, List<Strip>> PARTIAL_SHAPE_STRIPS =
			Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<PartialShapeKey, List<Strip>> eldest) {
					return size() > PARTIAL_SHAPE_CACHE_CAPACITY;
				}
			});

	private FacetShapeEdges() {
	}

	static void forEachEdge(VoxelShape shape, EdgeConsumer consumer) {
		shape.forAllEdges(consumer::accept);
	}

	static void forEachSurfaceStrip(VoxelShape shape, double maxWidth, SurfaceStripConsumer consumer) {
		if (isFullCube(shape)) {
			List<Strip> strips = fullCubeStrips(maxWidth);

			if (FacetOutlineStats.enabled()) {
				FacetOutlineStats.FULL_CUBE_STRIPS_SERVED.addAndGet(strips.size());
			}

			for (Strip strip : strips) {
				consumer.accept(strip.face, strip.minX, strip.minY, strip.minZ,
						strip.maxX, strip.maxY, strip.maxZ);
			}
			return;
		}

		List<Strip> strips = partialShapeStrips(shape, maxWidth);

		if (FacetOutlineStats.enabled()) {
			FacetOutlineStats.PARTIAL_STRIPS_SERVED.addAndGet(strips.size());
		}

		for (Strip strip : strips) {
			consumer.accept(strip.face, strip.minX, strip.minY, strip.minZ,
					strip.maxX, strip.maxY, strip.maxZ);
		}
	}

	private static List<Strip> partialShapeStrips(VoxelShape shape, double maxWidth) {
		PartialShapeKey key = new PartialShapeKey(shape, maxWidth);
		List<Strip> cached = PARTIAL_SHAPE_STRIPS.get(key);

		if (cached != null) {
			if (FacetOutlineStats.enabled()) {
				FacetOutlineStats.PARTIAL_CACHE_HITS.incrementAndGet();
			}
			return cached;
		}

		if (FacetOutlineStats.enabled()) {
			FacetOutlineStats.PARTIAL_CACHE_MISSES.incrementAndGet();
		}

		List<Strip> strips = new ArrayList<>();
		forEachSurfaceStripUncached(shape, maxWidth,
				(face, minX, minY, minZ, maxX, maxY, maxZ) ->
						strips.add(new Strip(face, minX, minY, minZ, maxX, maxY, maxZ)));
		List<Strip> immutable = List.copyOf(strips);
		PARTIAL_SHAPE_STRIPS.put(key, immutable);
		return immutable;
	}

	private static void forEachSurfaceStripUncached(VoxelShape shape, double maxWidth, SurfaceStripConsumer consumer) {
		double[] boxes = collectBoxes(shape);

		forEachEdge(shape, (x1, y1, z1, x2, y2, z2) -> {
			if (same(y1, y2) && same(z1, z2)) {
				forEachSegment(shape, Axis.X, Math.min(x1, x2), Math.max(x1, x2),
						(minX, maxX) -> emitXEdgeStrips(shape, boxes, minX, maxX, y1, z1, maxWidth, consumer));
			} else if (same(x1, x2) && same(z1, z2)) {
				forEachSegment(shape, Axis.Y, Math.min(y1, y2), Math.max(y1, y2),
						(minY, maxY) -> emitYEdgeStrips(shape, boxes, x1, minY, maxY, z1, maxWidth, consumer));
			} else if (same(x1, x2) && same(y1, y2)) {
				forEachSegment(shape, Axis.Z, Math.min(z1, z2), Math.max(z1, z2),
						(minZ, maxZ) -> emitZEdgeStrips(shape, boxes, x1, y1, minZ, maxZ, maxWidth, consumer));
			}
		});
	}

	private static boolean isFullCube(VoxelShape shape) {
		return shape == Shapes.block()
				|| (!shape.isEmpty()
				&& Math.abs(shape.min(Axis.X)) <= AXIS_EPSILON
				&& Math.abs(shape.min(Axis.Y)) <= AXIS_EPSILON
				&& Math.abs(shape.min(Axis.Z)) <= AXIS_EPSILON
				&& Math.abs(shape.max(Axis.X) - 1.0) <= AXIS_EPSILON
				&& Math.abs(shape.max(Axis.Y) - 1.0) <= AXIS_EPSILON
				&& Math.abs(shape.max(Axis.Z) - 1.0) <= AXIS_EPSILON
				&& shapeIsSingleFullBox(shape));
	}

	private static boolean shapeIsSingleFullBox(VoxelShape shape) {
		int[] count = {0};
		boolean[] full = {true};
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			count[0]++;
			if (Math.abs(minX) > AXIS_EPSILON || Math.abs(minY) > AXIS_EPSILON || Math.abs(minZ) > AXIS_EPSILON
					|| Math.abs(maxX - 1.0) > AXIS_EPSILON || Math.abs(maxY - 1.0) > AXIS_EPSILON
					|| Math.abs(maxZ - 1.0) > AXIS_EPSILON) {
				full[0] = false;
			}
		});
		return count[0] == 1 && full[0];
	}

	private static List<Strip> fullCubeStrips(double maxWidth) {
		int units = clampWidthUnits((int) Math.round(maxWidth / WIDTH_UNIT));
		return FULL_CUBE_STRIPS.computeIfAbsent(units, FacetShapeEdges::bakeFullCubeStrips);
	}

	private static List<Strip> bakeFullCubeStrips(int widthUnits) {
		double maxWidth = widthUnits * WIDTH_UNIT;
		List<Strip> strips = new ArrayList<>(24);
		forEachSurfaceStripUncached(Shapes.block(), maxWidth,
				(face, minX, minY, minZ, maxX, maxY, maxZ) ->
						strips.add(new Strip(face, minX, minY, minZ, maxX, maxY, maxZ)));
		return List.copyOf(strips);
	}

	private static int clampWidthUnits(int units) {
		return Math.max(MIN_WIDTH_UNITS, Math.min(MAX_WIDTH_UNITS, units));
	}

	private record Strip(Direction face, double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
	}

	private record PartialShapeKey(VoxelShape shape, double maxWidth) {
	}

	private static double[] collectBoxes(VoxelShape shape) {
		int[] count = {0};
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> count[0]++);
		double[] boxes = new double[count[0] * 6];
		int[] index = {0};
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			int offset = index[0] * 6;
			boxes[offset] = minX;
			boxes[offset + 1] = minY;
			boxes[offset + 2] = minZ;
			boxes[offset + 3] = maxX;
			boxes[offset + 4] = maxY;
			boxes[offset + 5] = maxZ;
			index[0]++;
		});
		return boxes;
	}

	private static void forEachSegment(VoxelShape shape, Axis axis, double min, double max, SegmentConsumer consumer) {
		DoubleList coordinates = shape.getCoords(axis);
		double segmentStart = min;

		for (int index = 0; index < coordinates.size(); index++) {
			double coordinate = coordinates.getDouble(index);

			if (coordinate <= segmentStart + AXIS_EPSILON) {
				continue;
			}

			if (coordinate >= max - AXIS_EPSILON) {
				break;
			}

			consumer.accept(segmentStart, coordinate);
			segmentStart = coordinate;
		}

		if (max - segmentStart > AXIS_EPSILON) {
			consumer.accept(segmentStart, max);
		}
	}

	private static void emitXEdgeStrips(VoxelShape shape, double[] boxes,
			double minX, double maxX, double y, double z, double maxWidth, SurfaceStripConsumer consumer) {
		double midX = (minX + maxX) * 0.5;
		boolean negativeYNegativeZ = contains(boxes, midX, y - SAMPLE_OFFSET, z - SAMPLE_OFFSET);
		boolean negativeYPositiveZ = contains(boxes, midX, y - SAMPLE_OFFSET, z + SAMPLE_OFFSET);
		boolean positiveYNegativeZ = contains(boxes, midX, y + SAMPLE_OFFSET, z - SAMPLE_OFFSET);
		boolean positiveYPositiveZ = contains(boxes, midX, y + SAMPLE_OFFSET, z + SAMPLE_OFFSET);

		emitXStripOnY(shape, minX, maxX, y, z, -1, negativeYNegativeZ, positiveYNegativeZ, maxWidth, consumer);
		emitXStripOnY(shape, minX, maxX, y, z, 1, negativeYPositiveZ, positiveYPositiveZ, maxWidth, consumer);
		emitXStripOnZ(shape, minX, maxX, y, z, -1, negativeYNegativeZ, negativeYPositiveZ, maxWidth, consumer);
		emitXStripOnZ(shape, minX, maxX, y, z, 1, positiveYNegativeZ, positiveYPositiveZ, maxWidth, consumer);
	}

	private static void emitYEdgeStrips(VoxelShape shape, double[] boxes,
			double x, double minY, double maxY, double z, double maxWidth, SurfaceStripConsumer consumer) {
		double midY = (minY + maxY) * 0.5;
		boolean negativeXNegativeZ = contains(boxes, x - SAMPLE_OFFSET, midY, z - SAMPLE_OFFSET);
		boolean negativeXPositiveZ = contains(boxes, x - SAMPLE_OFFSET, midY, z + SAMPLE_OFFSET);
		boolean positiveXNegativeZ = contains(boxes, x + SAMPLE_OFFSET, midY, z - SAMPLE_OFFSET);
		boolean positiveXPositiveZ = contains(boxes, x + SAMPLE_OFFSET, midY, z + SAMPLE_OFFSET);

		emitYStripOnX(shape, x, minY, maxY, z, -1, negativeXNegativeZ, positiveXNegativeZ, maxWidth, consumer);
		emitYStripOnX(shape, x, minY, maxY, z, 1, negativeXPositiveZ, positiveXPositiveZ, maxWidth, consumer);
		emitYStripOnZ(shape, x, minY, maxY, z, -1, negativeXNegativeZ, negativeXPositiveZ, maxWidth, consumer);
		emitYStripOnZ(shape, x, minY, maxY, z, 1, positiveXNegativeZ, positiveXPositiveZ, maxWidth, consumer);
	}

	private static void emitZEdgeStrips(VoxelShape shape, double[] boxes,
			double x, double y, double minZ, double maxZ, double maxWidth, SurfaceStripConsumer consumer) {
		double midZ = (minZ + maxZ) * 0.5;
		boolean negativeXNegativeY = contains(boxes, x - SAMPLE_OFFSET, y - SAMPLE_OFFSET, midZ);
		boolean negativeXPositiveY = contains(boxes, x - SAMPLE_OFFSET, y + SAMPLE_OFFSET, midZ);
		boolean positiveXNegativeY = contains(boxes, x + SAMPLE_OFFSET, y - SAMPLE_OFFSET, midZ);
		boolean positiveXPositiveY = contains(boxes, x + SAMPLE_OFFSET, y + SAMPLE_OFFSET, midZ);

		emitZStripOnX(shape, x, y, minZ, maxZ, -1, negativeXNegativeY, positiveXNegativeY, maxWidth, consumer);
		emitZStripOnX(shape, x, y, minZ, maxZ, 1, negativeXPositiveY, positiveXPositiveY, maxWidth, consumer);
		emitZStripOnY(shape, x, y, minZ, maxZ, -1, negativeXNegativeY, negativeXPositiveY, maxWidth, consumer);
		emitZStripOnY(shape, x, y, minZ, maxZ, 1, positiveXNegativeY, positiveXPositiveY, maxWidth, consumer);
	}

	private static void emitXStripOnY(VoxelShape shape, double minX, double maxX, double y, double z,
			int zStep, boolean negativeYSolid, boolean positiveYSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeYSolid == positiveYSolid) {
			return;
		}

		double otherZ = z + zStep * stripDepth(shape, Axis.Z, z, zStep, maxWidth);
		consumer.accept(negativeYSolid ? Direction.UP : Direction.DOWN,
				minX, y, Math.min(z, otherZ), maxX, y, Math.max(z, otherZ));
	}

	private static void emitXStripOnZ(VoxelShape shape, double minX, double maxX, double y, double z,
			int yStep, boolean negativeZSolid, boolean positiveZSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeZSolid == positiveZSolid) {
			return;
		}

		double otherY = y + yStep * stripDepth(shape, Axis.Y, y, yStep, maxWidth);
		consumer.accept(negativeZSolid ? Direction.SOUTH : Direction.NORTH,
				minX, Math.min(y, otherY), z, maxX, Math.max(y, otherY), z);
	}

	private static void emitYStripOnX(VoxelShape shape, double x, double minY, double maxY, double z,
			int zStep, boolean negativeXSolid, boolean positiveXSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeXSolid == positiveXSolid) {
			return;
		}

		double otherZ = z + zStep * stripDepth(shape, Axis.Z, z, zStep, maxWidth);
		consumer.accept(negativeXSolid ? Direction.EAST : Direction.WEST,
				x, minY, Math.min(z, otherZ), x, maxY, Math.max(z, otherZ));
	}

	private static void emitYStripOnZ(VoxelShape shape, double x, double minY, double maxY, double z,
			int xStep, boolean negativeZSolid, boolean positiveZSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeZSolid == positiveZSolid) {
			return;
		}

		double otherX = x + xStep * stripDepth(shape, Axis.X, x, xStep, maxWidth);
		consumer.accept(negativeZSolid ? Direction.SOUTH : Direction.NORTH,
				Math.min(x, otherX), minY, z, Math.max(x, otherX), maxY, z);
	}

	private static void emitZStripOnX(VoxelShape shape, double x, double y, double minZ, double maxZ,
			int yStep, boolean negativeXSolid, boolean positiveXSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeXSolid == positiveXSolid) {
			return;
		}

		double otherY = y + yStep * stripDepth(shape, Axis.Y, y, yStep, maxWidth);
		consumer.accept(negativeXSolid ? Direction.EAST : Direction.WEST,
				x, Math.min(y, otherY), minZ, x, Math.max(y, otherY), maxZ);
	}

	private static void emitZStripOnY(VoxelShape shape, double x, double y, double minZ, double maxZ,
			int xStep, boolean negativeYSolid, boolean positiveYSolid, double maxWidth, SurfaceStripConsumer consumer) {
		if (negativeYSolid == positiveYSolid) {
			return;
		}

		double otherX = x + xStep * stripDepth(shape, Axis.X, x, xStep, maxWidth);
		consumer.accept(negativeYSolid ? Direction.UP : Direction.DOWN,
				Math.min(x, otherX), y, minZ, Math.max(x, otherX), y, maxZ);
	}

	private static double stripDepth(VoxelShape shape, Axis axis, double coordinate, int step, double maxWidth) {
		DoubleList coordinates = shape.getCoords(axis);
		double nearest = Double.POSITIVE_INFINITY;

		for (int index = 0; index < coordinates.size(); index++) {
			double distance = (coordinates.getDouble(index) - coordinate) * step;

			if (distance > AXIS_EPSILON && distance < nearest) {
				nearest = distance;
			}
		}

		return Double.isFinite(nearest) ? Math.min(maxWidth, nearest) : 0.0;
	}

	private static boolean contains(double[] boxes, double x, double y, double z) {
		for (int offset = 0; offset < boxes.length; offset += 6) {
			if (x > boxes[offset] && x < boxes[offset + 3]
					&& y > boxes[offset + 1] && y < boxes[offset + 4]
					&& z > boxes[offset + 2] && z < boxes[offset + 5]) {
				return true;
			}
		}

		return false;
	}

	private static boolean same(double first, double second) {
		return Math.abs(first - second) <= AXIS_EPSILON;
	}

	@FunctionalInterface
	interface EdgeConsumer {
		void accept(double x1, double y1, double z1, double x2, double y2, double z2);
	}

	@FunctionalInterface
	private interface SegmentConsumer {
		void accept(double min, double max);
	}

	@FunctionalInterface
	interface SurfaceStripConsumer {
		void accept(Direction face, double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
	}
}

package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class FacetShapeEdgesTest {
	@Test
	void cubeProducesOnlyPositiveAreaSurfaceStrips() {
		List<Strip> strips = strips(Shapes.block());
		assertFalse(strips.isEmpty());
		assertTrue(strips.stream().allMatch(Strip::hasPositiveArea));
	}

	@Test
	void slabDoesNotCreateGeometryAboveItsShape() {
		List<Strip> strips = strips(Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
		assertFalse(strips.isEmpty());
		assertTrue(strips.stream().allMatch(strip -> strip.maxY <= 0.5 + FacetShapeEdges.AXIS_EPSILON));
	}

	@Test
	void compositeStairKeepsBothRearVerticalSidesAcrossInternalSplit() {
		VoxelShape stair = Shapes.or(
				Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
				Shapes.box(0.0, 0.5, 0.5, 1.0, 1.0, 1.0));
		List<Strip> strips = strips(stair);
		assertTrue(hasVerticalSegment(strips, 0.0, 0.0, 0.5), strips::toString);
		assertTrue(hasVerticalSegment(strips, 0.0, 0.5, 1.0), strips::toString);
		assertTrue(hasVerticalSegment(strips, 1.0, 0.0, 0.5), strips::toString);
		assertTrue(hasVerticalSegment(strips, 1.0, 0.5, 1.0), strips::toString);
	}

	private static List<Strip> strips(VoxelShape shape) {
		List<Strip> strips = new ArrayList<>();
		FacetShapeEdges.forEachSurfaceStrip(shape, FacetOutlineRules.DEFAULT_EDGE_WIDTH,
				(face, minX, minY, minZ, maxX, maxY, maxZ) -> strips.add(new Strip(face, minX, minY, minZ, maxX, maxY, maxZ)));
		return strips;
	}

	private static boolean hasVerticalSegment(List<Strip> strips, double x, double minY, double maxY) {
		return strips.stream().anyMatch(strip -> Math.abs(strip.minX - x) <= FacetShapeEdges.AXIS_EPSILON
				&& Math.abs(strip.maxX - x) <= FacetShapeEdges.AXIS_EPSILON
				&& strip.minY <= minY + FacetShapeEdges.AXIS_EPSILON
				&& strip.maxY >= maxY - FacetShapeEdges.AXIS_EPSILON
				&& strip.maxZ - strip.minZ > FacetShapeEdges.AXIS_EPSILON);
	}

	private record Strip(Direction face, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		private boolean hasPositiveArea() {
			return (maxX - minX) * (maxY - minY) + (maxY - minY) * (maxZ - minZ) + (maxX - minX) * (maxZ - minZ) > 0.0;
		}
	}
}

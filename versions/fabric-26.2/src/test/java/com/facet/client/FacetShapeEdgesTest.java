package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FacetShapeEdgesTest {
	private static final double WIDTH = 2.0 / 64.0;

	@BeforeEach
	void resetStats() {
		FacetOutlineStats.setEnabledForTesting(true);
		FacetOutlineStats.PARTIAL_CACHE_HITS.set(0);
		FacetOutlineStats.PARTIAL_CACHE_MISSES.set(0);
		FacetOutlineStats.PARTIAL_STRIPS_SERVED.set(0);
		FacetOutlineStats.FULL_CUBE_STRIPS_SERVED.set(0);
		FacetOutlineStats.STRIPS_SKIPPED_CULLED.set(0);
	}

	@Test
	void fullCubeServesCachedStrips() {
		List<String> strips = collect(Shapes.block(), WIDTH);

		assertEquals(24, strips.size());
		assertEquals(24, FacetOutlineStats.FULL_CUBE_STRIPS_SERVED.get());
	}

	@Test
	void partialShapeReusesCachedStripsForSameShapeAndWidth() {
		VoxelShape slab = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
		List<String> first = collect(slab, WIDTH);
		long missesAfterFirst = FacetOutlineStats.PARTIAL_CACHE_MISSES.get();
		List<String> second = collect(slab, WIDTH);

		assertEquals(first, second);
		assertEquals(missesAfterFirst, FacetOutlineStats.PARTIAL_CACHE_MISSES.get());
		assertTrue(FacetOutlineStats.PARTIAL_CACHE_HITS.get() > 0);
	}

	@Test
	void partialShapeStripsDifferByWidth() {
		VoxelShape slab = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

		assertNotEquals(collect(slab, 1.0 / 64.0), collect(slab, 2.0 / 64.0));
	}

	@Test
	void faceMaskPreservesLegacyBoundaryCullingForCubesSlabsAndStairs() {
		VoxelShape[] shapes = {
				Shapes.block(),
				Shapes.box(0, 0, 0, 1, 0.5, 1),
				Shapes.box(0, 0.5, 0, 1, 1, 1),
				Shapes.or(Shapes.box(0, 0, 0, 1, 0.5, 1), Shapes.box(0, 0.5, 0, 0.5, 1, 1))
		};
		for (VoxelShape shape : shapes) {
			for (double width : new double[] {WIDTH, 3.0 / 32.0}) {
				for (int mask = 0; mask < 64; mask++) {
					int culledFaces = mask;
					List<String> expected = new ArrayList<>();
					FacetShapeEdges.forEachSurfaceStrip(shape, width, (face, x1, y1, z1, x2, y2, z2) -> {
						if ((culledFaces & (1 << face.ordinal())) == 0
								|| !FacetOutlineRules.touchesBlockBoundary(face, x1, y1, z1, x2, y2, z2)) {
							expected.add(face + " " + x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2);
						}
					});
					assertEquals(expected, collect(shape, width, mask));
				}
			}
		}
		assertTrue(!collect(shapes[1], WIDTH, 63).isEmpty(), "Slab interior surface must remain visible");
	}

	@Test
	void cubeSkipsWholeFacesAndAccountsForSkippedStrips() {
		assertEquals(16, collect(Shapes.block(), WIDTH, 3).size());
		assertEquals(24, FacetOutlineStats.FULL_CUBE_STRIPS_SERVED.get());
		assertEquals(8, FacetOutlineStats.STRIPS_SKIPPED_CULLED.get());
	}

	private static List<String> collect(VoxelShape shape, double width) {
		return collect(shape, width, 0);
	}

	private static List<String> collect(VoxelShape shape, double width, int culledFaces) {
		List<String> strips = new ArrayList<>();
		FacetShapeEdges.forEachSurfaceStrip(shape, width, culledFaces,
				(face, minX, minY, minZ, maxX, maxY, maxZ) ->
						strips.add(face + " " + minX + "," + minY + "," + minZ
								+ "," + maxX + "," + maxY + "," + maxZ));
		return strips;
	}
}

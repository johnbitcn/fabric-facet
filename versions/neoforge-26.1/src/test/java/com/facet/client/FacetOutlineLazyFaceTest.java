package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetOutlineLazyFaceTest {
	@Test
	void onlyRequestedFacesAreBuiltAndRepeatedRequestsReuseQuads() {
		var model = new FacetNeoForgeOutlineRenderer.OutlineModel(null, null, Map.of(), false, null);
		int[] calls = new int[6];
		// Test the lazy dispatch without starting FML or allocating GPU-backed materials.
		var part = model.new FullCubeOutlinePart(null, FacetOutlineRules.DEFAULT_EDGE_WIDTH) {
			@Override
			List<BakedQuad> bakeFace(Direction face) {
				calls[face.ordinal()]++;
				return new ArrayList<>();
			}
		};
		assertTrue(part.getQuads(null).isEmpty());
		assertEquals(0, part.generatedFaceCount());
		var north = part.getQuads(Direction.NORTH);
		assertEquals(1, part.generatedFaceCount());
		assertSame(north, part.getQuads(Direction.NORTH));
		part.getQuads(Direction.UP);
		assertEquals(2, part.generatedFaceCount());
		for (Direction face : Direction.values()) {
			assertEquals(face == Direction.NORTH || face == Direction.UP ? 1 : 0, calls[face.ordinal()]);
		}
		assertTrue(part.getQuads(null).isEmpty());
		assertEquals(0, part.materialFlags());
		assertTrue(part.useAmbientOcclusion());
	}

	@Test
	void rainbowPartKeepsAnimationFlagsWithoutResolvingOrdinaryColors() {
		var model = new FacetNeoForgeOutlineRenderer.OutlineModel(null, null, Map.of(), true, null);
		var part = model.new FullCubeOutlinePart(null, FacetOutlineRules.ANCIENT_DEBRIS_EDGE_WIDTH);
		assertEquals(3.0 / 32.0, FacetOutlineRules.ANCIENT_DEBRIS_EDGE_WIDTH);
		assertEquals(BakedQuad.FLAG_ANIMATED, part.materialFlags());
		assertTrue(part.getQuads(null).isEmpty());
		assertEquals(0, part.generatedFaceCount());
	}

	@Test
	void faceColorsArePreparedOnceWithoutChangingRgb() {
		var colors = new EnumMap<Direction, Integer>(Direction.class);
		for (Direction face : Direction.values()) {
			colors.put(face, 0xFF123456);
		}
		var prepared = new FacetOutlineColor.FaceColors(colors);
		for (Direction face : Direction.values()) {
			assertEquals(0xFE123456, prepared.color(face));
			assertEquals(0xFE123456, prepared.color(face));
		}
	}
}

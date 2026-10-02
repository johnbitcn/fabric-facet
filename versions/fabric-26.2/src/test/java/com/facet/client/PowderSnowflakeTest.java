package com.facet.client;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowderSnowflakeTest {
	@Test
	void eachFaceHasCenteredDecalsAtBothSizesWithOutwardWinding() throws Exception {
		Class<?> model = Class.forName("com.facet.client.FacetBlockOverlay$OutlineBlockStateModel");
		var method = model.getDeclaredMethod("emitFaceDecal", QuadEmitter.class, Direction.class,
				double.class, Material.Baked.class, double.class, ChunkSectionLayer.class);
		method.setAccessible(true);
		for (int index = 0; index < 24; index++) {
			Direction face = Direction.values()[index % 6];
			double size = index < 12 ? 0.70 : 0.35;
			ChunkSectionLayer layer = index / 6 % 2 == 0 ? ChunkSectionLayer.CUTOUT : ChunkSectionLayer.TRANSLUCENT;
			float[][] vertices = new float[4][3];
			List<Object> layers = new ArrayList<>();
			int[] emitted = {0};
			QuadEmitter emitter = (QuadEmitter) Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),
					new Class<?>[] {QuadEmitter.class}, (proxy, called, args) -> {
				if (called.getName().equals("pos")) {
					vertices[(int) args[0]] = new float[] {(float) args[1], (float) args[2], (float) args[3]};
				} else if (called.getName().equals("chunkLayer")) {
					layers.add(args[0]);
				} else if (called.getName().equals("emit")) {
					emitted[0]++;
				}
				return proxy;
			});
			double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
			method.invoke(null, emitter, face, plane, null, size, layer);
			assertEquals(1, emitted[0]);
			assertEquals(List.of(layer), layers);
			int axis = face.getAxis().ordinal();
			for (int coordinate = 0; coordinate < 3; coordinate++) {
				if (coordinate == axis) {
					for (float[] vertex : vertices) {
						double bias = layer == ChunkSectionLayer.TRANSLUCENT ? 1.0 / 512.0 : FacetMcBridge.outlineSurfaceBias();
						assertEquals(plane + bias * face.getAxisDirection().getStep(), vertex[coordinate], 1e-6);
					}
				} else {
					float min = 1, max = 0;
					for (float[] vertex : vertices) {
						min = Math.min(min, vertex[coordinate]);
						max = Math.max(max, vertex[coordinate]);
					}
					assertEquals((1.0 - size) / 2.0, min, 1e-6);
					assertEquals((1.0 + size) / 2.0, max, 1e-6);
				}
			}
			float[] a = vertices[0], b = vertices[1], c = vertices[2];
			double nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
			double ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
			double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
			assertTrue(nx * face.getStepX() + ny * face.getStepY() + nz * face.getStepZ() > 0);
		}
	}
}

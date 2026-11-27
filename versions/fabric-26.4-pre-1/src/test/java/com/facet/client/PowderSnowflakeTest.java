package com.facet.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3fc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowderSnowflakeTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void eachFaceHasCenteredDecalsAtBothSizesWithOutwardWinding() throws Exception {
		var method = FacetBlockOverlay.class.getDeclaredMethod("createFaceDecal",
				Direction.class, double.class, Material.Baked.class, double.class, ChunkSectionLayer.class);
		method.setAccessible(true);

		try (var fixtures = new BakedOutlineTestFixtures()) {
			Material.Baked material = fixtures.material("facet:block/rainbow_snowflake", 16, 0xFFFFFFFF, 0);
			for (int index = 0; index < 24; index++) {
				Direction face = Direction.values()[index % 6];
				double size = index < 12 ? 0.70 : 0.35;
				ChunkSectionLayer layer = index / 6 % 2 == 0 ? ChunkSectionLayer.CUTOUT : ChunkSectionLayer.TRANSLUCENT;
				double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
				BakedQuad quad = (BakedQuad) method.invoke(null, face, plane, material, size, layer);

				assertEquals(face, quad.direction());
				assertEquals(layer, quad.materialInfo().layer());
				assertEquals(-1, quad.materialInfo().tintIndex());
				assertEquals(0, quad.materialInfo().lightEmission());
				assertNull(quad.materialInfo().shadeDirectionOverride());

				float[][] vertices = new float[BakedQuad.VERTEX_COUNT][3];
				for (int v = 0; v < BakedQuad.VERTEX_COUNT; v++) {
					Vector3fc pos = quad.position(v);
					vertices[v] = new float[] { pos.x(), pos.y(), pos.z() };
				}

				int axis = face.getAxis().ordinal();
				for (int coordinate = 0; coordinate < 3; coordinate++) {
					if (coordinate == axis) {
						double bias = layer == ChunkSectionLayer.TRANSLUCENT
								? 1.0 / 512.0 : FacetMcBridge.outlineSurfaceBias();
						for (float[] vertex : vertices) {
							assertEquals(plane + bias * face.getAxisDirection().getStep(), vertex[coordinate], 1e-6);
						}
					} else {
						float min = 1.0f;
						float max = 0.0f;
						for (float[] vertex : vertices) {
							min = Math.min(min, vertex[coordinate]);
							max = Math.max(max, vertex[coordinate]);
						}
						assertEquals((1.0 - size) / 2.0, min, 1e-6);
						assertEquals((1.0 + size) / 2.0, max, 1e-6);
					}
				}

				float[] a = vertices[0];
				float[] b = vertices[1];
				float[] c = vertices[2];
				double nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
				double ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
				double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
				assertTrue(nx * face.getStepX() + ny * face.getStepY() + nz * face.getStepZ() > 0);
			}
		}
	}
}

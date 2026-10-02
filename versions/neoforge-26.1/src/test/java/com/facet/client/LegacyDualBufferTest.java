package com.facet.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDualBufferTest {
	@Test
	void requestingSecondMaterialDoesNotEndTheFirstConsumer() throws Exception {
		var platform = FacetNeoForgePlatform.class;
		RenderType firstType = renderType("facet_test_first");
		RenderType secondType = renderType("facet_test_second");

		// Reproduce the old failure without drawing anything or creating a GPU device.
		try (var backing = new ByteBufferBuilder(1024)) {
			var shared = MultiBufferSource.immediate(backing);
			var ended = shared.getBuffer(firstType);
			shared.getBuffer(secondType);
			assertThrows(IllegalStateException.class, () -> ended.addVertex(0, 0, 0));
			shared.endBatch();
		}

		var firstSource = source(platform, "AFTER_TERRAIN_PRIMARY_SOURCE");
		var secondSource = source(platform, "AFTER_TERRAIN_SECONDARY_SOURCE");
		assertNotSame(firstSource, secondSource);
		var first = firstSource.getBuffer(firstType);
		var second = secondSource.getBuffer(secondType);
		assertDoesNotThrow(() -> first.addVertex(0, 0, 0));
		assertDoesNotThrow(() -> second.addVertex(0, 0, 0));
		// These test batches remain CPU-only; the test JVM owns their lifetime.
	}

	private static MultiBufferSource.BufferSource source(Class<?> platform, String name) throws Exception {
		var field = platform.getDeclaredField(name);
		field.setAccessible(true);
		return (MultiBufferSource.BufferSource) field.get(null);
	}

	private static RenderType renderType(String name) throws Exception {
		var pipeline = RenderPipeline.builder().withLocation(name).withVertexShader("test").withFragmentShader("test")
				.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS).build();
		var setup = RenderSetup.builder(pipeline).createRenderSetup();
		var create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
		create.setAccessible(true);
		return (RenderType) create.invoke(null, name, setup);
	}
}

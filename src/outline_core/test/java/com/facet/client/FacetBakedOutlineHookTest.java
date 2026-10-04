package com.facet.client;

import java.util.List;
import java.util.concurrent.Executor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verify private hook signatures against each target, not just compile-time APIs. */
class FacetBakedOutlineHookTest {
	@Test
	void atlasHookAndAccessorsMatchNativeClasses() throws ReflectiveOperationException {
		assertEquals(SpriteLoader.Preparations.class,
				SpriteLoader.class.getDeclaredMethod("stitch", List.class, int.class, Executor.class).getReturnType());
		assertEquals(Identifier.class, SpriteLoader.class.getDeclaredField("location").getType());
		assertEquals(NativeImage.class, SpriteContents.class.getDeclaredField("originalImage").getType());
		assertEquals(List.class, SpriteContents.class.getDeclaredField("additionalMetadata").getType());
		assertEquals(MipmapStrategy.class, SpriteContents.class.getDeclaredField("mipmapStrategy").getType());
		assertEquals(float.class, SpriteContents.class.getDeclaredField("alphaCutoffBias").getType());
		assertEquals(WeightedList.class, WeightedVariants.class.getDeclaredField("list").getType());
	}
}

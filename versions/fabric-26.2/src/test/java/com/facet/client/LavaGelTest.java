package com.facet.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.SpriteContents;

class LavaGelTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void onlyLavaChangesWhenOutlinesAreEnabledAndVanillaReturnsWhenDisabled() {
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, (BlockTintSource) null);
		var gel = new FluidModel(ChunkSectionLayer.TRANSLUCENT, null, null, null, (BlockTintSource) null);
		for (var fluid : new net.minecraft.world.level.material.Fluid[] {Fluids.LAVA, Fluids.FLOWING_LAVA}) {
			assertSame(gel, LavaGel.selectModel(true, fluid.defaultFluidState(), original, gel));
			assertSame(original, LavaGel.selectModel(false, fluid.defaultFluidState(), original, gel));
			assertSame(original, LavaGel.selectModel(true, fluid.defaultFluidState(), original, null));
		}
		for (var fluid : new net.minecraft.world.level.material.Fluid[] {Fluids.WATER, Fluids.FLOWING_WATER, Fluids.EMPTY}) {
			assertSame(original, LavaGel.selectModel(true, fluid.defaultFluidState(), original, gel));
		}
	}

	@Test
	void spriteInjectionSelectsAnExplicitExistingConstructor() throws ClassNotFoundException {
		var injection = Arrays.stream(Class.forName("com.facet.client.mixin.LavaGelSpriteMixin").getDeclaredMethods())
				.map(method -> method.getAnnotation(WrapOperation.class))
				.filter(Objects::nonNull).findFirst().orElseThrow();
		assertEquals(1, injection.method().length);
		assertTrue(Arrays.stream(SpriteContents.class.getDeclaredConstructors()).anyMatch(constructor ->
				injection.method()[0].equals("<init>" + MethodType.methodType(void.class,
						constructor.getParameterTypes()).descriptorString())));
	}

	@Test
	void transparencyPreservesOriginalLavaColorsAndTransparentPixels() {
		int[] original = {0xFFFF4500, 0xFFC83105, 0x80F04A09, 0x000C0201};
		try (NativeImage image = new NativeImage(2, 2, false)) {
			for (int i = 0; i < original.length; i++) image.setPixel(i % 2, i / 2, original[i]);
			LavaGel.applyTransparency(image);
			for (int i = 0; i < original.length; i++) {
				int actual = image.getPixel(i % 2, i / 2);
				assertEquals(original[i] & 0xFFFFFF, actual & 0xFFFFFF);
				assertEquals(ARGB.alpha(original[i]) * LavaGel.ALPHA / 255, ARGB.alpha(actual));
			}
			assertEquals(com.mojang.blaze3d.platform.Transparency.TRANSPARENT_AND_TRANSLUCENT, image.computeTransparency());
		}
	}

	@Test
	void lavaInteriorHasClearerFogOnlyWhileOutlinesAreEnabled() {
		try {
			for (boolean enabled : new boolean[] {false, true}) {
				LavaGel.initialize(() -> enabled);
				var fog = new net.minecraft.client.renderer.fog.FogData();
				fog.environmentalStart = 0.25F;
				fog.environmentalEnd = fog.skyEnd = fog.cloudEnd = 1.0F;
				fog.renderDistanceEnd = 120.0F;
				fog.color.set(0.6F, 0.1F, 0, 1);
				LavaGel.softenFog(fog);
				assertEquals(enabled ? 4.0F : 0.25F, fog.environmentalStart);
				assertEquals(enabled ? 24.0F : 1.0F, fog.environmentalEnd);
				assertEquals(fog.environmentalEnd, fog.skyEnd);
				assertEquals(fog.environmentalEnd, fog.cloudEnd);
				assertEquals(120.0F, fog.renderDistanceEnd);
				assertEquals(0.6F, fog.color.x);
				assertEquals(0.1F, fog.color.y);
			}
		} finally {
			LavaGel.initialize(() -> false);
		}
	}
}

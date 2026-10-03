package com.facet.client;

import java.util.function.BooleanSupplier;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Cosmetic lava model; vanilla still controls fluid geometry, lighting and gameplay. */
public final class LavaGel {
	private static final Material STILL = new Material(Identifier.fromNamespaceAndPath("facet", "block/lava_gel_still"));
	private static final Material FLOW = new Material(Identifier.fromNamespaceAndPath("facet", "block/lava_gel_flow"));
	static final int ALPHA = 110;
	private static volatile BooleanSupplier outlinesEnabled = () -> false;
	private static volatile FluidModel gelModel;

	private LavaGel() {
	}

	public static void initialize(BooleanSupplier enabled) {
		outlinesEnabled = enabled;
	}

	public static void applyTransparency(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int original = image.getPixel(x, y);
				image.setPixel(x, y, (original & 0xFFFFFF) | (ARGB.alpha(original) * ALPHA / 255 << 24));
			}
		}
	}

	public static void softenFog(FogData fog) {
		if (!outlinesEnabled.getAsBoolean()) {
			return;
		}
		fog.environmentalStart = Math.max(fog.environmentalStart, 4.0F);
		fog.environmentalEnd = Math.max(fog.environmentalEnd, 24.0F);
		fog.skyEnd = fog.environmentalEnd;
		fog.cloudEnd = fog.environmentalEnd;
	}

	public static void bake(MaterialBaker materials) {
		gelModel = new FluidModel(ChunkSectionLayer.TRANSLUCENT,
				materials.get(STILL, () -> "Facet lava gel"),
				materials.get(FLOW, () -> "Facet flowing lava gel"), null, (BlockTintSource) null);
	}

	public static FluidModel modelFor(FluidState state, FluidModel original) {
		return selectModel(outlinesEnabled.getAsBoolean(), state, original, gelModel);
	}

	static FluidModel selectModel(boolean enabled, FluidState state, FluidModel original, FluidModel gel) {
		boolean lava = state.getType() == Fluids.LAVA || state.getType() == Fluids.FLOWING_LAVA;
		return enabled && lava && gel != null ? gel : original;
	}
}

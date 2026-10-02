package com.facet.client;

import java.util.function.BooleanSupplier;

import net.minecraft.client.renderer.state.LightmapRenderState;

public final class HologramNightVision {
	private static BooleanSupplier enabled = () -> false;

	private HologramNightVision() {
	}

	public static void initialize(BooleanSupplier enabledSupplier) {
		enabled = enabledSupplier;
	}

	public static void apply(LightmapRenderState state) {
		if (enabled.getAsBoolean()) {
			state.nightVisionEffectIntensity = 1.0f;
		}
	}
}

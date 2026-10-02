package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.renderer.state.LightmapRenderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class HologramNightVisionTest {
	@AfterEach
	void reset() {
		HologramNightVision.initialize(() -> false);
	}

	@Test
	void followsTheLiveToggleWithoutChangingVanillaEffectsWhenDisabled() {
		boolean[] enabled = {false};
		HologramNightVision.initialize(() -> enabled[0]);
		LightmapRenderState state = new LightmapRenderState();
		state.nightVisionEffectIntensity = 0.4f;
		state.darknessEffectScale = 0.3f;
		HologramNightVision.apply(state);
		assertEquals(0.4f, state.nightVisionEffectIntensity);

		enabled[0] = true;
		HologramNightVision.apply(state);
		assertEquals(1.0f, state.nightVisionEffectIntensity);
		assertEquals(0.3f, state.darknessEffectScale);

		enabled[0] = false;
		// Vanilla extraction writes the current potion intensity before our hook.
		state.nightVisionEffectIntensity = 0.4f;
		HologramNightVision.apply(state);
		assertEquals(0.4f, state.nightVisionEffectIntensity);
		state.nightVisionEffectIntensity = 0.0f;
		HologramNightVision.apply(state);
		assertEquals(0.0f, state.nightVisionEffectIntensity);
	}
}

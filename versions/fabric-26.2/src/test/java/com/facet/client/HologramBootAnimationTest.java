package com.facet.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class HologramBootAnimationTest {
	private static final long START = 1_000_000_000L;

	@AfterEach
	void reset() {
		HologramBootAnimation.reset();
	}

	@Test
	void delaysThenRevealsAndSettlesAtTheExactFinalPose() {
		HologramBootAnimation.start(START);

		HologramBootAnimation.Frame delayed = HologramBootAnimation.frame(
				START + HologramBootAnimation.DELAY_NANOS - 1L);
		assertFalse(delayed.visible());
		assertTrue(delayed.active());
		assertEquals(0.0f, delayed.alphaScale());

		HologramBootAnimation.Frame surge = HologramBootAnimation.frame(
				START + HologramBootAnimation.DELAY_NANOS);
		assertTrue(surge.visible());
		assertTrue(surge.active());
		assertEquals(1.8f, surge.alphaScale(), 1.0e-6f);

		HologramBootAnimation.Frame afterSurge = HologramBootAnimation.frame(
				START + HologramBootAnimation.DELAY_NANOS + HologramBootAnimation.SURGE_NANOS);
		assertTrue(afterSurge.alphaScale() < 0.1f);

		HologramBootAnimation.Frame halfway = HologramBootAnimation.frame(
				START + HologramBootAnimation.DELAY_NANOS + HologramBootAnimation.REVEAL_NANOS / 2L);
		assertTrue(halfway.visible());
		assertTrue(halfway.active());
		assertEquals(0.5f, halfway.alphaScale(), 1.0e-6f);

		HologramBootAnimation.Frame complete = HologramBootAnimation.frame(
				START + HologramBootAnimation.DELAY_NANOS + HologramBootAnimation.REVEAL_NANOS);
		assertTrue(complete.visible());
		assertFalse(complete.active());
		assertEquals(1.0f, complete.alphaScale());
		assertEquals(0.0f, complete.horizontalOffset());
		assertEquals(0.0f, complete.verticalOffset());
	}
}

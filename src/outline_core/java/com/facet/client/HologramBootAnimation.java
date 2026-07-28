package com.facet.client;

final class HologramBootAnimation {
	static final long DELAY_NANOS = 300_000_000L;
	static final long REVEAL_NANOS = 1_000_000_000L;
	static final long SURGE_NANOS = 120_000_000L;
	private static final float SURGE_ALPHA_SCALE = 1.8f;
	private static final float MAX_HORIZONTAL_JITTER = 0.034f;
	private static final float MAX_VERTICAL_JITTER = 0.022f;
	private static long startedAtNanos = Long.MIN_VALUE;

	private HologramBootAnimation() {
	}

	static void start() {
		start(System.nanoTime());
	}

	static void start(long timeNanos) {
		startedAtNanos = timeNanos;
	}

	static void reset() {
		startedAtNanos = Long.MIN_VALUE;
	}

	static Frame frame(long timeNanos) {
		if (startedAtNanos == Long.MIN_VALUE) {
			return Frame.READY;
		}

		long elapsed = Math.max(0L, timeNanos - startedAtNanos);
		if (elapsed < DELAY_NANOS) {
			return Frame.HIDDEN;
		}

		float progress = Math.min(1.0f, (float) (elapsed - DELAY_NANOS) / REVEAL_NANOS);
		if (progress >= 1.0f) {
			reset();
			return Frame.READY;
		}

		float reveal = progress * progress * (3.0f - 2.0f * progress);
		float surgeProgress = Math.min(1.0f, (float) (elapsed - DELAY_NANOS) / SURGE_NANOS);
		float surge = 1.0f - surgeProgress;
		surge = surge * surge * SURGE_ALPHA_SCALE;
		float alphaScale = Math.max(reveal, surge);
		float jitterEnvelope = 1.0f - reveal;
		double seconds = (elapsed - DELAY_NANOS) / 1_000_000_000.0;
		float horizontal = (float) ((Math.sin(seconds * Math.PI * 62.0)
				+ 0.45 * Math.sin(seconds * Math.PI * 91.0 + 0.8))
				* MAX_HORIZONTAL_JITTER * jitterEnvelope);
		float vertical = (float) ((Math.sin(seconds * Math.PI * 74.0 + 1.7)
				+ 0.35 * Math.sin(seconds * Math.PI * 113.0))
				* MAX_VERTICAL_JITTER * jitterEnvelope);
		return new Frame(true, true, alphaScale, horizontal, vertical);
	}

	record Frame(boolean visible, boolean active, float alphaScale, float horizontalOffset, float verticalOffset) {
		private static final Frame HIDDEN = new Frame(false, true, 0.0f, 0.0f, 0.0f);
		private static final Frame READY = new Frame(true, false, 1.0f, 0.0f, 0.0f);
	}
}

package com.facet.client;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic counters for the mesh-outline path. Active only with
 * {@code -Dfacet.outlineStats=true}; all counters and the rolling log are no-ops otherwise.
 * Referenced only by Fabric-side hooks ({@code FacetBlockOverlay}, {@code FacetClient}),
 * so it compiles inert into loader targets without those hooks (NeoForge).
 */
final class FacetOutlineStats {
	private static final boolean ENABLED = System.getProperty("facet.outlineStats") != null;
	private static final Logger LOGGER = LoggerFactory.getLogger("facet");
	private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;
	private static long lastLogNanos;

	static final AtomicLong STRIPS_EMITTED = new AtomicLong();
	static final AtomicLong STRIPS_SKIPPED_CULLED = new AtomicLong();
	static final AtomicLong STRIPS_SKIPPED_CARPET = new AtomicLong();
	static final AtomicLong PARTIAL_CACHE_HITS = new AtomicLong();
	static final AtomicLong PARTIAL_CACHE_MISSES = new AtomicLong();
	static final AtomicLong PARTIAL_STRIPS_SERVED = new AtomicLong();
	static final AtomicLong FULL_CUBE_STRIPS_SERVED = new AtomicLong();

	private static volatile boolean forceEnabled;

	private FacetOutlineStats() {
	}

	static boolean enabled() {
		return ENABLED || forceEnabled;
	}

	static void setEnabledForTesting(boolean enabled) {
		forceEnabled = enabled;
	}

	/** Rolling summary; called from the client tick while enabled. */
	static void tick() {
		if (!enabled()) {
			return;
		}

		long now = System.nanoTime();

		if (now - lastLogNanos < LOG_INTERVAL_NANOS) {
			return;
		}

		lastLogNanos = now;
		LOGGER.info("outline stats: strips emitted={} culled={} carpetSkipped={} | "
						+ "partial cache hits={} misses={} served={} | fullCube served={}",
				STRIPS_EMITTED.get(),
				STRIPS_SKIPPED_CULLED.get(),
				STRIPS_SKIPPED_CARPET.get(),
				PARTIAL_CACHE_HITS.get(),
				PARTIAL_CACHE_MISSES.get(),
				PARTIAL_STRIPS_SERVED.get(),
				FULL_CUBE_STRIPS_SERVED.get());
	}
}

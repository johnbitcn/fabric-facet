package com.facet.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MouseScrollPolicyTest {
	@Test
	void toggleOnlyBlocksGameplayAndNeverScreens() {
		for (boolean disabled : new boolean[] {false, true}) {
			for (boolean inWorld : new boolean[] {false, true}) {
				for (boolean screenOpen : new boolean[] {false, true}) {
					assertEquals(disabled && inWorld && !screenOpen,
							MouseScrollPolicy.shouldBlock(disabled, inWorld, screenOpen));
				}
			}
		}
	}
}

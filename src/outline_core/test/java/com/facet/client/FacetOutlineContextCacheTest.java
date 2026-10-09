package com.facet.client;

import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verify the bound without bootstrapping a game world or accessing block entities. */
class FacetOutlineContextCacheTest {
	@Test
	@SuppressWarnings("unchecked")
	void contextPlansAreBoundedLruAndClearedOnResourceReload() throws ReflectiveOperationException {
		var field = FacetOutlineColor.class.getDeclaredField("CONTEXT_PLANS");
		field.setAccessible(true);
		var plans = (Map<Object, Object>) field.get(null);
		var keyType = Class.forName("com.facet.client.FacetOutlineColor$ContextPlanKey");
		var constructor = keyType.getDeclaredConstructor(BlockState.class, Object.class);
		constructor.setAccessible(true);
		FacetOutlineColor.clearCache();
		try {
			for (int i = 0; i < 1024; i++) plans.put(constructor.newInstance(null, i), null);
			var first = constructor.newInstance(null, 0);
			plans.get(first); // Access promotes the oldest entry.
			plans.put(constructor.newInstance(null, 1024), null);
			assertEquals(1024, plans.size());
			assertTrue(plans.containsKey(first));
			assertFalse(plans.containsKey(constructor.newInstance(null, 1)));
			FacetOutlineColor.clearCache();
			assertTrue(plans.isEmpty());
		} finally {
			FacetOutlineColor.clearCache();
		}
	}
}

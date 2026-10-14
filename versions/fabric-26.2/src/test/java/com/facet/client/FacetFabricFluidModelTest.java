package com.facet.client;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetFabricFluidModelTest {
	@Test
	void preservesTheVanillaTintSourceAndItsWorldSpecificBehavior() {
		BlockTintSource tint = new BlockTintSource() {
			@Override public int color(net.minecraft.world.level.block.state.BlockState state) { return 0xFF112233; }
			@Override
			public int colorInWorld(net.minecraft.world.level.block.state.BlockState state,
					net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockPos pos) {
				return pos.getX() == 10 ? 0xFF445566 : 0xFF778899;
			}
		};
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, tint);
		var replacement = FacetFluidModelPlatform.retexture(original, null, null, null);
		assertSame(tint, replacement.tintSource());
		assertEquals(0xFF445566, replacement.tintSource().colorInWorld(null, null, new BlockPos(10, 64, 0)));
		assertEquals(0xFF778899, replacement.tintSource().colorInWorld(null, null, BlockPos.ZERO));
		assertEquals(ChunkSectionLayer.SOLID, original.layer());
	}

	@Test
	void missingOriginalDoesNotIntroduceTint() {
		assertNull(FacetFluidModelPlatform.retexture(null, null, null, null).tintSource());
	}
}

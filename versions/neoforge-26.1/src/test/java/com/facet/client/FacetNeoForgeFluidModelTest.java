package com.facet.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetNeoForgeFluidModelTest {
	@Test
	void preservesFluidTintIncludingItsWorldSpecificBehavior() {
		FluidTintSource tint = new FluidTintSource() {
			@Override public int color(FluidState state) { return 0xFF112233; }
			@Override
			public int colorInWorld(FluidState fluid, net.minecraft.world.level.block.state.BlockState state,
					net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockPos pos) {
				return pos.getX() == 10 ? 0xFF445566 : 0xFF778899;
			}
		};
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, tint, null);
		var replacement = FacetFluidModelPlatform.retexture(original, null, null, null);
		assertSame(tint, replacement.fluidTintSource());
		assertEquals(0xFF112233, replacement.fluidTintSource().color((FluidState) null));
		assertEquals(0xFF445566, replacement.fluidTintSource().colorInWorld(null, null, null, new BlockPos(10, 64, 0)));
		assertEquals(0xFF778899, replacement.fluidTintSource().colorInWorld(null, null, null, BlockPos.ZERO));
		assertEquals(ChunkSectionLayer.SOLID, original.layer());
	}

	@Test
	void preservesCustomRenderingWithoutExecutingItDuringBaking() {
		int[] calls = {0};
		CustomFluidRenderer renderer = (fluidRenderer, fluid, level, pos, output, state) -> {
			calls[0]++;
			return true;
		};
		var original = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, null, renderer);
		var replacement = FacetFluidModelPlatform.retexture(original, null, null, null);
		assertSame(renderer, replacement.customRenderer());
		assertEquals(0, calls[0]);
		assertTrue(replacement.customRenderer().renderFluid(null, null, null, null, null, null));
		assertEquals(1, calls[0]);
		assertSame(renderer, original.customRenderer());
	}

	@Test
	void missingOriginalDoesNotIntroduceTintOrACustomRenderer() {
		var replacement = FacetFluidModelPlatform.retexture(null, null, null, null);
		assertNull(replacement.fluidTintSource());
		assertNull(replacement.customRenderer());
	}
}

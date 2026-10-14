package com.facet.client;

import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric retains the vanilla, context-free collection API. */
final class FacetModelParts {
	private FacetModelParts() {}

	static boolean collectStatic(BlockStateModel model, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		model.collectParts(random, parts);
		return true;
	}

	static void collect(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		model.collectParts(random, parts);
	}

	static Object geometryKey(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random) {
		return null;
	}
}

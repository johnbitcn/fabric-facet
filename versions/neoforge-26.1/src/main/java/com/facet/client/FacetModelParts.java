package com.facet.client;

import java.util.List;
import com.facet.client.mixin.WeightedVariantsAccessor;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** All four supported NeoForge versions expose these contextual collection APIs. */
final class FacetModelParts {
	private FacetModelParts() {}

	static boolean collectStatic(BlockStateModel model, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		if (!isStatic(model)) return false;
		collectKnownStatic(model, random, parts);
		return true;
	}

	private static boolean isStatic(BlockStateModel model) {
		if (model.getClass() == SingleVariant.class) return true;
		if (model.getClass() == WeightedVariants.class && model instanceof WeightedVariantsAccessor variants) {
			return variants.facet$getVariants().unwrap().stream().allMatch(entry -> isStatic(entry.value()));
		}
		if (model.getClass() == MultiPartModel.class && model instanceof StaticModelChildren children) {
			return children.facet$staticChildren().stream().allMatch(FacetModelParts::isStatic);
		}
		return false;
	}

	private static void collectKnownStatic(BlockStateModel model, RandomSource random,
			List<BlockStateModelPart> parts) {
		if (model.getClass() == SingleVariant.class) {
			// This concrete vanilla implementation directly appends its static part.
			((SingleVariant) model).collectParts(random, parts);
		} else if (model instanceof WeightedVariantsAccessor variants) {
			collectKnownStatic(variants.facet$getVariants().getRandomOrThrow(random), random, parts);
		} else {
			long seed = random.nextLong();
			for (BlockStateModel child : ((StaticModelChildren) model).facet$staticChildren()) {
				random.setSeed(seed);
				collectKnownStatic(child, random, parts);
			}
		}
	}

	static void collect(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		model.collectParts(level, pos, state, random, parts);
	}

	static Object geometryKey(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random) {
		return model.createGeometryKey(level, pos, state, random);
	}
}

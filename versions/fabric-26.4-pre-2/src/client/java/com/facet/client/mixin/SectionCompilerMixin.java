package com.facet.client.mixin;

import com.facet.client.FacetBlockOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
	@WrapOperation(method = "compile("
			+ "Lnet/minecraft/core/SectionPos;"
			+ "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;"
			+ "Lcom/mojang/blaze3d/vertex/VertexSorting;"
			+ "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
			+ ")Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock("
							+ "Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
							+ "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
							+ "Lnet/minecraft/core/BlockPos;"
							+ "Lnet/minecraft/world/level/block/state/BlockState;"
							+ "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void facet$preserveLayerOutputForOverlays(ModelBlockRenderer renderer, BlockQuadOutput output,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state,
			BlockStateModel model, long seed, Operation<Void> original,
			@Local(ordinal = 0) BlockQuadOutput layerOutput) {
		FacetBlockOverlay.pushLayerOutput(layerOutput);
		try {
			original.call(renderer, output, x, y, z, level, pos, state, model, seed);
		} finally {
			FacetBlockOverlay.popLayerOutput();
		}
	}
}

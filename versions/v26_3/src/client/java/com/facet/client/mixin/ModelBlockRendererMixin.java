package com.facet.client.mixin;

import java.util.List;

import com.facet.client.FacetBlockOverlay;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
	@Shadow
	@Final
	private List<BlockStateModelPart> parts;

	@Inject(method = "tesselateBlock", at = @At("HEAD"))
	private void facet$beginBlock(BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			CallbackInfo callbackInfo) {
		FacetBlockOverlay.beginBlock();
	}

	@Inject(
			method = "tesselateBlock",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V",
					shift = At.Shift.AFTER))
	private void facet$appendParts(BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			CallbackInfo callbackInfo) {
		FacetBlockOverlay.appendParts(parts, level, pos, state);
	}

	@Redirect(
			method = "putQuadWithTint",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
	private void facet$applyOutlineColor(BlockQuadOutput output, float x, float y, float z,
			BakedQuad quad, QuadInstance instance) {
		FacetBlockOverlay.applyOutlineColor(quad, instance);
		output.put(x, y, z, quad, instance);
	}

	@Inject(method = "tesselateBlock", at = @At("RETURN"))
	private void facet$finishBlock(BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			CallbackInfo callbackInfo) {
		FacetBlockOverlay.finishBlock();
	}
}

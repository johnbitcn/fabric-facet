package com.facet.client.mixin;

import com.facet.client.FacetBlockOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
	@Unique private static final Direction[] FACET$DIRECTIONS = Direction.values();

	@Shadow @Final private BlockModelLighter lighter;
	@Shadow @Final private boolean ambientOcclusion;
	@Shadow @Final private BlockPos.MutableBlockPos scratchPos;
	@Shadow @Final private QuadInstance quadInstance;

	@Shadow
	private boolean shouldRenderFace(BlockAndTintGetter level, BlockState state, Direction face, BlockPos neighborPos) {
		throw new AssertionError();
	}

	@WrapMethod(method = "tesselateBlock("
			+ "Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
			+ "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
			+ "Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;"
			+ "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V")
	private void facet$tesselateWithOutlines(BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			Operation<Void> original) {
		if (!(model instanceof FacetBlockOverlay.OutlineBlockStateModel outlineModel)) {
			original.call(output, x, y, z, level, pos, state, model, seed);
			return;
		}

		if (outlineModel.brittleIce()) {
			original.call(output, x, y, z, level, pos, state, outlineModel.selectBrittleIceModel(state), seed);
			if (outlineModel.hasGraffiti(pos, state)) {
				facet$emitOverlays(output, x, y, z, level, pos, state, seed, outlineModel, false);
			}
			return;
		}

		BlockStateModel bakedModel = outlineModel.selectBakedModel(level, pos, state);
		if (bakedModel != null) {
			original.call(output, x, y, z, level, pos, state, bakedModel, seed);
			if (outlineModel.hasGraffiti(pos, state)) {
				facet$emitOverlays(output, x, y, z, level, pos, state, seed, outlineModel, false);
			}
			return;
		}

		original.call(output, x, y, z, level, pos, state, outlineModel.selectFallbackBaseModel(), seed);
		boolean emitOutline = outlineModel.shouldEmitOutline(level, pos, state);
		if (emitOutline || outlineModel.hasGraffiti(pos, state)) {
			facet$emitOverlays(output, x, y, z, level, pos, state, seed, outlineModel, emitOutline);
		}
	}

	@Unique
	private void facet$emitOverlays(BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, long seed,
			FacetBlockOverlay.OutlineBlockStateModel outlineModel, boolean emitOutline) {
		BlockQuadOutput overlayOutput = FacetBlockOverlay.overlayOutput(output);
		Vec3 offset = state.getOffset(pos);
		float ox = x + (float) offset.x;
		float oy = y + (float) offset.y;
		float oz = z + (float) offset.z;

		int culledFaces = 0;
		for (Direction face : FACET$DIRECTIONS) {
			if (!shouldRenderFace(level, state, face, scratchPos.setWithOffset(pos, face))) {
				culledFaces |= 1 << face.ordinal();
			}
		}

		boolean blockUseAo = emitOutline && this.ambientOcclusion
				&& state.getLightEmission() == 0 && outlineModel.useAmbientOcclusion(seed);
		outlineModel.emitOverlays(level, pos, state, culledFaces, emitOutline,
				(quad, color, quadAo) -> {
					if (quadAo && blockUseAo) {
						this.lighter.prepareQuadAmbientOcclusion(level, state, pos, quad, this.quadInstance);
					} else {
						this.lighter.prepareQuadFlat(level, state, pos, BlockModelLighter.CHECK_LIGHT, quad, this.quadInstance);
					}
					this.quadInstance.setColor(color);
					overlayOutput.put(ox, oy, oz, quad, this.quadInstance);
				});
	}
}

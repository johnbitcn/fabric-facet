package com.facet.client.mixin;

import java.util.Map;

import com.facet.client.FacetBlockOverlay;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelManager.class)
abstract class ModelManagerMixin {
	@Inject(method = "createBlockStateToModelDispatch("
			+ "Ljava/util/Map;"
			+ "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;"
			+ ")Ljava/util/Map;", at = @At("RETURN"))
	private static void facet$wrapBlockStateModels(Map<BlockState, BlockStateModel> bakedModels,
			BlockStateModel missingModel, CallbackInfoReturnable<Map<BlockState, BlockStateModel>> cir) {
		FacetBlockOverlay.wrapBlockStateModels(cir.getReturnValue());
	}
}

package com.facet.client.mixin;

import java.util.List;
import com.facet.client.StaticModelChildren;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPartModel.class)
abstract class MultiPartStaticChildrenMixin implements StaticModelChildren {
	@Unique private List<BlockStateModel> facet$children;

	@Inject(method = "<init>(Lnet/minecraft/client/renderer/block/dispatch/multipart/"
			+ "MultiPartModel$SharedBakedState;Lnet/minecraft/world/level/block/state/BlockState;)V",
			at = @At("TAIL"))
	private void facet$selectStaticChildren(@Coerce Object shared, BlockState state, CallbackInfo ci) {
		facet$children = ((MultiPartSelectedModelsInvoker) shared).facet$selectModels(state);
	}

	@Override
	public List<BlockStateModel> facet$staticChildren() {
		return facet$children;
	}
}

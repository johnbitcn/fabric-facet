package com.facet.client.mixin;

import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel$SharedBakedState")
public interface MultiPartSelectedModelsInvoker {
	@Invoker("selectModels")
	List<BlockStateModel> facet$selectModels(BlockState state);
}

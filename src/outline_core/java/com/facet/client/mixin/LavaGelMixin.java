package com.facet.client.mixin;

import java.util.Map;

import com.facet.client.LavaGel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
abstract class LavaGelMixin {
	@Inject(method = "bake", at = @At("RETURN"))
	private static void facet$bakeGel(MaterialBaker materials, CallbackInfoReturnable<Map<Fluid, FluidModel>> cir) {
		LavaGel.bake(materials);
	}

	@Inject(method = "get", at = @At("RETURN"), cancellable = true)
	private void facet$selectGel(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
		cir.setReturnValue(LavaGel.modelFor(state, cir.getReturnValue()));
	}
}

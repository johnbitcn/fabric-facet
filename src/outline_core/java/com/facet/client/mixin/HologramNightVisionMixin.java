package com.facet.client.mixin;

import com.facet.client.HologramNightVision;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class HologramNightVisionMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void facet$hologramNightVision(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		HologramNightVision.apply(state);
	}
}

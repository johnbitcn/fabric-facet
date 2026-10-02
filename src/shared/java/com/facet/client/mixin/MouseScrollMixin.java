package com.facet.client.mixin;

import com.facet.client.FacetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseScrollMixin {
	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void facet$blockGameplayScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (FacetClient.shouldBlockMouseScroll(Minecraft.getInstance())) {
			ci.cancel();
		}
	}
}

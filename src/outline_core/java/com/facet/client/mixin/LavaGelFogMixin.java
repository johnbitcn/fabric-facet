package com.facet.client.mixin;

import com.facet.client.LavaGel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFogEnvironment.class)
abstract class LavaGelFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void facet$clearerLavaInterior(FogData fog, Camera camera, ClientLevel level,
			float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		LavaGel.softenFog(fog);
	}
}

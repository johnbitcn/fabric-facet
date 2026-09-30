package com.facet.client.mixin;

import com.facet.client.FacetOutlineColor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
abstract class NetherrackTextureMixin {
	@Shadow @Final private Identifier name;
	@Shadow @Final private NativeImage originalImage;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void facet$flattenNetherrackCopy(CallbackInfo ci) {
		if (name.toString().equals("facet:block/netherrack_average")) {
			FacetOutlineColor.flattenTexture(originalImage);
		}
	}
}

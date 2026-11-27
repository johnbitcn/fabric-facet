package com.facet.client.mixin;

import com.facet.client.FacetBlockOverlay;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MaterialBaker.class)
abstract class MaterialBakerMixin {
	@Shadow @Final private SpriteLoader.Preparations blockAtlas;

	@Inject(method = "logMissingTextures()V", at = @At("HEAD"))
	private void facet$prepareOutlineMaterials(CallbackInfo ci) {
		FacetBlockOverlay.beginModelReload((MaterialBaker) (Object) this, this.blockAtlas);
	}
}

package com.facet.client.mixin;

import com.facet.client.LavaGel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidRenderer.class)
abstract class FluidDirectionSideMixin {
	// First flowingMaterial call is the rotated top; second is shared by all four sides.
	@WrapOperation(method = "tesselate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/FluidModel;flowingMaterial()Lnet/minecraft/client/resources/model/sprite/Material$Baked;",
			ordinal = 1))
	private Material.Baked facet$centerSideArrow(FluidModel model, Operation<Material.Baked> original) {
		return LavaGel.sideMaterial(model, original.call(model));
	}
}

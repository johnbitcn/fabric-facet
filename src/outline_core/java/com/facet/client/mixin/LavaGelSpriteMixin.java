package com.facet.client.mixin;

import com.facet.client.LavaGel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpriteContents.class)
abstract class LavaGelSpriteMixin {
	@Shadow @Final private Identifier name;

	// Constructors need an explicit descriptor; bare <init> selects no target.
	// Change the aliased copy before transparency classification and mipmap generation.
	@WrapOperation(method = "<init>(Lnet/minecraft/resources/Identifier;"
			+ "Lnet/minecraft/client/resources/metadata/animation/FrameSize;"
			+ "Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
			at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/NativeImage;computeTransparency()Lcom/mojang/blaze3d/platform/Transparency;"))
	private Transparency facet$transparentLavaCopy(NativeImage image, Operation<Transparency> original) {
		String id = name.toString();
		if (id.equals("facet:block/lava_gel_still") || id.equals("facet:block/lava_gel_flow")) {
			LavaGel.applyTransparency(image);
		}
		return original.call(image);
	}
}

package com.facet.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage facet$getOriginalImage();

	@Accessor("additionalMetadata")
	List<MetadataSectionType.WithValue<?>> facet$getAdditionalMetadata();

	@Accessor("mipmapStrategy")
	MipmapStrategy facet$getMipmapStrategy();

	@Accessor("alphaCutoffBias")
	float facet$getAlphaCutoffBias();
}

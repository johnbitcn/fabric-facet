package com.facet.client;

import java.util.Optional;
import com.facet.client.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;

/** Preserve resource-pack texture strategy and extra metadata in static variants. */
final class FacetSpriteFactory {
	private FacetSpriteFactory() {
	}

	static SpriteContents create(Identifier id, int size, NativeImage image, SpriteContents source) {
		SpriteContentsAccessor metadata = (SpriteContentsAccessor) source;
		TextureMetadataSection texture = new TextureMetadataSection(false, false,
				metadata.facet$getMipmapStrategy(), metadata.facet$getAlphaCutoffBias());
		return new SpriteContents(id, new FrameSize(size, size), image,
				Optional.empty(), metadata.facet$getAdditionalMetadata(), Optional.of(texture));
	}
}

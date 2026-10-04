package com.facet.client.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import com.facet.client.FacetBakedOutlineTextures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteLoader.class)
abstract class BakedOutlineAtlasMixin {
	@Shadow @Final private Identifier location;

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations facet$bakeOutlines(List<SpriteContents> sprites,
			int mipLevel, Executor executor, Operation<SpriteLoader.Preparations> original) {
		if (!location.equals(TextureAtlas.LOCATION_BLOCKS)) {
			return original.call(sprites, mipLevel, executor);
		}
		FacetBakedOutlineTextures.Batch batch = FacetBakedOutlineTextures.prepare(sprites);
		try {
			SpriteLoader.Preparations result = original.call(batch.sprites(), mipLevel, executor);
			FacetBakedOutlineTextures.publish(batch);
			return result;
		} catch (ReportedException failure) {
			batch.closeGenerated();
			if (batch.generated().isEmpty()) {
				throw failure;
			}
			// Extra sprites must never prevent a large resource pack from loading.
			LoggerFactory.getLogger("facet").warn("Baked outline atlas did not fit; using geometry outlines");
			FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(sprites, List.of(), Set.of(), 0));
			return original.call(sprites, mipLevel, executor);
		} catch (RuntimeException | Error failure) {
			batch.closeGenerated();
			throw failure;
		}
	}
}

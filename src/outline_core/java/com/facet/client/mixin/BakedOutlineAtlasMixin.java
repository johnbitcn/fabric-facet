package com.facet.client.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.facet.client.FacetBakedOutlineTextures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteLoader.class)
abstract class BakedOutlineAtlasMixin {
	@Unique private boolean facet$blockAtlas;

	// loadAndStitch receives the logical atlas ID; stitch only knows its texture path.
	@Inject(method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;"
			+ "Lnet/minecraft/resources/Identifier;ILjava/util/concurrent/Executor;"
			+ "Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
	private void facet$identifyAtlas(ResourceManager resources, Identifier atlasId, int mipLevel,
			Executor executor, Set<MetadataSectionType<?>> metadata,
			CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> ci) {
		facet$blockAtlas = atlasId.equals(AtlasIds.BLOCKS);
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations facet$bakeOutlines(List<SpriteContents> sprites,
			int mipLevel, Executor executor, Operation<SpriteLoader.Preparations> original) {
		if (!facet$blockAtlas) {
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

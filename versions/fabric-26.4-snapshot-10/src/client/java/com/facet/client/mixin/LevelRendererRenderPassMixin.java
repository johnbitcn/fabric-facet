package com.facet.client.mixin;

import com.facet.client.FacetMcBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
abstract class LevelRendererRenderPassMixin {
	@WrapMethod(method = "executeClassicTransparency("
			+ "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"
			+ "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender$ImprovedFogTextures;"
			+ "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;Z)V")
	private void facet$withClassicTransparencyPass(ChunkSectionsToRender sections,
			ChunkSectionsToRender.ImprovedFogTextures improvedFogTextures,
			FeatureRenderDispatcher.PreparedFrame preparedFrame, RenderPass renderPass,
			boolean renderWeather, Operation<Void> original) {
		FacetMcBridge.beginClassicTransparency(renderPass);
		try {
			original.call(sections, improvedFogTextures, preparedFrame, renderPass, renderWeather);
		} finally {
			FacetMcBridge.endClassicTransparency();
		}
	}
}

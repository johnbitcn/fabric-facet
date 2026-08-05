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
	@WrapMethod(method = "executeClassicTransparency")
	private void facet$withClassicTransparencyPass(ChunkSectionsToRender sections,
			FeatureRenderDispatcher.PreparedFrame preparedFrame, RenderPass renderPass,
			Operation<Void> original) {
		FacetMcBridge.beginClassicTransparency(renderPass);
		try {
			original.call(sections, preparedFrame, renderPass);
		} finally {
			FacetMcBridge.endClassicTransparency();
		}
	}
}

package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderType;

@FunctionalInterface
interface FacetRenderSink {
	void submit(PoseStack poseStack, RenderType renderType, GeometryRenderer renderer);

	@FunctionalInterface
	interface GeometryRenderer {
		void render(PoseStack.Pose pose, VertexConsumer consumer);
	}
}

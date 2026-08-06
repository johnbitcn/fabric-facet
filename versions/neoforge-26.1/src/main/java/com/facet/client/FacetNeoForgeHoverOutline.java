package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

final class FacetNeoForgeHoverOutline {
	private static final int OUTLINE_COLOR = 0xFF39FF14;
	private static final int DISTANT_OUTLINE_COLOR = 0xFF36F6FF;
	private static final long HUE_CYCLE_NANOS = 1_200_000_000L;
	private static final double FACE_PLANE_EPSILON = 1.0e-5;
	private static final Vector3f LINE_NORMAL_SCRATCH = new Vector3f();

	private FacetNeoForgeHoverOutline() {
	}

	static void extract(ExtractBlockOutlineRenderStateEvent event) {
		if (!FacetNeoForgeOutlineConfig.hoverEnabled()) {
			return;
		}

		BlockHitResult hit = event.getHitResult();
		BlockPos pos = event.getBlockPos();
		double facePlane = switch (hit.getDirection().getAxis()) {
			case X -> hit.getLocation().x - pos.getX();
			case Y -> hit.getLocation().y - pos.getY();
			case Z -> hit.getLocation().z - pos.getZ();
		};
		event.addCustomRenderer(new Renderer(hit.getDirection(), facePlane));
	}

	static void renderDistant(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		if (!FacetNeoForgeOutlineConfig.hoverEnabled()) {
			return;
		}

		var minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft.level == null || minecraft.hitResult instanceof BlockHitResult) {
			return;
		}

		var camera = minecraft.gameRenderer.getMainCamera();
		BlockHitResult hit = findViewedBlock(camera);
		if (hit.getType() != HitResult.Type.BLOCK || !minecraft.level.isLoaded(hit.getBlockPos())) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
		if (shape.isEmpty()) {
			shape = Shapes.block();
		}

		VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
		Vec3 cameraPos = camera.position();
		FacetShapeEdges.forEachEdge(shape, (x1, y1, z1, x2, y2, z2) ->
				Renderer.emitLine(event.getPoseStack(), consumer, pos, cameraPos, x1, y1, z1, x2, y2, z2, DISTANT_OUTLINE_COLOR));
		minecraft.renderBuffers().bufferSource().endBatch(RenderTypes.lines());
	}

	private static BlockHitResult findViewedBlock(net.minecraft.client.Camera camera) {
		Vec3 from = camera.position();
		double reach = Math.max(16.0, net.minecraft.client.Minecraft.getInstance().options.renderDistance().get() * 16.0);
		Vec3 to = from.add(camera.forwardVector().x() * reach, camera.forwardVector().y() * reach, camera.forwardVector().z() * reach);
		return net.minecraft.client.Minecraft.getInstance().level.clip(
				new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
	}

	private record Renderer(Direction hitFace, double facePlane) implements CustomBlockOutlineRenderer {
		@Override
		public boolean render(BlockOutlineRenderState renderState, MultiBufferSource.BufferSource buffer, PoseStack poseStack,
				boolean translucentPass, LevelRenderState levelRenderState) {
			if (renderState.isTranslucent() == translucentPass) {
				Vec3 camera = levelRenderState.cameraRenderState.pos;
				if (camera != null) {
					VertexConsumer consumer = buffer.getBuffer(RenderTypes.lines());
					int faceColor = Mth.hsvToArgb(
							(float) ((System.nanoTime() % HUE_CYCLE_NANOS) / (double) HUE_CYCLE_NANOS), 1.0f, 1.0f, 255);
					FacetShapeEdges.forEachEdge(renderState.shape(), (x1, y1, z1, x2, y2, z2) -> {
						int color = edgeOnHitFace(x1, y1, z1, x2, y2, z2) ? faceColor : OUTLINE_COLOR;
						emitLine(poseStack, consumer, renderState.pos(), camera, x1, y1, z1, x2, y2, z2, color);
					});
				}
			}
			return true;
		}

		private boolean edgeOnHitFace(double x1, double y1, double z1, double x2, double y2, double z2) {
			return switch (hitFace.getAxis()) {
				case X -> approximatelyEqual(x1, facePlane) && approximatelyEqual(x2, facePlane);
				case Y -> approximatelyEqual(y1, facePlane) && approximatelyEqual(y2, facePlane);
				case Z -> approximatelyEqual(z1, facePlane) && approximatelyEqual(z2, facePlane);
			};
		}

	private static void emitLine(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, Vec3 camera,
				double x1, double y1, double z1, double x2, double y2, double z2, int color) {
			float startX = (float) (pos.getX() + x1 - camera.x);
			float startY = (float) (pos.getY() + y1 - camera.y);
			float startZ = (float) (pos.getZ() + z1 - camera.z);
			float endX = (float) (pos.getX() + x2 - camera.x);
			float endY = (float) (pos.getY() + y2 - camera.y);
			float endZ = (float) (pos.getZ() + z2 - camera.z);
			Vector3f normal = LINE_NORMAL_SCRATCH.set(endX - startX, endY - startY, endZ - startZ);
			if (normal.lengthSquared() <= 1.0e-8f) {
				return;
			}

			normal.normalize();
			consumer.addVertex(poseStack.last(), startX, startY, startZ)
					.setColor(color)
					.setNormal(poseStack.last(), normal)
					.setLineWidth(FacetNeoForgeOutlineConfig.hoverWidth());
			consumer.addVertex(poseStack.last(), endX, endY, endZ)
					.setColor(color)
					.setNormal(poseStack.last(), normal)
					.setLineWidth(FacetNeoForgeOutlineConfig.hoverWidth());
		}

		private static boolean approximatelyEqual(double first, double second) {
			return Math.abs(first - second) <= FACE_PLANE_EPSILON;
		}
	}
}

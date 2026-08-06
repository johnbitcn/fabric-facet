package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

	private FacetNeoForgeHoverOutline() {}

	static void extract(ExtractBlockOutlineRenderStateEvent event) {
		if (!FacetNeoForgeOutlineConfig.hoverEnabled()) return;
		BlockHitResult hit = event.getHitResult();
		BlockPos pos = event.getBlockPos();
		double plane = switch (hit.getDirection().getAxis()) {
			case X -> hit.getLocation().x - pos.getX();
			case Y -> hit.getLocation().y - pos.getY();
			case Z -> hit.getLocation().z - pos.getZ();
		};
		event.addCustomRenderer(new Renderer(hit.getDirection(), plane));
	}

	static void renderDistant(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		if (!FacetNeoForgeOutlineConfig.hoverEnabled()) return;
		var minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft.level == null || minecraft.hitResult instanceof BlockHitResult) return;
		var camera = FacetNeoForgePlatform.mainCamera(minecraft);
		BlockHitResult hit = findViewedBlock(camera);
		if (hit.getType() != HitResult.Type.BLOCK || !minecraft.level.isLoaded(hit.getBlockPos())) return;
		BlockPos pos = hit.getBlockPos();
		VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
		if (shape.isEmpty()) shape = Shapes.block();
		Vec3 cameraPos = camera.position();
		VoxelShape finalShape = shape;
		FacetNeoForgePlatform.render(event, RenderTypes.lines(), (pose, consumer) ->
			FacetShapeEdges.forEachEdge(finalShape, (x1, y1, z1, x2, y2, z2) ->
				emitLine(pose, consumer, pos, cameraPos, x1, y1, z1, x2, y2, z2, DISTANT_OUTLINE_COLOR)));
	}

	private static BlockHitResult findViewedBlock(net.minecraft.client.Camera camera) {
		Vec3 from = camera.position();
		double reach = Math.max(16.0, net.minecraft.client.Minecraft.getInstance().options.renderDistance().get() * 16.0);
		Vec3 to = from.add(camera.forwardVector().x() * reach, camera.forwardVector().y() * reach, camera.forwardVector().z() * reach);
		return net.minecraft.client.Minecraft.getInstance().level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
	}

	private record Renderer(Direction hitFace, double facePlane) implements CustomBlockOutlineRenderer {
		@Override
		public boolean render(BlockOutlineRenderState state, SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
			Vec3 camera = levelState.cameraRenderState.pos;
			if (camera == null) return true;
			int faceColor = Mth.hsvToArgb((float) ((System.nanoTime() % HUE_CYCLE_NANOS) / (double) HUE_CYCLE_NANOS), 1.0f, 1.0f, 255);
			collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
				FacetShapeEdges.forEachEdge(state.shape(), (x1, y1, z1, x2, y2, z2) ->
					emitLine(poseStack, consumer, state.pos(), camera, x1, y1, z1, x2, y2, z2,
							edgeOnHitFace(x1, y1, z1, x2, y2, z2) ? faceColor : OUTLINE_COLOR)));
			return true;
		}

		private boolean edgeOnHitFace(double x1, double y1, double z1, double x2, double y2, double z2) {
			return switch (hitFace.getAxis()) {
				case X -> near(x1, facePlane) && near(x2, facePlane);
				case Y -> near(y1, facePlane) && near(y2, facePlane);
				case Z -> near(z1, facePlane) && near(z2, facePlane);
			};
		}
	}

	private static void emitLine(PoseStack pose, VertexConsumer consumer, BlockPos pos, Vec3 camera,
			double x1, double y1, double z1, double x2, double y2, double z2, int color) {
		float sx = (float) (pos.getX() + x1 - camera.x), sy = (float) (pos.getY() + y1 - camera.y), sz = (float) (pos.getZ() + z1 - camera.z);
		float ex = (float) (pos.getX() + x2 - camera.x), ey = (float) (pos.getY() + y2 - camera.y), ez = (float) (pos.getZ() + z2 - camera.z);
		Vector3f normal = LINE_NORMAL_SCRATCH.set(ex - sx, ey - sy, ez - sz);
		if (normal.lengthSquared() <= 1.0e-8f) return;
		normal.normalize();
		consumer.addVertex(pose.last(), sx, sy, sz).setColor(color).setNormal(pose.last(), normal).setLineWidth(FacetNeoForgeOutlineConfig.hoverWidth());
		consumer.addVertex(pose.last(), ex, ey, ez).setColor(color).setNormal(pose.last(), normal).setLineWidth(FacetNeoForgeOutlineConfig.hoverWidth());
	}

	private static boolean near(double first, double second) { return Math.abs(first - second) <= FACE_PLANE_EPSILON; }
}

package com.facet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.facet.client.mixin.BlockItemInvoker;

final class PlacementPreview {
	private static final int HOLOGRAM_RED = 72;
	private static final int HOLOGRAM_GREEN = 255;
	private static final int HOLOGRAM_BLUE = 255;
	private static final long FLICKER_INTERVAL_NANOS = 70_000_000L;
	private static final float BASE_ALPHA = 0.29f;
	private static final float FLICKER_ALPHA_RANGE = 0.13f;
	private static final float GLITCH_CHANCE = 0.08f;
	private static final float MIN_SLICE_HEIGHT = 0.055f;
	private static final float MAX_SLICE_HEIGHT = 0.145f;
	private static final float MIN_SLICE_OFFSET = 0.035f;
	private static final float MAX_SLICE_OFFSET = 0.09f;
	private static final float EDGE_ALPHA_SCALE = 1.9f;
	private static final float EDGE_WIDTH = 1.5f;
	private static final float OVERLAP_SCALE = 1.002f;

	private PlacementPreview() {
	}

	static void render(LevelRenderContext context) {
		if (!FacetConfig.placementPreviewEnabled()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;

		if (level == null || minecraft.player == null || minecraft.player.isSpectator()
				|| !(minecraft.hitResult instanceof BlockHitResult hitResult)
				|| hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		Prediction prediction = predict(minecraft, level, hitResult);

		if (prediction == null) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;

		if (camera == null || camera.pos == null) {
			return;
		}

		if (!prediction.valid()) {
			return;
		}

		HologramFrame frame = HologramFrame.create(System.nanoTime());
		Vector3fc left = FacetMcBridge.mainCamera(minecraft).leftVector();
		float screenRightX = -left.x();
		float screenRightZ = -left.z();

		prediction.blocks().stream()
				.sorted(Comparator.comparingDouble((PreviewBlock block) -> distanceSquared(block.pos(), camera.pos)).reversed())
				.forEach(block -> renderBlock(context, minecraft, level, camera.pos, block,
						frame, screenRightX, screenRightZ));
	}

	private static Prediction predict(Minecraft minecraft, ClientLevel level, BlockHitResult hitResult) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = minecraft.player.getItemInHand(hand);

			if (!(stack.getItem() instanceof BlockItem blockItem)
					|| minecraft.player.getCooldowns().isOnCooldown(stack)) {
				continue;
			}

			BlockPlaceContext context = new BlockPlaceContext(minecraft.player, hand, stack, hitResult);

			if (!minecraft.player.mayUseItemAt(hitResult.getBlockPos(), hitResult.getDirection(), stack)
					|| !context.canPlace()) {
				return Prediction.invalid();
			}

			context = blockItem.updatePlacementContext(context);

			if (context == null) {
				return Prediction.invalid();
			}

			BlockState state = ((BlockItemInvoker) blockItem).facet$getPlacementState(context);

			if (state == null) {
				return Prediction.invalid();
			}

			BlockItemStateProperties properties = stack.getOrDefault(
					DataComponents.BLOCK_STATE,
					BlockItemStateProperties.EMPTY);
			state = properties.apply(state);

			List<PreviewBlock> blocks = expandBlocks(level, context.getClickedPos(), state);

			if (blocks.stream().anyMatch(block -> level.isOutsideBuildHeight(block.pos())
					|| !level.getWorldBorder().isWithinBounds(block.pos()))) {
				return Prediction.invalid();
			}

			return Prediction.valid(blocks);
		}

		return null;
	}

	private static List<PreviewBlock> expandBlocks(ClientLevel level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			BlockState lower = state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
			BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
			return List.of(new PreviewBlock(pos, lower), new PreviewBlock(pos.above(), upper));
		}

		if (state.getBlock() instanceof BedBlock) {
			BlockState foot = state.setValue(BedBlock.PART, BedPart.FOOT);
			Direction facing = foot.getValue(BedBlock.FACING);
			BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
			return List.of(new PreviewBlock(pos, foot), new PreviewBlock(pos.relative(facing), head));
		}

		if (state.getBlock() instanceof DoublePlantBlock) {
			BlockState lower = copyWaterlogged(level, pos,
					state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
			BlockPos upperPos = pos.above();
			BlockState upper = copyWaterlogged(level, upperPos,
					state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
			return List.of(new PreviewBlock(pos, lower), new PreviewBlock(upperPos, upper));
		}

		return List.of(new PreviewBlock(pos, state));
	}

	private static BlockState copyWaterlogged(ClientLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			return state;
		}

		return state.setValue(BlockStateProperties.WATERLOGGED, level.isWaterAt(pos));
	}

	private static void renderBlock(LevelRenderContext context, Minecraft minecraft, ClientLevel level,
			Vec3 camera, PreviewBlock preview, HologramFrame frame, float screenRightX, float screenRightZ) {
		BlockState state = preview.state();

		if (state.getRenderShape() != RenderShape.MODEL) {
			return;
		}

		BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(state.getSeed(preview.pos())), parts);

		if (parts.isEmpty()) {
			return;
		}

		int[] tints = blockTints(minecraft, level, preview);
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(
				preview.pos().getX() - camera.x,
				preview.pos().getY() - camera.y,
				preview.pos().getZ() - camera.z);

		if (!level.getBlockState(preview.pos()).isAir()) {
			poseStack.translate(0.5, 0.5, 0.5);
			poseStack.scale(OVERLAP_SCALE, OVERLAP_SCALE, OVERLAP_SCALE);
			poseStack.translate(-0.5, -0.5, -0.5);
		}

		int[] colors = hologramColors(tints, frame.alpha());
		context.submitNodeCollector().submitCustomGeometry(
				poseStack,
				RenderTypes.translucentMovingBlock(),
				(pose, consumer) -> renderModelParts(pose, consumer, parts, colors, frame,
						screenRightX, screenRightZ));

		VoxelShape shape = state.getShape(level, preview.pos());

		if (!shape.isEmpty()) {
			int edgeColor = hologramColor(-1, Math.min(0.92f, frame.alpha() * EDGE_ALPHA_SCALE));
			context.submitNodeCollector().submitCustomGeometry(
					poseStack,
					RenderTypes.linesTranslucent(),
					(pose, consumer) -> renderEdges(pose, consumer, shape, edgeColor, frame,
							screenRightX, screenRightZ));
		}
		poseStack.popPose();
	}

	private static void renderModelParts(PoseStack.Pose pose, VertexConsumer consumer,
			List<BlockStateModelPart> parts, int[] colors, HologramFrame frame,
			float screenRightX, float screenRightZ) {
		QuadInstance quadInstance = new QuadInstance();
		quadInstance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
		quadInstance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

		for (BlockStateModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				renderQuads(pose, consumer, part.getQuads(direction), colors, quadInstance, frame,
						screenRightX, screenRightZ);
			}

			renderQuads(pose, consumer, part.getQuads(null), colors, quadInstance, frame,
					screenRightX, screenRightZ);
		}
	}

	private static void renderQuads(PoseStack.Pose pose, VertexConsumer consumer, List<BakedQuad> quads,
			int[] colors, QuadInstance quadInstance, HologramFrame frame,
			float screenRightX, float screenRightZ) {
		for (BakedQuad quad : quads) {
			int tintIndex = quad.materialInfo().tintIndex();
			int color = tintIndex >= 0 && tintIndex < colors.length
					? colors[tintIndex]
					: colors[colors.length - 1];
			quadInstance.setColor(color);

			if (frame.slice() == null) {
				consumer.putBakedQuad(pose, quad, quadInstance);
			} else {
				renderGlitchedQuad(pose, consumer, quad, color, frame.slice(), screenRightX, screenRightZ);
			}
		}
	}

	private static int[] hologramColors(int[] tints, float alpha) {
		int[] colors = new int[tints.length + 1];

		for (int index = 0; index < tints.length; index++) {
			colors[index] = hologramColor(tints[index], alpha);
		}

		colors[tints.length] = hologramColor(-1, alpha);
		return colors;
	}

	private static int hologramColor(int tint, float alpha) {
		int color = ARGB.color(Math.round(alpha * 255.0f), HOLOGRAM_RED, HOLOGRAM_GREEN, HOLOGRAM_BLUE);
		return tint == -1 ? color : ARGB.multiply(color, tint);
	}

	private static void renderGlitchedQuad(PoseStack.Pose pose, VertexConsumer consumer, BakedQuad quad,
			int color, GlitchSlice slice, float screenRightX, float screenRightZ) {
		List<HologramVertex> vertices = quadVertices(quad);
		renderPolygon(pose, consumer, clip(vertices, Float.NEGATIVE_INFINITY, slice.minY()),
				quad.direction(), color, 0.0f, 0.0f);
		renderPolygon(pose, consumer, clip(vertices, slice.maxY(), Float.POSITIVE_INFINITY),
				quad.direction(), color, 0.0f, 0.0f);
		renderPolygon(pose, consumer, clip(vertices, slice.minY(), slice.maxY()),
				quad.direction(), color, screenRightX * slice.offset(), screenRightZ * slice.offset());
	}

	private static List<HologramVertex> quadVertices(BakedQuad quad) {
		List<HologramVertex> vertices = new ArrayList<>(BakedQuad.VERTEX_COUNT);

		for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
			Vector3fc position = quad.position(index);
			long packedUv = quad.packedUV(index);
			vertices.add(new HologramVertex(position.x(), position.y(), position.z(),
					UVPair.unpackU(packedUv), UVPair.unpackV(packedUv)));
		}

		return vertices;
	}

	private static List<HologramVertex> clip(List<HologramVertex> vertices, float minY, float maxY) {
		return clipAt(clipAt(vertices, minY, true), maxY, false);
	}

	private static List<HologramVertex> clipAt(List<HologramVertex> vertices, float boundary, boolean keepAbove) {
		if (vertices.isEmpty() || Float.isInfinite(boundary)) {
			return vertices;
		}

		List<HologramVertex> clipped = new ArrayList<>(vertices.size() + 2);
		HologramVertex previous = vertices.get(vertices.size() - 1);
		boolean previousInside = keepAbove ? previous.y() >= boundary : previous.y() <= boundary;

		for (HologramVertex current : vertices) {
			boolean currentInside = keepAbove ? current.y() >= boundary : current.y() <= boundary;

			if (currentInside != previousInside) {
				float amount = (boundary - previous.y()) / (current.y() - previous.y());
				clipped.add(previous.lerp(current, amount));
			}

			if (currentInside) {
				clipped.add(current);
			}

			previous = current;
			previousInside = currentInside;
		}

		return clipped;
	}

	private static void renderPolygon(PoseStack.Pose pose, VertexConsumer consumer,
			List<HologramVertex> vertices, Direction direction, int color, float offsetX, float offsetZ) {
		if (vertices.size() < 3) {
			return;
		}

		Vector3f normal = pose.transformNormal(direction.getUnitVec3f(), new Vector3f());

		for (int index = 1; index < vertices.size() - 1; index++) {
			emitVertex(pose, consumer, vertices.get(0), color, normal, offsetX, offsetZ);
			emitVertex(pose, consumer, vertices.get(index), color, normal, offsetX, offsetZ);
			emitVertex(pose, consumer, vertices.get(index + 1), color, normal, offsetX, offsetZ);
			emitVertex(pose, consumer, vertices.get(index + 1), color, normal, offsetX, offsetZ);
		}
	}

	private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, HologramVertex vertex,
			int color, Vector3f normal, float offsetX, float offsetZ) {
		Vector3f transformed = pose.pose().transformPosition(
				new Vector3f(vertex.x() + offsetX, vertex.y(), vertex.z() + offsetZ));
		consumer.addVertex(transformed)
				.setColor(color)
				.setUv(vertex.u(), vertex.v())
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(normal.x(), normal.y(), normal.z());
	}

	private static void renderEdges(PoseStack.Pose pose, VertexConsumer consumer, VoxelShape shape,
			int color, HologramFrame frame, float screenRightX, float screenRightZ) {
		shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
			GlitchSlice slice = frame.slice();

			if (slice == null) {
				emitClippedLine(pose, consumer, x1, y1, z1, x2, y2, z2,
						Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0.0f, 0.0f, color);
				return;
			}

			emitClippedLine(pose, consumer, x1, y1, z1, x2, y2, z2,
					Float.NEGATIVE_INFINITY, slice.minY(), 0.0f, 0.0f, color);
			emitClippedLine(pose, consumer, x1, y1, z1, x2, y2, z2,
					slice.maxY(), Float.POSITIVE_INFINITY, 0.0f, 0.0f, color);
			emitClippedLine(pose, consumer, x1, y1, z1, x2, y2, z2,
					slice.minY(), slice.maxY(), screenRightX * slice.offset(),
					screenRightZ * slice.offset(), color);
		});
	}

	private static void emitClippedLine(PoseStack.Pose pose, VertexConsumer consumer,
			double x1, double y1, double z1, double x2, double y2, double z2,
			float minY, float maxY, float offsetX, float offsetZ, int color) {
		double deltaY = y2 - y1;
		double start = 0.0;
		double end = 1.0;

		if (Math.abs(deltaY) < 1.0e-8) {
			if (y1 < minY || y1 > maxY) {
				return;
			}
		} else {
			double first = (minY - y1) / deltaY;
			double second = (maxY - y1) / deltaY;
			start = Math.max(0.0, Math.min(first, second));
			end = Math.min(1.0, Math.max(first, second));

			if (start > end) {
				return;
			}
		}

		float startX = (float) (x1 + (x2 - x1) * start) + offsetX;
		float startY = (float) (y1 + deltaY * start);
		float startZ = (float) (z1 + (z2 - z1) * start) + offsetZ;
		float endX = (float) (x1 + (x2 - x1) * end) + offsetX;
		float endY = (float) (y1 + deltaY * end);
		float endZ = (float) (z1 + (z2 - z1) * end) + offsetZ;
		Vector3f normal = new Vector3f(endX - startX, endY - startY, endZ - startZ);

		if (normal.lengthSquared() <= 1.0e-8f) {
			return;
		}

		normal.normalize();
		consumer.addVertex(pose, startX, startY, startZ)
				.setColor(color)
				.setNormal(pose, normal)
				.setLineWidth(EDGE_WIDTH);
		consumer.addVertex(pose, endX, endY, endZ)
				.setColor(color)
				.setNormal(pose, normal)
				.setLineWidth(EDGE_WIDTH);
	}

	private static int[] blockTints(Minecraft minecraft, ClientLevel level, PreviewBlock preview) {
		List<BlockTintSource> sources = minecraft.getBlockColors().getTintSources(preview.state());
		int[] tints = new int[sources.size()];

		for (int index = 0; index < sources.size(); index++) {
			tints[index] = sources.get(index).colorInWorld(preview.state(), level, preview.pos());
		}

		return tints;
	}

	private static double distanceSquared(BlockPos pos, Vec3 camera) {
		double x = pos.getX() + 0.5 - camera.x;
		double y = pos.getY() + 0.5 - camera.y;
		double z = pos.getZ() + 0.5 - camera.z;
		return x * x + y * y + z * z;
	}

	private static long mix(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static float unitFloat(long value) {
		return (float) ((value >>> 40) * 0x1.0p-24);
	}

	private record PreviewBlock(BlockPos pos, BlockState state) {
	}

	private record HologramVertex(float x, float y, float z, float u, float v) {
		private HologramVertex lerp(HologramVertex other, float amount) {
			return new HologramVertex(
					x + (other.x - x) * amount,
					y + (other.y - y) * amount,
					z + (other.z - z) * amount,
					u + (other.u - u) * amount,
					v + (other.v - v) * amount);
		}
	}

	private record GlitchSlice(float minY, float maxY, float offset) {
	}

	private record HologramFrame(float alpha, GlitchSlice slice) {
		private static HologramFrame create(long timeNanos) {
			long frame = timeNanos / FLICKER_INTERVAL_NANOS;
			long random = mix(frame);
			float alpha = BASE_ALPHA + unitFloat(random) * FLICKER_ALPHA_RANGE;

			if ((random & 0x1FL) == 0L) {
				alpha *= 0.58f;
			}

			long glitchRandom = mix(random ^ 0xD1B54A32D192ED03L);

			if (unitFloat(glitchRandom) >= GLITCH_CHANCE) {
				return new HologramFrame(alpha, null);
			}

			float height = MIN_SLICE_HEIGHT
					+ unitFloat(mix(glitchRandom + 1L)) * (MAX_SLICE_HEIGHT - MIN_SLICE_HEIGHT);
			float minY = 0.08f + unitFloat(mix(glitchRandom + 2L)) * (0.84f - height);
			float offset = MIN_SLICE_OFFSET
					+ unitFloat(mix(glitchRandom + 3L)) * (MAX_SLICE_OFFSET - MIN_SLICE_OFFSET);

			if ((glitchRandom & 1L) == 0L) {
				offset = -offset;
			}

			return new HologramFrame(alpha, new GlitchSlice(minY, minY + height, offset));
		}
	}

	private record Prediction(boolean valid, List<PreviewBlock> blocks) {
		private static Prediction valid(List<PreviewBlock> blocks) {
			return new Prediction(true, List.copyOf(blocks));
		}

		private static Prediction invalid() {
			return new Prediction(false, List.of());
		}
	}
}

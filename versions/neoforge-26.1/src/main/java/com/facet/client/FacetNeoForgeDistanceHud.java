package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class FacetNeoForgeDistanceHud {
	private static final int X_COLOR = 0xFFFF6F8F;
	private static final int Z_COLOR = 0xFFFFFF20;
	private static final int Y_COLOR = 0xFF39FF14;
	private static final int HUD_FONT_SIZE_INCREASE = 2;
	private static boolean visible;
	/** Per-frame memo of the distant-target raycast, shared by the distance HUD, the distance
	 *  path and the hover outline. Cleared before every frame render (RenderFrameEvent.Pre) so
	 *  all consumers of one frame reuse a single level.clip (see {@link #distanceTarget}). */
	private static BlockHitResult farTargetCache;

	private FacetNeoForgeDistanceHud() {
	}

	static void clearFarTargetCache() {
		farTargetCache = null;
	}

	static void toggle() {
		visible = !visible;
	}

	static void renderHud(GuiGraphicsExtractor graphics) {
		if (!visible) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		BlockHitResult hit = distanceTarget(minecraft);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		DistanceInfo distance = distanceInfo(minecraft.player.getOnPos(), hit.getBlockPos());
		String xSegment = "X:" + distance.x();
		String zSegment = "Z:" + distance.z();
		String ySegment = "Y:" + distance.y();
		String mSegment = "M:" + distance.manhattan();
		String separator = ", ";
		int rawTextWidth = width(minecraft, xSegment) + width(minecraft, separator) + width(minecraft, zSegment)
				+ width(minecraft, separator) + width(minecraft, ySegment) + width(minecraft, separator) + width(minecraft, mSegment);
		float textScale = (minecraft.font.lineHeight + HUD_FONT_SIZE_INCREASE) / (float) minecraft.font.lineHeight;
		int textWidth = Mth.ceil(rawTextWidth * textScale);
		int textHeight = Mth.ceil(minecraft.font.lineHeight * textScale);
		int x = graphics.guiWidth() / 2 + 12;
		int y = graphics.guiHeight() / 2 - (textHeight + 10) / 2;
		int boxWidth = textWidth + 14;
		int boxHeight = textHeight + 10;

		graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x8C06181E);
		graphics.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, 0x5536F6FF);
		graphics.fill(x + 1, y + boxHeight - 3, x + boxWidth - 1, y + boxHeight - 2, 0x3324C7D9);
		graphics.outline(x, y, boxWidth, boxHeight, 0xAA36F6FF);
		for (int scanY = y + 4; scanY < y + boxHeight - 3; scanY += 4) {
			graphics.fill(x + 2, scanY, x + boxWidth - 2, scanY + 1, 0x2236F6FF);
		}

		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(x + 7, y + 5);
			graphics.pose().scale(textScale);
			int textX = 0;
			textX = text(graphics, minecraft, xSegment, textX, X_COLOR);
			textX = text(graphics, minecraft, separator, textX, 0xFFE9FFFF);
			textX = text(graphics, minecraft, zSegment, textX, Z_COLOR);
			textX = text(graphics, minecraft, separator, textX, 0xFFE9FFFF);
			textX = text(graphics, minecraft, ySegment, textX, Y_COLOR);
			textX = text(graphics, minecraft, separator, textX, 0xFFE9FFFF);
			text(graphics, minecraft, mSegment, textX, 0xFFE9FFFF);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	static void renderPath(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		if (!visible || !FacetNeoForgeOutlineConfig.distancePathVisible()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || event.getLevelRenderState().cameraRenderState.pos == null) {
			return;
		}

		BlockHitResult hit = distanceTarget(minecraft);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos start = minecraft.player.getOnPos();
		BlockPos target = hit.getBlockPos();
		BlockPos xCorner = new BlockPos(target.getX(), start.getY(), start.getZ());
		BlockPos zCorner = new BlockPos(target.getX(), start.getY(), target.getZ());
		Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
		FacetNeoForgePlatform.render(event, RenderTypes.debugFilledBox(), (pose, consumer) -> {
			renderSegment(pose, consumer, camera, start, xCorner, pulseColor(255, 32, 96));
			renderSegment(pose, consumer, camera, xCorner, zCorner, pulseColor(255, 255, 32));
			renderSegment(pose, consumer, camera, zCorner, target, pulseColor(57, 255, 20));
		});
	}

	private static BlockHitResult findViewedBlock(Minecraft minecraft, Camera camera) {
		Vec3 from = camera.position();
		double reach = Math.max(16.0, minecraft.options.renderDistance().get() * 16.0);
		Vec3 to = from.add(camera.forwardVector().x() * reach, camera.forwardVector().y() * reach, camera.forwardVector().z() * reach);
		return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
	}

	/**
	 * Distance HUD/path/hover target. Reuses the per-frame game hit result when the
	 * crosshair already points at a block (same ray and first-hit target), avoiding a
	 * full level raycast every frame. When the crosshair misses, the distant raycast
	 * result is memoized per frame (cleared at RenderFrameEvent.Pre) so the HUD, the
	 * distance path and the hover outline share a single level.clip instead of each
	 * running their own (up to 200+ block steps at typical render distances).
	 */
	static BlockHitResult distanceTarget(Minecraft minecraft) {
		if (minecraft.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			return hit;
		}

		if (farTargetCache == null) {
			farTargetCache = findViewedBlock(minecraft, FacetNeoForgePlatform.mainCamera(minecraft));
		}

		return farTargetCache;
	}

	private static DistanceInfo distanceInfo(BlockPos from, BlockPos to) {
		int x = Math.abs(to.getX() - from.getX());
		int y = Math.abs(to.getY() - from.getY());
		int z = Math.abs(to.getZ() - from.getZ());
		return new DistanceInfo(x, z, y, x + y + z);
	}

	private static int width(Minecraft minecraft, String text) {
		return minecraft.font.width(Component.literal(text));
	}

	private static int text(GuiGraphicsExtractor graphics, Minecraft minecraft, String text, int x, int color) {
		Component component = Component.literal(text);
		graphics.text(minecraft.font, component, x, 0, color, false);
		return x + minecraft.font.width(component);
	}

	private static int pulseColor(int red, int green, int blue) {
		float phase = (float) ((System.nanoTime() % 1_800_000_000L) / 1_800_000_000.0);
		float brightness = 0.84f + 0.16f * (0.5f + 0.5f * Mth.sin(phase * Mth.TWO_PI));
		return net.minecraft.util.ARGB.color(92, Math.round(red * brightness), Math.round(green * brightness), Math.round(blue * brightness));
	}

	private static void renderSegment(PoseStack pose, VertexConsumer consumer, Vec3 camera, BlockPos from, BlockPos to, int color) {
		if (from.equals(to)) {
			return;
		}
		BlockPos first = new BlockPos(
				from.getX() + Integer.compare(to.getX(), from.getX()),
				from.getY() + Integer.compare(to.getY(), from.getY()),
				from.getZ() + Integer.compare(to.getZ(), from.getZ()));
		double minX = Math.min(first.getX(), to.getX()) - camera.x;
		double minY = Math.min(first.getY(), to.getY()) - camera.y;
		double minZ = Math.min(first.getZ(), to.getZ()) - camera.z;
		double maxX = Math.max(first.getX(), to.getX()) + 1.0 - camera.x;
		double maxY = Math.max(first.getY(), to.getY()) + 1.0 - camera.y;
		double maxZ = Math.max(first.getZ(), to.getZ()) + 1.0 - camera.z;
		box(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, color);
	}

	private static void box(PoseStack pose, VertexConsumer consumer, double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ, int color) {
		face(pose, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, 0, -1, 0, color);
		face(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 0, 1, 0, color);
		face(pose, consumer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0, 0, -1, color);
		face(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0, 0, 1, color);
		face(pose, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1, 0, 0, color);
		face(pose, consumer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 1, 0, 0, color);
	}

	private static void face(PoseStack pose, VertexConsumer consumer,
			double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, double x4, double y4, double z4,
			float normalX, float normalY, float normalZ, int color) {
		vertex(pose, consumer, x1, y1, z1, normalX, normalY, normalZ, color);
		vertex(pose, consumer, x2, y2, z2, normalX, normalY, normalZ, color);
		vertex(pose, consumer, x3, y3, z3, normalX, normalY, normalZ, color);
		vertex(pose, consumer, x4, y4, z4, normalX, normalY, normalZ, color);
	}

	private static void vertex(PoseStack pose, VertexConsumer consumer, double x, double y, double z,
			float normalX, float normalY, float normalZ, int color) {
		consumer.addVertex(pose.last(), (float) x, (float) y, (float) z)
				.setColor(color)
				.setNormal(pose.last(), normalX, normalY, normalZ);
	}

	private record DistanceInfo(int x, int z, int y, int manhattan) {
	}
}

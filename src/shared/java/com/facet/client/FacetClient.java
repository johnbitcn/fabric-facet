package com.facet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Vector3f;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class FacetClient implements ClientModInitializer {
	private static final double EDGE_EPSILON = 1.0e-6;
	private static final double HOVER_SURFACE_BIAS = 1.0 / 256.0;
	private static final double HOVER_FACE_EPSILON = 1.0e-3;
	private static final float OUTLINE_ALPHA_MARKER_MAX = 254.0f / 255.0f;
	private static final float DARK_LUMINANCE_MAX = 0.25f;
	private static final float DARK_CONTRAST_ADJUSTMENT = -0.25f;
	private static final float DEFAULT_BRIGHTNESS_ADJUSTMENT = -0.60f;
	private static final double DISTANCE_PATH_SURFACE_BIAS = 1.0 / 64.0;
	private static final int DISTANCE_X_COLOR = 0xFFFF6F8F;
	private static final int DISTANCE_Z_COLOR = 0xFFFFFF20;
	private static final int DISTANCE_Y_COLOR = 0xFF39FF14;
	private static final int DISTANCE_HUD_FONT_SIZE_INCREASE = 2;
	private static final int OUT_OF_REACH_HOVER_COLOR = 0xFF36F6FF;
	private static final Identifier DISTANCE_HUD_ID = Identifier.fromNamespaceAndPath("facet", "distance_hud");
	private static KeyMapping toggleOutlineKeyMapping;
	private static KeyMapping toggleHoverOutlineKeyMapping;
	private static KeyMapping toggleDistanceHudKeyMapping;
	private static KeyMapping graffitiKeyMapping;
	private static KeyMapping openSettingsKeyMapping;
	private static boolean distanceHudVisible;

	@Override
	public void onInitializeClient() {
		FacetConfig.load();
		GraffitiStore.load();
		registerKeyMappings();
		FacetBlockOverlay.initialize();
		LevelRenderEvents.COLLECT_SUBMITS.register(FacetClient::renderHoverOutline);
		LevelRenderEvents.COLLECT_SUBMITS.register(FacetClient::renderDistancePath);
		LevelRenderEvents.COLLECT_SUBMITS.register(PlacementPreview::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, DISTANCE_HUD_ID, FacetClient::renderDistanceHud);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((minecraft, level) ->
				GraffitiStore.setContext(FacetMcBridge.worldScope(minecraft, level), level.dimension().identifier()));
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> GraffitiStore.reconcileChunk(level, chunk.getPos()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> GraffitiStore.flush());
		ClientTickEvents.END_CLIENT_TICK.register(FacetClient::handleKeyMappings);
	}

	private static void registerKeyMappings() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("facet", "keybinds"));
		toggleOutlineKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.facet.toggle_outline",
				FacetMcBridge.keyboardType(),
				InputConstants.UNKNOWN.getValue(),
				category));
		toggleHoverOutlineKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.facet.toggle_hover_outline",
				FacetMcBridge.keyboardType(),
				InputConstants.UNKNOWN.getValue(),
				category));
		toggleDistanceHudKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.facet.toggle_distance_hud",
				FacetMcBridge.keyboardType(),
				InputConstants.UNKNOWN.getValue(),
				category));
		graffitiKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.facet.graffiti",
				FacetMcBridge.keyboardType(),
				InputConstants.KEY_G,
				category));
		openSettingsKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.facet.open_settings",
				FacetMcBridge.keyboardType(),
				InputConstants.UNKNOWN.getValue(),
				category));
	}

	private static void handleKeyMappings(Minecraft minecraft) {
		while (toggleOutlineKeyMapping.consumeClick()) {
			FacetConfig.setEnabled(!FacetConfig.enabled());
		}

		while (toggleHoverOutlineKeyMapping.consumeClick()) {
			FacetConfig.setHoverEnabled(!FacetConfig.hoverEnabled());
		}

		while (toggleDistanceHudKeyMapping.consumeClick()) {
			distanceHudVisible = !distanceHudVisible;
		}

		while (graffitiKeyMapping.consumeClick()) {
			openGraffitiWheel(minecraft);
		}

		while (openSettingsKeyMapping.consumeClick()) {
			FacetMcBridge.showScreen(minecraft, new FacetConfigScreen(null));
		}

		GraffitiStore.flush();
	}

	private static void openGraffitiWheel(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null
				|| !(minecraft.hitResult instanceof BlockHitResult hitResult)
				|| hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hitResult.getBlockPos();
		BlockState state = minecraft.level.getBlockState(pos);
		Direction direction = hitResult.getDirection();
		GraffitiType currentType = GraffitiStore.getType(pos, direction);

		GraffitiEligibility.Result result = GraffitiEligibility.evaluate(minecraft.level, pos, state, direction);

		if (currentType == null && result != GraffitiEligibility.Result.ALLOWED) {
			minecraft.player.sendOverlayMessage(Component.translatable(switch (result) {
				case NON_SOLID -> "message.facet.graffiti.non_solid";
				case FUNCTIONAL -> "message.facet.graffiti.functional";
				case INCOMPLETE_FACE -> "message.facet.graffiti.incomplete_face";
				case ALLOWED -> throw new IllegalStateException("Allowed graffiti result was rejected");
			}));
			return;
		}

		FacetMcBridge.showScreen(minecraft, new GraffitiWheelScreen(
				FacetMcBridge.worldScope(minecraft, minecraft.level),
				minecraft.level.dimension().identifier(),
				pos.immutable(),
				direction,
				BuiltInRegistries.BLOCK.getKey(state.getBlock())));
	}

	private static void renderDistanceHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
		if (!distanceHudVisible) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		Camera camera = FacetMcBridge.mainCamera(minecraft);
		BlockHitResult hitResult = findViewedBlock(minecraft, camera);

		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		DistanceInfo distance = distanceInfo(playerFootPos(minecraft), hitResult.getBlockPos());
		String xSegment = "X:" + distance.x();
		String zSegment = "Z:" + distance.z();
		String ySegment = "Y:" + distance.y();
		String mSegment = "M:" + distance.manhattan();
		String separator = ", ";
		int rawTextWidth = hudTextWidth(minecraft, xSegment)
				+ hudTextWidth(minecraft, separator)
				+ hudTextWidth(minecraft, zSegment)
				+ hudTextWidth(minecraft, separator)
				+ hudTextWidth(minecraft, ySegment)
				+ hudTextWidth(minecraft, separator)
				+ hudTextWidth(minecraft, mSegment);
		float textScale = (minecraft.font.lineHeight + DISTANCE_HUD_FONT_SIZE_INCREASE) / (float) minecraft.font.lineHeight;
		int textWidth = Mth.ceil(rawTextWidth * textScale);
		int textHeight = Mth.ceil(minecraft.font.lineHeight * textScale);
		int paddingX = 7;
		int paddingY = 5;
		int boxWidth = textWidth + paddingX * 2;
		int boxHeight = textHeight + paddingY * 2;
		int x = guiGraphics.guiWidth() / 2 + 12;
		int y = guiGraphics.guiHeight() / 2 - boxHeight / 2;
		int background = 0x8C06181E;
		int glow = 0xAA36F6FF;
		int dimGlow = 0x5536F6FF;
		int text = 0xFFE9FFFF;

		guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, background);
		guiGraphics.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, dimGlow);
		guiGraphics.fill(x + 1, y + boxHeight - 3, x + boxWidth - 1, y + boxHeight - 2, 0x3324C7D9);
		guiGraphics.outline(x, y, boxWidth, boxHeight, glow);

		for (int scanY = y + 4; scanY < y + boxHeight - 3; scanY += 4) {
			guiGraphics.fill(x + 2, scanY, x + boxWidth - 2, scanY + 1, 0x2236F6FF);
		}

		int textX = x + paddingX;
		int textY = y + paddingY;
		guiGraphics.pose().pushMatrix();
		try {
			guiGraphics.pose().translate(textX, textY);
			guiGraphics.pose().scale(textScale);
			int localTextX = 0;
			localTextX = drawHudText(guiGraphics, minecraft, xSegment, localTextX, 0, DISTANCE_X_COLOR);
			localTextX = drawHudText(guiGraphics, minecraft, separator, localTextX, 0, text);
			localTextX = drawHudText(guiGraphics, minecraft, zSegment, localTextX, 0, DISTANCE_Z_COLOR);
			localTextX = drawHudText(guiGraphics, minecraft, separator, localTextX, 0, text);
			localTextX = drawHudText(guiGraphics, minecraft, ySegment, localTextX, 0, DISTANCE_Y_COLOR);
			localTextX = drawHudText(guiGraphics, minecraft, separator, localTextX, 0, text);
			drawHudText(guiGraphics, minecraft, mSegment, localTextX, 0, text);
		} finally {
			guiGraphics.pose().popMatrix();
		}
	}

	private static int drawHudText(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, String text, int x, int y, int color) {
		Component component = hudText(text);
		guiGraphics.text(minecraft.font, component, x, y, color, false);
		return x + minecraft.font.width(component);
	}

	private static int hudTextWidth(Minecraft minecraft, String text) {
		return minecraft.font.width(hudText(text));
	}

	private static Component hudText(String text) {
		return Component.literal(text);
	}

	private static BlockHitResult findViewedBlock(Minecraft minecraft, Camera camera) {
		Vec3 from = camera.position();
		double reach = Math.max(16.0, minecraft.options.renderDistance().get() * 16.0);
		Vec3 to = from.add(
				camera.forwardVector().x() * reach,
				camera.forwardVector().y() * reach,
				camera.forwardVector().z() * reach);
		ClipContext context = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());

		return minecraft.level.clip(context);
	}

	private static DistanceInfo distanceInfo(BlockPos playerFootPos, BlockPos targetPos) {
		int dx = targetPos.getX() - playerFootPos.getX();
		int dy = targetPos.getY() - playerFootPos.getY();
		int dz = targetPos.getZ() - playerFootPos.getZ();
		int x = Math.abs(dx);
		int z = Math.abs(dz);
		int y = Math.abs(dy);
		int manhattan = x + z + y;

		return new DistanceInfo(x, z, y, manhattan);
	}

	private static BlockPos playerFootPos(Minecraft minecraft) {
		return minecraft.player.getOnPos();
	}

	private static boolean isGlassBlock(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	private static boolean isIncludedThinFullLikeBlock(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.equals("dirt_path") || path.equals("soul_sand") || path.equals("farmland") || path.equals("mud");
	}

	static boolean shouldRenderOutline(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (isGlassBlock(state)) {
			return false;
		}

		return state.isCollisionShapeFullBlock(level, pos)
				|| state.getBlock() instanceof SlabBlock
				|| state.getBlock() instanceof StairBlock
				|| state.getBlock() instanceof CarpetBlock
				|| isIncludedThinFullLikeBlock(state);
	}

	static boolean usesExperimentalLineOutlines() {
		return false;
	}

	static int outlineColor(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		int rgb = state.getMapColor(level, pos).col;
		float red = ((rgb >> 16) & 0xFF) / 255.0f;
		float green = ((rgb >> 8) & 0xFF) / 255.0f;
		float blue = (rgb & 0xFF) / 255.0f;
		float luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f;

		if (luminance <= DARK_LUMINANCE_MAX) {
			red = adjustContrast(red, DARK_CONTRAST_ADJUSTMENT);
			green = adjustContrast(green, DARK_CONTRAST_ADJUSTMENT);
			blue = adjustContrast(blue, DARK_CONTRAST_ADJUSTMENT);
		} else {
			red = adjustBrightness(red, DEFAULT_BRIGHTNESS_ADJUSTMENT);
			green = adjustBrightness(green, DEFAULT_BRIGHTNESS_ADJUSTMENT);
			blue = adjustBrightness(blue, DEFAULT_BRIGHTNESS_ADJUSTMENT);
		}

		return ARGB.colorFromFloat(outlineAlpha(), red, green, blue);
	}

	private static float adjustBrightness(float value, float adjustment) {
		return clamp01(value * (1.0f + adjustment));
	}

	private static float adjustContrast(float value, float adjustment) {
		return clamp01((value - 0.5f) * (1.0f + adjustment) + 0.5f);
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private static float outlineAlpha() {
		return Math.min(FacetConfig.opacity(), OUTLINE_ALPHA_MARKER_MAX);
	}

	static boolean touchesExteriorFace(AABB box, AABB bounds, Direction direction) {
		return switch (direction) {
			case DOWN -> Math.abs(box.minY - bounds.minY) <= EDGE_EPSILON;
			case UP -> Math.abs(box.maxY - bounds.maxY) <= EDGE_EPSILON;
			case NORTH -> Math.abs(box.minZ - bounds.minZ) <= EDGE_EPSILON;
			case SOUTH -> Math.abs(box.maxZ - bounds.maxZ) <= EDGE_EPSILON;
			case WEST -> Math.abs(box.minX - bounds.minX) <= EDGE_EPSILON;
			case EAST -> Math.abs(box.maxX - bounds.maxX) <= EDGE_EPSILON;
		};
	}

	private static void renderDistancePath(LevelRenderContext context) {
		if (!distanceHudVisible || !FacetConfig.distancePathVisible()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		Camera camera = FacetMcBridge.mainCamera(minecraft);
		BlockHitResult hitResult = findViewedBlock(minecraft, camera);

		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		CameraRenderState renderCamera = context.levelState().cameraRenderState;

		if (renderCamera == null || renderCamera.pos == null) {
			return;
		}

		BlockPos startPos = playerFootPos(minecraft);
		BlockPos targetPos = hitResult.getBlockPos();
		BlockPos xCorner = new BlockPos(targetPos.getX(), startPos.getY(), startPos.getZ());
		BlockPos zCorner = new BlockPos(targetPos.getX(), startPos.getY(), targetPos.getZ());
		int xColor = distancePathBrightnessOverlayColor(255, 32, 96);
		int zColor = distancePathBrightnessOverlayColor(255, 255, 32);
		int yColor = distancePathBrightnessOverlayColor(57, 255, 20);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(), (pose, consumer) -> {
			renderPathSegmentBlocks(pose, consumer, renderCamera.pos, startPos, xCorner, xColor);
			renderPathSegmentBlocks(pose, consumer, renderCamera.pos, xCorner, zCorner, zColor);
			renderPathSegmentBlocks(pose, consumer, renderCamera.pos, zCorner, targetPos, yColor);
		});
	}

	private static void renderHoverOutline(LevelRenderContext context) {
		if (!FacetConfig.hoverEnabled()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		ClientLevel level = minecraft.level;

		if (level == null) {
			return;
		}

		BlockHitResult hitResult;
		int color;

		if (minecraft.hitResult instanceof BlockHitResult nearHitResult && nearHitResult.getType() == HitResult.Type.BLOCK) {
			hitResult = nearHitResult;
			color = -1;
		} else {
			hitResult = findViewedBlock(minecraft, FacetMcBridge.mainCamera(minecraft));

			if (hitResult.getType() != HitResult.Type.BLOCK) {
				return;
			}

			color = outOfReachHoverColor();
		}

		renderHoverOutline(context, level, hitResult, color);
	}

	private static void renderHoverOutline(LevelRenderContext context, ClientLevel level, BlockHitResult hitResult, int color) {
		BlockPos pos = hitResult.getBlockPos();

		if (!level.isLoaded(pos)) {
			return;
		}

		BlockState state = level.getBlockState(pos);

		if (state.isAir()) {
			return;
		}

		Direction direction = hitResult.getDirection();
		Vec3 hitLocation = hitResult.getLocation();
		Vec3 localHit = new Vec3(hitLocation.x - pos.getX(), hitLocation.y - pos.getY(), hitLocation.z - pos.getZ());
		VoxelShape shape = state.getShape(level, pos);
		AABB box = shape.isEmpty() ? null : hoverFaceBox(shape, direction, localHit);

		if (box == null) {
			box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		}

		CameraRenderState camera = context.levelState().cameraRenderState;

		if (camera == null || camera.pos == null) {
			return;
		}

		AABB faceBox = box;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(),
				(pose, consumer) -> renderHoverFace(pose, consumer, pos, camera.pos, faceBox, direction, color));
	}

	private static AABB hoverFaceBox(VoxelShape shape, Direction direction, Vec3 localHit) {
		AABB[] bestBox = new AABB[1];
		double[] bestDistance = {Double.MAX_VALUE};

		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
			double distance = faceDistance(box, direction, localHit);

			if (distance <= HOVER_FACE_EPSILON && distance < bestDistance[0]) {
				bestBox[0] = box;
				bestDistance[0] = distance;
			}
		});

		return bestBox[0];
	}

	private static double faceDistance(AABB box, Direction direction, Vec3 localHit) {
		return switch (direction) {
			case DOWN -> contains(localHit.x, box.minX, box.maxX) && contains(localHit.z, box.minZ, box.maxZ)
					? Math.abs(localHit.y - box.minY) : Double.MAX_VALUE;
			case UP -> contains(localHit.x, box.minX, box.maxX) && contains(localHit.z, box.minZ, box.maxZ)
					? Math.abs(localHit.y - box.maxY) : Double.MAX_VALUE;
			case NORTH -> contains(localHit.x, box.minX, box.maxX) && contains(localHit.y, box.minY, box.maxY)
					? Math.abs(localHit.z - box.minZ) : Double.MAX_VALUE;
			case SOUTH -> contains(localHit.x, box.minX, box.maxX) && contains(localHit.y, box.minY, box.maxY)
					? Math.abs(localHit.z - box.maxZ) : Double.MAX_VALUE;
			case WEST -> contains(localHit.z, box.minZ, box.maxZ) && contains(localHit.y, box.minY, box.maxY)
					? Math.abs(localHit.x - box.minX) : Double.MAX_VALUE;
			case EAST -> contains(localHit.z, box.minZ, box.maxZ) && contains(localHit.y, box.minY, box.maxY)
					? Math.abs(localHit.x - box.maxX) : Double.MAX_VALUE;
		};
	}

	private static boolean contains(double value, double min, double max) {
		return value >= min - HOVER_FACE_EPSILON && value <= max + HOVER_FACE_EPSILON;
	}

	private static void renderHoverFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera, AABB box, Direction direction, int color) {
		renderOutlineFaceLines(pose, consumer, pos, camera, box, direction, HOVER_SURFACE_BIAS, color, FacetConfig.hoverWidth());
	}

	private static void renderOutlineFaceLines(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera, AABB box, Direction direction,
			double surfaceBias, int color, float width) {
		double biasX = direction.getStepX() * surfaceBias;
		double biasY = direction.getStepY() * surfaceBias;
		double biasZ = direction.getStepZ() * surfaceBias;

		switch (direction) {
			case DOWN -> renderHorizontalHoverFace(pose, consumer, pos, camera, box, box.minY + biasY, biasX, 0.0, biasZ, color, width);
			case UP -> renderHorizontalHoverFace(pose, consumer, pos, camera, box, box.maxY + biasY, biasX, 0.0, biasZ, color, width);
			case NORTH -> renderZHoverFace(pose, consumer, pos, camera, box, box.minZ + biasZ, biasX, biasY, 0.0, color, width);
			case SOUTH -> renderZHoverFace(pose, consumer, pos, camera, box, box.maxZ + biasZ, biasX, biasY, 0.0, color, width);
			case WEST -> renderXHoverFace(pose, consumer, pos, camera, box, box.minX + biasX, 0.0, biasY, biasZ, color, width);
			case EAST -> renderXHoverFace(pose, consumer, pos, camera, box, box.maxX + biasX, 0.0, biasY, biasZ, color, width);
		}
	}

	private static void renderHorizontalHoverFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera, AABB box, double y, double biasX, double biasY, double biasZ, int color, float width) {
		double minX = box.minX + biasX;
		double maxX = box.maxX + biasX;
		double minZ = box.minZ + biasZ;
		double maxZ = box.maxZ + biasZ;

		emitOutlineLine(pose, consumer, pos, camera, minX, y + biasY, minZ, maxX, y + biasY, minZ, 0, color, width);
		emitOutlineLine(pose, consumer, pos, camera, maxX, y + biasY, minZ, maxX, y + biasY, maxZ, 1, color, width);
		emitOutlineLine(pose, consumer, pos, camera, maxX, y + biasY, maxZ, minX, y + biasY, maxZ, 2, color, width);
		emitOutlineLine(pose, consumer, pos, camera, minX, y + biasY, maxZ, minX, y + biasY, minZ, 3, color, width);
	}

	private static void renderZHoverFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera, AABB box, double z, double biasX, double biasY, double biasZ, int color, float width) {
		double minX = box.minX + biasX;
		double maxX = box.maxX + biasX;
		double minY = box.minY + biasY;
		double maxY = box.maxY + biasY;

		emitOutlineLine(pose, consumer, pos, camera, minX, minY, z + biasZ, maxX, minY, z + biasZ, 0, color, width);
		emitOutlineLine(pose, consumer, pos, camera, maxX, minY, z + biasZ, maxX, maxY, z + biasZ, 1, color, width);
		emitOutlineLine(pose, consumer, pos, camera, maxX, maxY, z + biasZ, minX, maxY, z + biasZ, 2, color, width);
		emitOutlineLine(pose, consumer, pos, camera, minX, maxY, z + biasZ, minX, minY, z + biasZ, 3, color, width);
	}

	private static void renderXHoverFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera, AABB box, double x, double biasX, double biasY, double biasZ, int color, float width) {
		double minY = box.minY + biasY;
		double maxY = box.maxY + biasY;
		double minZ = box.minZ + biasZ;
		double maxZ = box.maxZ + biasZ;

		emitOutlineLine(pose, consumer, pos, camera, x + biasX, minY, minZ, x + biasX, minY, maxZ, 0, color, width);
		emitOutlineLine(pose, consumer, pos, camera, x + biasX, minY, maxZ, x + biasX, maxY, maxZ, 1, color, width);
		emitOutlineLine(pose, consumer, pos, camera, x + biasX, maxY, maxZ, x + biasX, maxY, minZ, 2, color, width);
		emitOutlineLine(pose, consumer, pos, camera, x + biasX, maxY, minZ, x + biasX, minY, minZ, 3, color, width);
	}

	private static void emitOutlineLine(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Vec3 camera,
			double x1, double y1, double z1,
			double x2, double y2, double z2,
			int colorIndex, int color, float width) {
		float startX = (float) (pos.getX() + x1 - camera.x);
		float startY = (float) (pos.getY() + y1 - camera.y);
		float startZ = (float) (pos.getZ() + z1 - camera.z);
		float endX = (float) (pos.getX() + x2 - camera.x);
		float endY = (float) (pos.getY() + y2 - camera.y);
		float endZ = (float) (pos.getZ() + z2 - camera.z);
		float deltaX = endX - startX;
		float deltaY = endY - startY;
		float deltaZ = endZ - startZ;

		if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= 1.0e-8f) {
			return;
		}

		Vector3f normal = new Vector3f(deltaX, deltaY, deltaZ).normalize();
		int startColor = color == -1 ? hoverColor(colorIndex) : color;
		int endColor = color == -1 ? hoverColor(colorIndex + 1) : color;

		consumer.addVertex(pose, startX, startY, startZ)
				.setColor(startColor)
				.setNormal(pose, normal)
				.setLineWidth(width);
		consumer.addVertex(pose, endX, endY, endZ)
				.setColor(endColor)
				.setNormal(pose, normal)
				.setLineWidth(width);
	}

	private static void renderPathSegmentBlocks(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, BlockPos from, BlockPos to, int color) {
		if (from.equals(to)) {
			return;
		}

		int stepX = Integer.compare(to.getX(), from.getX());
		int stepY = Integer.compare(to.getY(), from.getY());
		int stepZ = Integer.compare(to.getZ(), from.getZ());
		BlockPos first = new BlockPos(from.getX() + stepX, from.getY() + stepY, from.getZ() + stepZ);

		double minX = Math.min(first.getX(), to.getX()) - DISTANCE_PATH_SURFACE_BIAS - camera.x;
		double minY = Math.min(first.getY(), to.getY()) - DISTANCE_PATH_SURFACE_BIAS - camera.y;
		double minZ = Math.min(first.getZ(), to.getZ()) - DISTANCE_PATH_SURFACE_BIAS - camera.z;
		double maxX = Math.max(first.getX(), to.getX()) + 1.0 + DISTANCE_PATH_SURFACE_BIAS - camera.x;
		double maxY = Math.max(first.getY(), to.getY()) + 1.0 + DISTANCE_PATH_SURFACE_BIAS - camera.y;
		double maxZ = Math.max(first.getZ(), to.getZ()) + 1.0 + DISTANCE_PATH_SURFACE_BIAS - camera.z;

		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.DOWN, color);
		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.UP, color);
		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.NORTH, color);
		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.SOUTH, color);
		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.WEST, color);
		emitPathFace(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, Direction.EAST, color);
	}

	private static void emitPathFace(PoseStack.Pose pose, VertexConsumer consumer,
			double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ,
			Direction direction, int color) {
		float normalX = direction.getStepX();
		float normalY = direction.getStepY();
		float normalZ = direction.getStepZ();

		switch (direction) {
			case DOWN -> emitPathQuad(pose, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, normalX, normalY, normalZ, color);
			case UP -> emitPathQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, normalX, normalY, normalZ, color);
			case NORTH -> emitPathQuad(pose, consumer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, normalX, normalY, normalZ, color);
			case SOUTH -> emitPathQuad(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, normalX, normalY, normalZ, color);
			case WEST -> emitPathQuad(pose, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, normalX, normalY, normalZ, color);
			case EAST -> emitPathQuad(pose, consumer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, normalX, normalY, normalZ, color);
		}
	}

	private static void emitPathQuad(PoseStack.Pose pose, VertexConsumer consumer,
			double x1, double y1, double z1,
			double x2, double y2, double z2,
			double x3, double y3, double z3,
			double x4, double y4, double z4,
			float normalX, float normalY, float normalZ, int color) {
		emitPathVertex(pose, consumer, x1, y1, z1, normalX, normalY, normalZ, color);
		emitPathVertex(pose, consumer, x2, y2, z2, normalX, normalY, normalZ, color);
		emitPathVertex(pose, consumer, x3, y3, z3, normalX, normalY, normalZ, color);
		emitPathVertex(pose, consumer, x4, y4, z4, normalX, normalY, normalZ, color);
	}

	private static void emitPathVertex(PoseStack.Pose pose, VertexConsumer consumer,
			double x, double y, double z,
			float normalX, float normalY, float normalZ, int color) {
		consumer.addVertex(pose, (float) x, (float) y, (float) z)
				.setColor(color)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	private static int hoverColor(int offset) {
		float hue = (float) ((System.nanoTime() % 4_000_000_000L) / 4_000_000_000.0);
		int alpha = Math.round(FacetConfig.hoverOpacity() * 255.0f);
		return Mth.hsvToArgb(Mth.positiveModulo(hue + offset * 0.16f, 1.0f), 1.0f, 1.0f, alpha);
	}

	private static int outOfReachHoverColor() {
		int alpha = Math.round(FacetConfig.hoverOpacity() * 255.0f);
		return ARGB.color(alpha, ARGB.red(OUT_OF_REACH_HOVER_COLOR), ARGB.green(OUT_OF_REACH_HOVER_COLOR), ARGB.blue(OUT_OF_REACH_HOVER_COLOR));
	}

	private static int distancePathBrightnessOverlayColor(int red, int green, int blue) {
		float phase = (float) ((System.nanoTime() % 1_800_000_000L) / 1_800_000_000.0);
		float brightness = 0.84f + 0.16f * (0.5f + 0.5f * Mth.sin(phase * Mth.TWO_PI));
		int alpha = 92;

		return ARGB.color(alpha,
				Math.round(red * brightness),
				Math.round(green * brightness),
				Math.round(blue * brightness));
	}

	private record DistanceInfo(int x, int z, int y, int manhattan) {
	}

}

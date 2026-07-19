package com.facet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

final class GraffitiWheelScreen extends Screen {
	private static final int OPTION_SIZE = 40;
	private static final int TEXTURE_SIZE = 32;
	private static final int SOURCE_TEXTURE_SIZE = 128;
	private static final int OPTION_RADIUS = 80;
	private static final double FAN_START_DEGREES = -150.0;
	private static final double FAN_STEP_DEGREES = 40.0;
	private static final long ANIMATION_DURATION_NANOS = 300_000_000L;
	private static final int CLEAR_WIDTH = 76;
	private static final int CLEAR_HEIGHT = 22;
	private static final int OPTION_BACKGROUND = 0xCC0A1016;
	private static final int OPTION_HOVER = 0xE0203D49;
	private static final int OPTION_BORDER = 0xCC35F6FF;
	private static final int OPTION_SELECTED = 0xFFFFD43B;
	private static final int OPTION_DISABLED = 0x99000000;
	private static final int CLEAR_BACKGROUND = 0xDD6B1C24;
	private static final int CLEAR_HOVER = 0xFFF04452;
	private static final int CLEAR_DISABLED = 0xAA252525;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int WHEEL_ALPHA = 0xD9;

	private final String world;
	private final Identifier dimension;
	private final BlockPos pos;
	private final Direction direction;
	private final Identifier blockId;
	private int originX;
	private int originY;
	private long animationStartNanos;
	private double exitStartFactor = 1.0;
	private float frameAlpha;
	private boolean originInitialized;
	private boolean exiting;
	private boolean closeScheduled;

	GraffitiWheelScreen(String world, Identifier dimension, BlockPos pos, Direction direction, Identifier blockId) {
		super(Component.translatable("screen.facet.graffiti.title"));
		this.world = world;
		this.dimension = dimension;
		this.pos = pos;
		this.direction = direction;
		this.blockId = blockId;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		initializeOrigin(mouseX, mouseY);
		long now = System.nanoTime();
		double animationFactor = animationFactor(now);

		if (exiting && now - animationStartNanos >= ANIMATION_DURATION_NANOS) {
			scheduleClose();
			return;
		}

		this.frameAlpha = (float) animationFactor;
		boolean canApply = canApplyGraffiti();
		GraffitiType currentType = GraffitiStore.getType(pos, direction);
		GraffitiType hoveredType = null;

		graphics.centeredText(this.font, this.title, originX, originY - OPTION_RADIUS - OPTION_SIZE / 2 - 20,
				applyWheelAlpha(TEXT_COLOR));

		for (GraffitiType type : GraffitiType.values()) {
			Bounds bounds = optionBounds(type, originX, originY, animationFactor);
			boolean hovered = bounds.contains(mouseX, mouseY);
			int borderColor = currentType == type ? OPTION_SELECTED : OPTION_BORDER;

			if (hovered) {
				hoveredType = type;
			}

			graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
					applyWheelAlpha(hovered && canApply ? OPTION_HOVER : OPTION_BACKGROUND));
			graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), applyWheelAlpha(borderColor));
			graphics.blit(RenderPipelines.GUI_TEXTURED, type.textureId(), bounds.x() + 4, bounds.y() + 4,
					0.0f, 0.0f, TEXTURE_SIZE, TEXTURE_SIZE,
					SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE,
					applyWheelAlpha(TEXT_COLOR));
			graphics.fill(bounds.x() + 3, bounds.y() + 3, bounds.x() + 15, bounds.y() + 15,
					applyWheelAlpha(0xCC000000));
			graphics.text(this.font, Integer.toString(type.number()), bounds.x() + 6, bounds.y() + 5,
					applyWheelAlpha(TEXT_COLOR), false);

			if (!canApply) {
				graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
						applyWheelAlpha(OPTION_DISABLED));
			}
		}

		if (hoveredType != null) {
			graphics.centeredText(this.font, Component.translatable(hoveredType.translationKey()), originX,
					originY - OPTION_RADIUS - OPTION_SIZE / 2 - 9, applyWheelAlpha(TEXT_COLOR));
		}

		Bounds clearBounds = clearBounds(originX, originY);
		boolean canClear = currentType != null && targetMatches();
		boolean clearHovered = clearBounds.contains(mouseX, mouseY);
		int clearColor = canClear ? (clearHovered ? CLEAR_HOVER : CLEAR_BACKGROUND) : CLEAR_DISABLED;
		graphics.fill(clearBounds.x(), clearBounds.y(), clearBounds.right(), clearBounds.bottom(), applyWheelAlpha(clearColor));
		graphics.outline(clearBounds.x(), clearBounds.y(), clearBounds.width(), clearBounds.height(),
				applyWheelAlpha(canClear ? OPTION_BORDER : 0xFF666666));
		graphics.centeredText(this.font, Component.translatable("screen.facet.graffiti.clear"), originX, clearBounds.y() + 7,
				applyWheelAlpha(canClear ? TEXT_COLOR : 0xFF999999));

		graphics.centeredText(this.font, Component.translatable("screen.facet.graffiti.hint"), originX,
				originY + CLEAR_HEIGHT / 2 + 12, applyWheelAlpha(0xFFB8DDE5));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (exiting) {
			return true;
		}

		int key = event.key();

		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
			apply(GraffitiType.byNumber(key - GLFW.GLFW_KEY_0));
			return true;
		}

		if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
			clear();
			return true;
		}

		if (key == GLFW.GLFW_KEY_ESCAPE || key == InputConstants.KEY_G) {
			closeScreen();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (exiting) {
			return true;
		}

		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}

		initializeOrigin((int) event.x(), (int) event.y());
		double animationFactor = animationFactor(System.nanoTime());

		for (GraffitiType type : GraffitiType.values()) {
			if (optionBounds(type, originX, originY, animationFactor).contains(event.x(), event.y())) {
				apply(type);
				return true;
			}
		}

		if (clearBounds(originX, originY).contains(event.x(), event.y())) {
			clear();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		beginExit();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void apply(GraffitiType type) {
		if (!targetMatches()) {
			targetChanged();
			return;
		}

		BlockState state = this.minecraft.level.getBlockState(pos);
		GraffitiEligibility.Result result = GraffitiEligibility.evaluate(this.minecraft.level, pos, state, direction);

		if (result != GraffitiEligibility.Result.ALLOWED) {
			return;
		}

		GraffitiStore.Change change = GraffitiStore.set(pos, direction, state, type);

		if (change != GraffitiStore.Change.UNCHANGED) {
			FacetMcBridge.rebuildBlockSection(this.minecraft, pos);
			playSpraySound();
			this.minecraft.player.sendOverlayMessage(Component.translatable(
					change == GraffitiStore.Change.ADDED
							? "message.facet.graffiti.added"
							: "message.facet.graffiti.replaced"));
		}

		closeScreen();
	}

	private void playSpraySound() {
		double soundX = pos.getX() + 0.5 + direction.getStepX() * 0.501;
		double soundY = pos.getY() + 0.5 + direction.getStepY() * 0.501;
		double soundZ = pos.getZ() + 0.5 + direction.getStepZ() * 0.501;
		float pitch = 0.95f + this.minecraft.level.getRandom().nextFloat() * 0.10f;
		this.minecraft.level.playLocalSound(soundX, soundY, soundZ, SoundEvents.BRUSH_GENERIC,
				SoundSource.BLOCKS, 0.8f, pitch, false);
	}

	private void clear() {
		if (!targetMatches()) {
			targetChanged();
			return;
		}

		if (GraffitiStore.clear(pos, direction)) {
			FacetMcBridge.rebuildBlockSection(this.minecraft, pos);
			this.minecraft.player.sendOverlayMessage(Component.translatable("message.facet.graffiti.removed"));
		}

		closeScreen();
	}

	private boolean canApplyGraffiti() {
		if (!targetMatches()) {
			return false;
		}

		BlockState state = this.minecraft.level.getBlockState(pos);
		return GraffitiEligibility.evaluate(this.minecraft.level, pos, state, direction) == GraffitiEligibility.Result.ALLOWED;
	}

	private boolean targetMatches() {
		if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
			return false;
		}

		if (!world.equals(FacetMcBridge.worldScope(this.minecraft, this.minecraft.level))
				|| !dimension.equals(this.minecraft.level.dimension().identifier())) {
			return false;
		}

		return blockId.equals(BuiltInRegistries.BLOCK.getKey(this.minecraft.level.getBlockState(pos).getBlock()));
	}

	private void targetChanged() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.sendOverlayMessage(Component.translatable("message.facet.graffiti.target_changed"));
		}

		closeScreen();
	}

	private void closeScreen() {
		beginExit();
	}

	private static Bounds optionBounds(GraffitiType type, int originX, int originY, double animationFactor) {
		int half = OPTION_SIZE / 2;
		double angle = Math.toRadians(FAN_START_DEGREES + (type.number() - 1) * FAN_STEP_DEGREES);
		int centerX = originX + (int) Math.round(Math.cos(angle) * OPTION_RADIUS * animationFactor);
		int centerY = originY + (int) Math.round(Math.sin(angle) * OPTION_RADIUS * animationFactor);
		return new Bounds(centerX - half, centerY - half, OPTION_SIZE, OPTION_SIZE);
	}

	private static Bounds clearBounds(int centerX, int centerY) {
		return new Bounds(centerX - CLEAR_WIDTH / 2, centerY - CLEAR_HEIGHT / 2, CLEAR_WIDTH, CLEAR_HEIGHT);
	}

	private int applyWheelAlpha(int color) {
		int alpha = color >>> 24;
		int animatedAlpha = Math.round(alpha * (WHEEL_ALPHA / 255.0f) * frameAlpha);
		return (color & 0x00FFFFFF) | (animatedAlpha << 24);
	}

	private void initializeOrigin(int mouseX, int mouseY) {
		if (originInitialized) {
			return;
		}

		originX = mouseX;
		originY = mouseY;
		animationStartNanos = System.nanoTime();
		originInitialized = true;
	}

	private void beginExit() {
		if (exiting) {
			return;
		}

		long now = System.nanoTime();
		exitStartFactor = animationFactor(now);
		animationStartNanos = now;
		exiting = true;
	}

	private double animationFactor(long now) {
		if (!originInitialized) {
			return 0.0;
		}

		double elapsed = Math.clamp((double) (now - animationStartNanos) / ANIMATION_DURATION_NANOS, 0.0, 1.0);
		double eased = cubicBezierEase(elapsed);
		return exiting ? exitStartFactor * (1.0 - eased) : eased;
	}

	private void scheduleClose() {
		if (closeScheduled || this.minecraft == null) {
			return;
		}

		closeScheduled = true;
		this.minecraft.execute(() -> FacetMcBridge.showScreen(this.minecraft, null));
	}

	private static double cubicBezierEase(double progress) {
		if (progress <= 0.0 || progress >= 1.0) {
			return progress;
		}

		double low = 0.0;
		double high = 1.0;

		for (int iteration = 0; iteration < 12; iteration++) {
			double parameter = (low + high) * 0.5;

			if (cubicBezierCoordinate(parameter, 0.1, 0.2) < progress) {
				low = parameter;
			} else {
				high = parameter;
			}
		}

		return cubicBezierCoordinate((low + high) * 0.5, 0.8, 1.0);
	}

	private static double cubicBezierCoordinate(double parameter, double firstControl, double secondControl) {
		double inverse = 1.0 - parameter;
		return 3.0 * inverse * inverse * parameter * firstControl
				+ 3.0 * inverse * parameter * parameter * secondControl
				+ parameter * parameter * parameter;
	}

	private record Bounds(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}
}

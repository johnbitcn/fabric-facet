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
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

final class GraffitiWheelScreen extends Screen {
	private static final int OPTION_SIZE = 64;
	private static final int TEXTURE_SIZE = 56;
	private static final int SOURCE_TEXTURE_SIZE = 64;
	private static final int OPTION_RADIUS = 72;
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

	private final String world;
	private final Identifier dimension;
	private final BlockPos pos;
	private final Direction direction;
	private final Identifier blockId;

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
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		boolean canApply = canApplyGraffiti();
		GraffitiType currentType = GraffitiStore.getType(pos, direction);
		GraffitiType hoveredType = null;

		graphics.centeredText(this.font, this.title, centerX, centerY - OPTION_RADIUS - OPTION_SIZE / 2 - 20, TEXT_COLOR);

		for (GraffitiType type : GraffitiType.values()) {
			Bounds bounds = optionBounds(type, centerX, centerY);
			boolean hovered = bounds.contains(mouseX, mouseY);
			int borderColor = currentType == type ? OPTION_SELECTED : OPTION_BORDER;

			if (hovered) {
				hoveredType = type;
			}

			graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), hovered && canApply ? OPTION_HOVER : OPTION_BACKGROUND);
			graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), borderColor);
			graphics.blit(RenderPipelines.GUI_TEXTURED, type.textureId(), bounds.x() + 4, bounds.y() + 4,
					0.0f, 0.0f, TEXTURE_SIZE, TEXTURE_SIZE,
					SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE, SOURCE_TEXTURE_SIZE);
			graphics.fill(bounds.x() + 3, bounds.y() + 3, bounds.x() + 15, bounds.y() + 15, 0xCC000000);
			graphics.text(this.font, Integer.toString(type.number()), bounds.x() + 6, bounds.y() + 5, TEXT_COLOR, false);

			if (!canApply) {
				graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, OPTION_DISABLED);
			}
		}

		if (hoveredType != null) {
			graphics.centeredText(this.font, Component.translatable(hoveredType.translationKey()), centerX,
					centerY - OPTION_RADIUS - OPTION_SIZE / 2 - 9, TEXT_COLOR);
		}

		Bounds clearBounds = clearBounds(centerX, centerY);
		boolean canClear = currentType != null && targetMatches();
		boolean clearHovered = clearBounds.contains(mouseX, mouseY);
		int clearColor = canClear ? (clearHovered ? CLEAR_HOVER : CLEAR_BACKGROUND) : CLEAR_DISABLED;
		graphics.fill(clearBounds.x(), clearBounds.y(), clearBounds.right(), clearBounds.bottom(), clearColor);
		graphics.outline(clearBounds.x(), clearBounds.y(), clearBounds.width(), clearBounds.height(), canClear ? OPTION_BORDER : 0xFF666666);
		graphics.centeredText(this.font, Component.translatable("screen.facet.graffiti.clear"), centerX, clearBounds.y() + 7,
				canClear ? TEXT_COLOR : 0xFF999999);

		graphics.centeredText(this.font, Component.translatable("screen.facet.graffiti.hint"), centerX,
				centerY + OPTION_RADIUS + OPTION_SIZE / 2 + 12, 0xFFB8DDE5);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
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
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}

		int centerX = this.width / 2;
		int centerY = this.height / 2;

		for (GraffitiType type : GraffitiType.values()) {
			if (optionBounds(type, centerX, centerY).contains(event.x(), event.y())) {
				apply(type);
				return true;
			}
		}

		if (clearBounds(centerX, centerY).contains(event.x(), event.y())) {
			clear();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		closeScreen();
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
			this.minecraft.player.sendOverlayMessage(Component.translatable(
					change == GraffitiStore.Change.ADDED
							? "message.facet.graffiti.added"
							: "message.facet.graffiti.replaced"));
		}

		closeScreen();
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
		if (this.minecraft != null) {
			FacetMcBridge.showScreen(this.minecraft, null);
		}
	}

	private static Bounds optionBounds(GraffitiType type, int centerX, int centerY) {
		int half = OPTION_SIZE / 2;

		return switch (type) {
			case SQUARE -> new Bounds(centerX - half, centerY - OPTION_RADIUS - half, OPTION_SIZE, OPTION_SIZE);
			case CIRCLE -> new Bounds(centerX + OPTION_RADIUS - half, centerY - half, OPTION_SIZE, OPTION_SIZE);
			case CROSS -> new Bounds(centerX - half, centerY + OPTION_RADIUS - half, OPTION_SIZE, OPTION_SIZE);
			case TRIANGLE -> new Bounds(centerX - OPTION_RADIUS - half, centerY - half, OPTION_SIZE, OPTION_SIZE);
		};
	}

	private static Bounds clearBounds(int centerX, int centerY) {
		return new Bounds(centerX - CLEAR_WIDTH / 2, centerY - CLEAR_HEIGHT / 2, CLEAR_WIDTH, CLEAR_HEIGHT);
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

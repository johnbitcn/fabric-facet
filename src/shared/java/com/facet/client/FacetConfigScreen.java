package com.facet.client;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FacetConfigScreen extends Screen {
	private static final double EDGE_WIDTH_SETTING_MIN = 1.0;
	private static final double EDGE_WIDTH_SETTING_MAX = 20.0;
	private static final double EDGE_WIDTH_SETTING_STEP = 1.0;
	private static final int DEFAULT_PANEL_WIDTH = 360;
	private static final int MIN_PANEL_WIDTH = 300;
	private static final int SCREEN_MARGIN = 8;
	private static final int PANEL_PADDING_X = 16;
	private static final int PANEL_PADDING_Y = 14;
	private static final int COMPACT_PANEL_PADDING_Y = 8;
	private static final int COMPACT_HEIGHT_THRESHOLD = 310;
	private static final int TITLE_HEIGHT = 13;
	private static final int TITLE_TO_SECTION_GAP = 8;
	private static final int COMPACT_TITLE_TO_SECTION_GAP = 5;
	private static final int SECTION_GAP = 9;
	private static final int COMPACT_SECTION_GAP = 4;
	private static final int SECTION_INSET = 10;
	private static final int SECTION_HEADER_HEIGHT = 17;
	private static final int SECTION_BOTTOM_PADDING = 7;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 5;
	private static final int RESET_WIDTH = 72;
	private static final int CONTROL_GAP = 8;
	private static final int DONE_WIDTH = 180;
	private static final int DONE_GAP = 12;
	private static final int COMPACT_DONE_GAP = 8;
	private static final int PANEL_BACKGROUND = 0xD00A1016;
	private static final int PANEL_SHADOW = 0x66000000;
	private static final int PANEL_BORDER = 0xCC35F6FF;
	private static final int PANEL_BORDER_DIM = 0x5535F6FF;
	private static final int PANEL_HIGHLIGHT = 0x442CF4FF;
	private static final int SECTION_BACKGROUND = 0x8C10232C;
	private static final int SECTION_BORDER = 0x5535F6FF;
	private static final int SECTION_ACCENT = 0xCC35F6FF;
	private static final int SECTION_SEPARATOR = 0x6635F6FF;
	private static final int TITLE_COLOR = 0xFFE9FFFF;
	private static final int SECTION_TITLE_COLOR = 0xFF95FBFF;

	private final Screen parent;
	private ValueSlider edgeWidthSlider;
	private ValueSlider hoverWidthSlider;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentWidth;
	private int titleY;
	private int generalSectionY;
	private int blockSectionY;
	private int hoverSectionY;

	public FacetConfigScreen(Screen parent) {
		super(Component.translatable("config.facet.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		boolean compact = this.height < COMPACT_HEIGHT_THRESHOLD;
		int panelPaddingY = compact ? COMPACT_PANEL_PADDING_Y : PANEL_PADDING_Y;
		int titleToSectionGap = compact ? COMPACT_TITLE_TO_SECTION_GAP : TITLE_TO_SECTION_GAP;
		int sectionGap = compact ? COMPACT_SECTION_GAP : SECTION_GAP;
		int doneGap = compact ? COMPACT_DONE_GAP : DONE_GAP;
		int generalSectionHeight = sectionHeight(4);
		int blockSectionHeight = sectionHeight(1);
		int hoverSectionHeight = sectionHeight(1);

		panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(DEFAULT_PANEL_WIDTH, this.width - SCREEN_MARGIN * 2));
		contentWidth = panelWidth - PANEL_PADDING_X * 2;
		panelHeight = panelPaddingY * 2
				+ TITLE_HEIGHT
				+ titleToSectionGap
				+ generalSectionHeight
				+ sectionGap
				+ blockSectionHeight
				+ sectionGap
				+ hoverSectionHeight
				+ doneGap
				+ ROW_HEIGHT;
		panelX = (this.width - panelWidth) / 2;
		panelY = Math.max(SCREEN_MARGIN, (this.height - panelHeight) / 2);
		contentX = panelX + PANEL_PADDING_X;
		titleY = panelY + panelPaddingY + 2;

		int y = panelY + panelPaddingY + TITLE_HEIGHT + titleToSectionGap;
		generalSectionY = y;
		y += generalSectionHeight + sectionGap;
		blockSectionY = y;
		y += blockSectionHeight + sectionGap;
		hoverSectionY = y;
		y += hoverSectionHeight + doneGap;

		int x = controlX();
		int sliderWidth = sliderWidth();
		int resetX = resetX();
		int controlWidth = controlWidth();
		int rowY = firstRowY(generalSectionY);

		addRenderableWidget(Button.builder(enabledMessage(), button -> {
			FacetConfig.setEnabled(!FacetConfig.enabled());
			button.setMessage(enabledMessage());
		}).bounds(x, rowY, controlWidth, ROW_HEIGHT).build());
		rowY += ROW_HEIGHT + ROW_GAP;

		addRenderableWidget(Button.builder(hoverEnabledMessage(), button -> {
			FacetConfig.setHoverEnabled(!FacetConfig.hoverEnabled());
			button.setMessage(hoverEnabledMessage());
		}).bounds(x, rowY, controlWidth, ROW_HEIGHT).build());
		rowY += ROW_HEIGHT + ROW_GAP;

		addRenderableWidget(Button.builder(distancePathVisibleMessage(), button -> {
			FacetConfig.setDistancePathVisible(!FacetConfig.distancePathVisible());
			button.setMessage(distancePathVisibleMessage());
		}).bounds(x, rowY, controlWidth, ROW_HEIGHT).build());
		rowY += ROW_HEIGHT + ROW_GAP;

		addRenderableWidget(Button.builder(placementPreviewEnabledMessage(), button -> {
			FacetConfig.setPlacementPreviewEnabled(!FacetConfig.placementPreviewEnabled());
			button.setMessage(placementPreviewEnabledMessage());
		}).bounds(x, rowY, controlWidth, ROW_HEIGHT).build());
		rowY = firstRowY(blockSectionY);

		edgeWidthSlider = new ValueSlider(
				x,
				rowY,
				sliderWidth,
				ROW_HEIGHT,
				"config.facet.width",
				EDGE_WIDTH_SETTING_MIN,
				EDGE_WIDTH_SETTING_MAX,
				edgeWidthSetting(),
				value -> FacetConfig.setEdgeWidth(edgeWidthFromSetting(value)),
				FacetConfigScreen::formatWholeNumber,
				EDGE_WIDTH_SETTING_STEP);
		addRenderableWidget(edgeWidthSlider);
		addRenderableWidget(Button.builder(Component.translatable("config.facet.reset"), button -> {
			FacetConfig.resetEdgeWidth();
			edgeWidthSlider.setActualValue(edgeWidthSetting());
		}).bounds(resetX, rowY, RESET_WIDTH, ROW_HEIGHT).build());
		rowY = firstRowY(hoverSectionY);

		hoverWidthSlider = new ValueSlider(
				x,
				rowY,
				sliderWidth,
				ROW_HEIGHT,
				"config.facet.hover_width",
				FacetConfig.MIN_HOVER_WIDTH,
				FacetConfig.MAX_HOVER_WIDTH,
				FacetConfig.hoverWidth(),
				FacetConfig::setHoverWidth,
				FacetConfigScreen::formatHoverWidth,
				FacetConfig.HOVER_WIDTH_STEP);
		addRenderableWidget(hoverWidthSlider);
		addRenderableWidget(Button.builder(Component.translatable("config.facet.reset"), button -> {
			FacetConfig.resetHoverWidth();
			hoverWidthSlider.setActualValue(FacetConfig.hoverWidth());
		}).bounds(resetX, rowY, RESET_WIDTH, ROW_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(this.width / 2 - DONE_WIDTH / 2, y, DONE_WIDTH, ROW_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawPanel(graphics);
		drawSection(graphics, generalSectionY, sectionHeight(4), Component.translatable("config.facet.section.general"));
		drawSection(graphics, blockSectionY, sectionHeight(1), Component.translatable("config.facet.section.block_outline"));
		drawSection(graphics, hoverSectionY, sectionHeight(1), Component.translatable("config.facet.section.hover_outline"));
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		FacetMcBridge.showScreen(this.minecraft, parent);
	}

	private static Component enabledMessage() {
		return Component.translatable(
				"config.facet.outline",
				Component.translatable(FacetConfig.enabled() ? "options.on" : "options.off"));
	}

	private static Component hoverEnabledMessage() {
		return Component.translatable(
				"config.facet.hover_outline",
				Component.translatable(FacetConfig.hoverEnabled() ? "options.on" : "options.off"));
	}

	private static Component distancePathVisibleMessage() {
		return Component.translatable(
				"config.facet.distance_path",
				Component.translatable(FacetConfig.distancePathVisible() ? "options.on" : "options.off"));
	}

	private static Component placementPreviewEnabledMessage() {
		return Component.translatable(
				"config.facet.placement_preview",
				Component.translatable(FacetConfig.placementPreviewEnabled() ? "options.on" : "options.off"));
	}

	private void drawPanel(GuiGraphicsExtractor graphics) {
		graphics.fill(panelX + 2, panelY + 3, panelX + panelWidth + 2, panelY + panelHeight + 3, PANEL_SHADOW);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
		graphics.outline(panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2, PANEL_BORDER_DIM);
		graphics.fill(panelX + 10, panelY + 1, panelX + 58, panelY + 2, PANEL_HIGHLIGHT);
		graphics.fill(panelX + panelWidth - 58, panelY + panelHeight - 2, panelX + panelWidth - 10, panelY + panelHeight - 1, PANEL_HIGHLIGHT);
		graphics.centeredText(this.font, this.title, this.width / 2, titleY, TITLE_COLOR);
	}

	private void drawSection(GuiGraphicsExtractor graphics, int y, int height, Component title) {
		int x = contentX;

		graphics.fill(x, y, x + contentWidth, y + height, SECTION_BACKGROUND);
		graphics.outline(x, y, contentWidth, height, SECTION_BORDER);
		graphics.fill(x, y, x + 2, y + height, SECTION_ACCENT);
		graphics.fill(x + 2, y + 1, x + contentWidth - 1, y + 2, PANEL_HIGHLIGHT);
		graphics.text(this.font, title.getString(), x + SECTION_INSET, y + 5, SECTION_TITLE_COLOR, false);
		graphics.horizontalLine(x + SECTION_INSET, x + contentWidth - SECTION_INSET - 1, y + SECTION_HEADER_HEIGHT - 2, SECTION_SEPARATOR);
	}

	private static int sectionHeight(int rows) {
		return SECTION_HEADER_HEIGHT + rows * ROW_HEIGHT + (rows - 1) * ROW_GAP + SECTION_BOTTOM_PADDING;
	}

	private int firstRowY(int sectionY) {
		return sectionY + SECTION_HEADER_HEIGHT;
	}

	private int controlX() {
		return contentX + SECTION_INSET;
	}

	private int controlWidth() {
		return contentWidth - SECTION_INSET * 2;
	}

	private int sliderWidth() {
		return controlWidth() - RESET_WIDTH - CONTROL_GAP;
	}

	private int resetX() {
		return controlX() + sliderWidth() + CONTROL_GAP;
	}

	private static String formatWholeNumber(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

	private static String formatHoverWidth(double value) {
		return String.format(Locale.ROOT, "%.0f px", value);
	}

	private static double edgeWidthSetting() {
		return FacetConfig.effectiveEdgeWidth() / FacetConfig.EDGE_WIDTH_UNIT;
	}

	private static double edgeWidthFromSetting(double value) {
		return value * FacetConfig.EDGE_WIDTH_UNIT;
	}

	private interface ValueFormatter {
		String format(double value);
	}

	private static final class ValueSlider extends AbstractSliderButton {
		private final String labelKey;
		private final double min;
		private final double max;
		private final DoubleConsumer setter;
		private final ValueFormatter formatter;
		private final double step;

		private ValueSlider(int x, int y, int width, int height, String labelKey, double min, double max, double value, DoubleConsumer setter, ValueFormatter formatter) {
			this(x, y, width, height, labelKey, min, max, value, setter, formatter, 0.0);
		}

		private ValueSlider(int x, int y, int width, int height, String labelKey, double min, double max, double value, DoubleConsumer setter, ValueFormatter formatter, double step) {
			super(x, y, width, height, Component.empty(), normalize(snap(value, min, max, step), min, max));
			this.labelKey = labelKey;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.formatter = formatter;
			this.step = step;
			updateMessage();
		}

		private void setActualValue(double value) {
			this.value = normalize(snap(value, min, max, step), min, max);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("config.facet.slider", Component.translatable(labelKey), formatter.format(actualValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(actualValue());
		}

		private double actualValue() {
			return snap(min + (max - min) * this.value, min, max, step);
		}

		private static double normalize(double value, double min, double max) {
			return (Math.max(min, Math.min(max, value)) - min) / (max - min);
		}

		private static double snap(double value, double min, double max, double step) {
			double clamped = Math.max(min, Math.min(max, value));

			if (step <= 0.0) {
				return clamped;
			}

			double snapped = Math.round(clamped / step) * step;
			return Math.max(min, Math.min(max, snapped));
		}
	}
}

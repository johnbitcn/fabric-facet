package com.facet.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class FacetKeyConflictNotifier {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("facet", "icon.png");
	private static final long STARTUP_SCAN_DELAY_NANOS = 1_000_000_000L;
	private static final long FLICKER_INTERVAL_NANOS = 70_000_000L;
	private static final float HOLOGRAM_BASE_ALPHA = 0.29f;
	private static final float HOLOGRAM_FLICKER_ALPHA_RANGE = 0.13f;
	private static final float PANEL_BRIGHTNESS_MULTIPLIER = 1.3f;
	private static final int BACKGROUND_DIM = 0x70000000;
	private static final int CHAMFER = 5;
	private static List<KeyMapping> facetMappings = List.of();
	private static long startupScanAtNanos = Long.MIN_VALUE;
	private static boolean startupScanComplete;
	private static ScreenPresenter screenPresenter;

	private FacetKeyConflictNotifier() {
	}

	static void register(ScreenPresenter presenter, KeyMapping... mappings) {
		screenPresenter = presenter;
		facetMappings = Arrays.stream(mappings).filter(Objects::nonNull).toList();
		startupScanAtNanos = Long.MIN_VALUE;
		startupScanComplete = false;
	}

	static void tick(Minecraft minecraft) {
		if (startupScanComplete || facetMappings.isEmpty() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		long now = System.nanoTime();
		if (startupScanAtNanos == Long.MIN_VALUE) {
			startupScanAtNanos = now + STARTUP_SCAN_DELAY_NANOS;
			return;
		}
		if (now < startupScanAtNanos) {
			return;
		}
		startupScanComplete = true;
		List<String> conflictingKeys = findConflictingKeys(minecraft);
		if (!conflictingKeys.isEmpty() && screenPresenter != null) {
			screenPresenter.show(minecraft, new ConflictScreen(List.copyOf(conflictingKeys)));
		}
	}

	private static List<String> findConflictingKeys(Minecraft minecraft) {
		TreeSet<String> conflictingKeys = new TreeSet<>();
		for (KeyMapping facet : facetMappings) {
			if (facet.isUnbound()) {
				continue;
			}
			for (KeyMapping other : minecraft.options.keyMappings) {
				if (other == facet || other.isUnbound() || !facet.same(other)) {
					continue;
				}
				conflictingKeys.add(facet.getTranslatedKeyMessage().getString());
				break;
			}
		}
		return new ArrayList<>(conflictingKeys);
	}

	private static float hologramOpacity(long timeNanos) {
		long random = mix(timeNanos / FLICKER_INTERVAL_NANOS);
		float opacity = HOLOGRAM_BASE_ALPHA + ((random >>> 40) & 0xFFFFFFL) / 16_777_215.0f * HOLOGRAM_FLICKER_ALPHA_RANGE;
		return (random & 31L) == 0L ? opacity * 0.58f : opacity;
	}

	private static void fillChamfered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x + CHAMFER, y, x + width - CHAMFER, y + height, color);
		graphics.fill(x, y + CHAMFER, x + width, y + height - CHAMFER, color);
	}

	private static void outlineChamfered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x + CHAMFER, y, x + width - CHAMFER, y + 1, color);
		graphics.fill(x + CHAMFER, y + height - 1, x + width - CHAMFER, y + height, color);
		graphics.fill(x, y + CHAMFER, x + 1, y + height - CHAMFER, color);
		graphics.fill(x + width - 1, y + CHAMFER, x + width, y + height - CHAMFER, color);
		for (int offset = 1; offset < CHAMFER; offset++) {
			graphics.fill(x + CHAMFER - offset, y + offset, x + CHAMFER - offset + 1, y + offset + 1, color);
			graphics.fill(x + width - CHAMFER + offset - 1, y + offset, x + width - CHAMFER + offset, y + offset + 1, color);
			graphics.fill(x + CHAMFER - offset, y + height - offset - 1, x + CHAMFER - offset + 1, y + height - offset, color);
			graphics.fill(x + width - CHAMFER + offset - 1, y + height - offset - 1, x + width - CHAMFER + offset, y + height - offset, color);
		}
	}

	private static int alpha(int color, float multiplier) {
		int value = Math.round(((color >>> 24) & 0xFF) * multiplier);
		return color & 0x00FFFFFF | value << 24;
	}

	private static long mix(long value) {
		value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
		value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	@FunctionalInterface
	interface ScreenPresenter {
		void show(Minecraft minecraft, Screen screen);
	}

	private static final class ConflictScreen extends Screen {
		private final List<String> conflictingKeys;
		private List<net.minecraft.util.FormattedCharSequence> summaryLines = List.of();
		private int panelX;
		private int panelY;
		private int panelWidth;
		private int panelHeight;

		private ConflictScreen(List<String> conflictingKeys) {
			super(Component.translatable("message.facet.key_conflict.title"));
			this.conflictingKeys = conflictingKeys;
		}

		@Override
		protected void init() {
			panelWidth = 246;
			summaryLines = font.split(Component.translatable("message.facet.key_conflict.summary", String.join("、", conflictingKeys)), panelWidth - 20);
			panelHeight = 40 + summaryLines.size() * font.lineHeight;
			panelX = (width - panelWidth) / 2;
			panelY = (height - panelHeight) / 2;
			Component closeLabel = Component.translatable("message.facet.key_conflict.close");
			int buttonWidth = font.width(closeLabel) + 20;
			addRenderableWidget(Button.builder(closeLabel, button -> onClose())
					.bounds(panelX + panelWidth - buttonWidth - 10, panelY + 8, buttonWidth, font.lineHeight + 10).build());
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			long now = System.nanoTime();
			float opacity = Math.min(1.0f, hologramOpacity(now) * PANEL_BRIGHTNESS_MULTIPLIER);

			graphics.fill(0, 0, width, height, BACKGROUND_DIM);
			fillChamfered(graphics, panelX, panelY, panelWidth, panelHeight, alpha(0xD8051720, opacity));
			outlineChamfered(graphics, panelX, panelY, panelWidth, panelHeight, alpha(0xCC36F6FF, opacity));
			graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 3, alpha(0x8836F6FF, opacity));
			graphics.fill(panelX + 1, panelY + panelHeight - 3, panelX + panelWidth - 1, panelY + panelHeight - 1, alpha(0x4424C7D9, opacity));
			for (int scanY = panelY + 6; scanY < panelY + panelHeight - 3; scanY += 4) {
				graphics.fill(panelX + 2, scanY, panelX + panelWidth - 2, scanY + 1, alpha(0x1836F6FF, opacity));
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, panelX + 10, panelY - 16,
					0.0f, 0.0f, 32, 32, 512, 512, 512, 512, alpha(0xFFFFFFFF, opacity));
			graphics.text(font, title, panelX + 50, panelY + 8, alpha(0xFFB8FBFF, opacity), false);
			int textY = panelY + 34;
			for (net.minecraft.util.FormattedCharSequence summaryLine : summaryLines) {
				graphics.text(font, summaryLine, panelX + 10, textY, alpha(0xFFFFFFFF, opacity), false);
				textY += font.lineHeight;
			}
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public void onClose() {
			screenPresenter.show(minecraft, null);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}

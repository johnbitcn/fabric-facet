package com.facet.client;

import net.neoforged.neoforge.common.ModConfigSpec;

final class FacetNeoForgeOutlineConfig {
	static final ModConfigSpec SPEC;
	private static final ModConfigSpec.BooleanValue ENABLED;
	private static final ModConfigSpec.IntValue EDGE_WIDTH;
	private static final ModConfigSpec.BooleanValue HOVER_ENABLED;
	private static final ModConfigSpec.DoubleValue HOVER_WIDTH;
	private static final ModConfigSpec.BooleanValue DISTANCE_PATH_VISIBLE;
	private static final ModConfigSpec.BooleanValue PLACEMENT_PREVIEW_ENABLED;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		ENABLED = builder
				.comment("Whether Facet's core block outlines are included in chunk meshes.")
				.translation("config.facet.outline")
				.define("enabled", true);
		EDGE_WIDTH = builder
				.comment("Facet block-outline width in 1/64 block units.")
				.translation("config.facet.width")
				.defineInRange("edge_width", 2, 1, 20);
		HOVER_ENABLED = builder
				.comment("Whether Facet replaces the vanilla targeted-block outline.")
				.translation("config.facet.hover_outline")
				.define("hover_enabled", true);
		HOVER_WIDTH = builder
				.comment("Width of Facet's targeted-block outline in pixels.")
				.translation("config.facet.hover_width")
				.defineInRange("hover_width", 2.0, 1.0, 8.0);
		DISTANCE_PATH_VISIBLE = builder
				.comment("Whether the Manhattan path is rendered while the distance HUD is visible.")
				.translation("config.facet.distance_path")
				.define("distance_path_visible", true);
		PLACEMENT_PREVIEW_ENABLED = builder
				.comment("Whether Facet renders a holographic preview before placing a block.")
				.translation("config.facet.placement_preview")
				.define("placement_preview_enabled", true);
		SPEC = builder.build();
	}

	private FacetNeoForgeOutlineConfig() {
	}

	static boolean enabled() {
		return ENABLED.getAsBoolean();
	}

	static boolean toggleEnabled() {
		setEnabled(!enabled());
		return enabled();
	}

	static void setEnabled(boolean enabled) {
		ENABLED.set(enabled);
		ENABLED.save();
	}

	static int edgeWidthSetting() {
		return EDGE_WIDTH.get();
	}

	static double edgeWidth() {
		return edgeWidthSetting() / 64.0;
	}

	static void setEdgeWidthSetting(int width) {
		EDGE_WIDTH.set(Math.max(1, Math.min(20, width)));
		EDGE_WIDTH.save();
	}

	static boolean hoverEnabled() {
		return HOVER_ENABLED.getAsBoolean();
	}

	static float hoverWidth() {
		return HOVER_WIDTH.get().floatValue();
	}

	static boolean toggleHoverEnabled() {
		setHoverEnabled(!hoverEnabled());
		return hoverEnabled();
	}

	static void setHoverEnabled(boolean enabled) {
		HOVER_ENABLED.set(enabled);
		HOVER_ENABLED.save();
	}

	static void setHoverWidth(float width) {
		HOVER_WIDTH.set((double) Math.max(1.0f, Math.min(8.0f, Math.round(width))));
		HOVER_WIDTH.save();
	}

	static boolean distancePathVisible() {
		return DISTANCE_PATH_VISIBLE.getAsBoolean();
	}

	static void setDistancePathVisible(boolean visible) {
		DISTANCE_PATH_VISIBLE.set(visible);
		DISTANCE_PATH_VISIBLE.save();
	}

	static boolean placementPreviewEnabled() {
		return PLACEMENT_PREVIEW_ENABLED.getAsBoolean();
	}

	static void setPlacementPreviewEnabled(boolean enabled) {
		PLACEMENT_PREVIEW_ENABLED.set(enabled);
		PLACEMENT_PREVIEW_ENABLED.save();
	}
}

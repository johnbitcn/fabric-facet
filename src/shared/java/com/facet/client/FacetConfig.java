package com.facet.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

import net.fabricmc.loader.api.FabricLoader;

public final class FacetConfig {
	public static final boolean DEFAULT_ENABLED = true;
	public static final boolean DEFAULT_HOVER_ENABLED = true;
	public static final boolean DEFAULT_DISTANCE_PATH_VISIBLE = true;
	public static final float DEFAULT_OPACITY = 0.75f;
	public static final double DEFAULT_EDGE_WIDTH = 1.0 / 32.0;
	public static final float DEFAULT_HOVER_OPACITY = 1.0f;
	public static final float DEFAULT_HOVER_WIDTH = 3.0f;
	public static final float MIN_HOVER_WIDTH = 1.0f;
	public static final float MAX_HOVER_WIDTH = 8.0f;
	public static final float HOVER_WIDTH_STEP = 0.25f;
	public static final double MAX_EDGE_WIDTH = 0.30;
	private static final double STORED_MIN_EDGE_WIDTH = 1.0 / 128.0;
	private static final float OLD_DEFAULT_OPACITY = 0.8f;
	private static final double OLD_DEFAULT_EDGE_WIDTH = 1.0 / 16.0;

	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("facet.properties");
	private static boolean enabled = DEFAULT_ENABLED;
	private static boolean hoverEnabled = DEFAULT_HOVER_ENABLED;
	private static boolean distancePathVisible = DEFAULT_DISTANCE_PATH_VISIBLE;
	private static float opacity = DEFAULT_OPACITY;
	private static double edgeWidth = DEFAULT_EDGE_WIDTH;
	private static float hoverOpacity = DEFAULT_HOVER_OPACITY;
	private static float hoverWidth = DEFAULT_HOVER_WIDTH;
	private static int textureResolution = 16;

	private FacetConfig() {
	}

	public static void load() {
		Properties properties = new Properties();

		if (Files.isRegularFile(CONFIG_PATH)) {
			try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
				properties.load(input);
			} catch (IOException ignored) {
			}
		}

		enabled = Boolean.parseBoolean(properties.getProperty("enabled", Boolean.toString(DEFAULT_ENABLED)));
		hoverEnabled = Boolean.parseBoolean(properties.getProperty("hoverEnabled", Boolean.toString(DEFAULT_HOVER_ENABLED)));
		distancePathVisible = Boolean.parseBoolean(properties.getProperty("distancePathVisible", Boolean.toString(DEFAULT_DISTANCE_PATH_VISIBLE)));
		opacity = clampFloat(parseFloat(properties.getProperty("opacity", Float.toString(DEFAULT_OPACITY)), DEFAULT_OPACITY), 0.0f, 1.0f);
		edgeWidth = clampDouble(parseDouble(properties.getProperty("edgeWidth", Double.toString(DEFAULT_EDGE_WIDTH)), DEFAULT_EDGE_WIDTH), STORED_MIN_EDGE_WIDTH, MAX_EDGE_WIDTH);
		hoverOpacity = clampFloat(parseFloat(properties.getProperty("hoverOpacity", Float.toString(DEFAULT_HOVER_OPACITY)), DEFAULT_HOVER_OPACITY), 0.0f, 1.0f);
		hoverWidth = snapHoverWidth(parseFloat(properties.getProperty("hoverWidth", Float.toString(DEFAULT_HOVER_WIDTH)), DEFAULT_HOVER_WIDTH));

		if (Float.compare(opacity, OLD_DEFAULT_OPACITY) == 0) {
			opacity = DEFAULT_OPACITY;
		}

		if (Double.compare(edgeWidth, OLD_DEFAULT_EDGE_WIDTH) == 0) {
			edgeWidth = DEFAULT_EDGE_WIDTH;
		}

		save();
	}

	public static boolean enabled() {
		return enabled;
	}

	public static boolean hoverEnabled() {
		return hoverEnabled;
	}

	public static boolean distancePathVisible() {
		return distancePathVisible;
	}

	public static float opacity() {
		return opacity;
	}

	public static double edgeWidth() {
		return edgeWidth;
	}

	public static float hoverOpacity() {
		return hoverOpacity;
	}

	public static float hoverWidth() {
		return hoverWidth;
	}

	public static double effectiveEdgeWidth() {
		return clampEdgeWidth(edgeWidth);
	}

	public static double minEdgeWidth() {
		return 0.5 / textureResolution;
	}

	public static double maxEdgeWidth() {
		return Math.max(minEdgeWidth(), MAX_EDGE_WIDTH);
	}

	public static int textureResolution() {
		return textureResolution;
	}

	public static void setTextureResolution(int value) {
		if (value <= 0 || textureResolution == value) {
			return;
		}

		textureResolution = value;
		double clampedEdgeWidth = clampEdgeWidth(edgeWidth);

		if (Double.compare(edgeWidth, clampedEdgeWidth) != 0) {
			edgeWidth = clampedEdgeWidth;
			save();
		}
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		saveAndRebuildChunks();
	}

	public static void setHoverEnabled(boolean value) {
		hoverEnabled = value;
		save();
	}

	public static void setDistancePathVisible(boolean value) {
		distancePathVisible = value;
		save();
	}

	public static void setOpacity(float value) {
		opacity = clampFloat(value, 0.0f, 1.0f);
		saveAndRebuildChunks();
	}

	public static void resetOpacity() {
		setOpacity(DEFAULT_OPACITY);
	}

	public static void setEdgeWidth(double value) {
		edgeWidth = clampEdgeWidth(value);
		saveAndRebuildChunks();
	}

	public static void resetEdgeWidth() {
		setEdgeWidth(DEFAULT_EDGE_WIDTH);
	}

	public static void setHoverOpacity(float value) {
		hoverOpacity = clampFloat(value, 0.0f, 1.0f);
		save();
	}

	public static void resetHoverOpacity() {
		setHoverOpacity(DEFAULT_HOVER_OPACITY);
	}

	public static void setHoverWidth(double value) {
		hoverWidth = snapHoverWidth(value);
		save();
	}

	public static void resetHoverWidth() {
		setHoverWidth(DEFAULT_HOVER_WIDTH);
	}

	private static void saveAndRebuildChunks() {
		save();
		rebuildChunks();
	}

	private static void save() {
		Properties properties = new Properties();
		properties.setProperty("enabled", Boolean.toString(enabled));
		properties.setProperty("hoverEnabled", Boolean.toString(hoverEnabled));
		properties.setProperty("distancePathVisible", Boolean.toString(distancePathVisible));
		properties.setProperty("opacity", Float.toString(opacity));
		properties.setProperty("edgeWidth", Double.toString(edgeWidth));
		properties.setProperty("hoverOpacity", Float.toString(hoverOpacity));
		properties.setProperty("hoverWidth", Float.toString(hoverWidth));

		try {
			Files.createDirectories(CONFIG_PATH.getParent());

			try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
				properties.store(output, "Facet client settings");
			}
		} catch (IOException ignored) {
		}
	}

	private static void rebuildChunks() {
		Minecraft minecraft = Minecraft.getInstance();

		FacetMcBridge.rebuildChunks(minecraft);
	}

	private static float parseFloat(String value, float fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		try {
			float parsed = Float.parseFloat(value);
			return Float.isFinite(parsed) ? parsed : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static double parseDouble(String value, double fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		try {
			double parsed = Double.parseDouble(value);
			return Double.isFinite(parsed) ? parsed : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static float clampFloat(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampEdgeWidth(double value) {
		return clampDouble(value, minEdgeWidth(), maxEdgeWidth());
	}

	private static float snapHoverWidth(double value) {
		double clamped = clampDouble(value, MIN_HOVER_WIDTH, MAX_HOVER_WIDTH);
		double snapped = Math.round(clamped / HOVER_WIDTH_STEP) * HOVER_WIDTH_STEP;
		return (float) clampDouble(snapped, MIN_HOVER_WIDTH, MAX_HOVER_WIDTH);
	}
}

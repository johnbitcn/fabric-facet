package com.facet.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

final class FacetOutlineRules {
	static final double DEFAULT_EDGE_WIDTH = 1.0 / 32.0;
	static final double SURFACE_BIAS = 1.0 / 1024.0;
	static final float OUTLINE_ALPHA = 1.0f;
	private static final int HIGH_LIGHTNESS_MIN = 85;
	private static final int LOW_LIGHTNESS_MAX = 15;
	private static final int NEUTRAL_SATURATION_MAX = 15;

	private FacetOutlineRules() {
	}

	static boolean shouldRender(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass")) {
			return false;
		}

		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return state.isCollisionShapeFullBlock(level, pos)
				|| state.getBlock() instanceof SlabBlock
				|| state.getBlock() instanceof StairBlock
				|| state.getBlock() instanceof CarpetBlock
				|| path.equals("dirt_path") || path.equals("soul_sand") || path.equals("farmland") || path.equals("mud");
	}

	static boolean touchesBlockBoundary(Direction direction,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return switch (direction) {
			case DOWN -> Math.abs(minY) <= FacetShapeEdges.AXIS_EPSILON;
			case UP -> Math.abs(maxY - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
			case NORTH -> Math.abs(minZ) <= FacetShapeEdges.AXIS_EPSILON;
			case SOUTH -> Math.abs(maxZ - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
			case WEST -> Math.abs(minX) <= FacetShapeEdges.AXIS_EPSILON;
			case EAST -> Math.abs(maxX - 1.0) <= FacetShapeEdges.AXIS_EPSILON;
		};
	}

	static int outlineColor(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return mapColorBorder(state.getMapColor(level, pos).col, OUTLINE_ALPHA);
	}

	static int mapColorBorder(int rgb, float alpha) {
		int[] hsl = rgbToHsl(rgb);
		int hue;
		int saturation;
		int lightness;

		if (hsl[2] > HIGH_LIGHTNESS_MIN) {
			hue = 215;
			saturation = 15;
			lightness = 65;
		} else if (hsl[2] < LOW_LIGHTNESS_MAX) {
			hue = 235;
			saturation = 25;
			lightness = 30;
		} else if (hsl[1] <= NEUTRAL_SATURATION_MAX) {
			hue = 220;
			saturation = 12;
			lightness = Math.max(10, (int) (hsl[2] * 0.70f));
		} else {
			if (hsl[0] < 60) {
				hue = wrapHue(hsl[0] - 12);
			} else if (hsl[0] < 260) {
				hue = wrapHue(hsl[0] + 14);
			} else {
				hue = wrapHue(hsl[0] - 10);
			}

			saturation = clamp((int) (hsl[1] * 1.15f + 10.0f), 0, 100);
			lightness = clamp((int) (hsl[2] * 0.65f), 12, 100);
		}

		float[] borderRgb = hslToRgb(hue, saturation, lightness);
		return ARGB.colorFromFloat(alpha, borderRgb[0], borderRgb[1], borderRgb[2]);
	}

	private static int[] rgbToHsl(int rgb) {
		float red = ARGB.red(rgb) / 255.0f;
		float green = ARGB.green(rgb) / 255.0f;
		float blue = ARGB.blue(rgb) / 255.0f;
		float max = Math.max(red, Math.max(green, blue));
		float min = Math.min(red, Math.min(green, blue));
		float delta = max - min;
		float lightness = (max + min) * 0.5f;
		float hue = 0.0f;
		float saturation = 0.0f;

		if (delta > 0.0f) {
			saturation = delta / (1.0f - Math.abs(2.0f * lightness - 1.0f));

			if (max == red) {
				hue = ((green - blue) / delta) % 6.0f;
			} else if (max == green) {
				hue = (blue - red) / delta + 2.0f;
			} else {
				hue = (red - green) / delta + 4.0f;
			}

			hue *= 60.0f;
			if (hue < 0.0f) {
				hue += 360.0f;
			}
		}

		return new int[] {Math.round(hue), Math.round(saturation * 100.0f), Math.round(lightness * 100.0f)};
	}

	private static float[] hslToRgb(int hue, int saturation, int lightness) {
		float normalizedSaturation = saturation / 100.0f;
		float normalizedLightness = lightness / 100.0f;
		float chroma = (1.0f - Math.abs(2.0f * normalizedLightness - 1.0f)) * normalizedSaturation;
		float section = hue / 60.0f;
		float intermediate = chroma * (1.0f - Math.abs(section % 2.0f - 1.0f));
		float red;
		float green;
		float blue;

		if (section < 1.0f) {
			red = chroma;
			green = intermediate;
			blue = 0.0f;
		} else if (section < 2.0f) {
			red = intermediate;
			green = chroma;
			blue = 0.0f;
		} else if (section < 3.0f) {
			red = 0.0f;
			green = chroma;
			blue = intermediate;
		} else if (section < 4.0f) {
			red = 0.0f;
			green = intermediate;
			blue = chroma;
		} else if (section < 5.0f) {
			red = intermediate;
			green = 0.0f;
			blue = chroma;
		} else {
			red = chroma;
			green = 0.0f;
			blue = intermediate;
		}

		float match = normalizedLightness - chroma * 0.5f;
		return new float[] {red + match, green + match, blue + match};
	}

	private static int wrapHue(int hue) {
		return (hue % 360 + 360) % 360;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

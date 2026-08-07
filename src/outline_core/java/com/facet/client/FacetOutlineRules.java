package com.facet.client;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

final class FacetOutlineRules {
	static final double DEFAULT_EDGE_WIDTH = 1.0 / 32.0;
	/**
	 * Default offset along the face normal so cutout/solid depth tests keep strips above the
	 * block face. Raised from 1/1024 together with the cutout-layer switch: coplanar cutout
	 * strips z-fight without this offset. Shared by all version bridges (see
	 * {@code FacetMcBridge.outlineSurfaceBias}); changing it requires in-world re-validation
	 * on every target.
	 */
	static final double SURFACE_BIAS = 1.0 / 256.0;
	/**
	 * Vertex alpha for mesh outlines. The 26.3 custom terrain shader that consumed this
	 * marker ({@code isFacetOutline()} via {@code rawVertexColor.a < 0.999}) was removed;
	 * the value is kept so strips stay above typical cutout discard thresholds while
	 * remaining distinct from opaque (alpha 255) terrain if a shader channel returns.
	 */
	static final int OUTLINE_SHADER_ALPHA = 254;
	private static final float DARK_TEXTURE_VALUE_THRESHOLD = 15.0f;
	private static final float DARK_TEXTURE_LIGHTNESS_BOOST = 15.0f;
	/**
	 * Cached per-state scope results. Vanilla {@link BlockState} instances are shared singletons
	 * and {@code isCollisionShapeFullBlock} is a state constant, so results are position-independent
	 * and remain valid for the lifetime of the client.
	 */
	private static final ConcurrentHashMap<BlockState, Boolean> SCOPE_CACHE = new ConcurrentHashMap<>();

	private FacetOutlineRules() {
	}

	/** Apply the fixed outline alpha marker without changing RGB. */
	static int withOutlineAlpha(int rgb) {
		return ARGB.color(OUTLINE_SHADER_ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
	}

	static boolean shouldRender(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return isInScope(level, pos, state);
	}

	static boolean shouldAnalyze(BlockState state) {
		return isInScope(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state);
	}

	private static boolean isInScope(BlockGetter level, BlockPos pos, BlockState state) {
		Boolean cached = SCOPE_CACHE.get(state);

		if (cached != null) {
			return cached;
		}

		boolean computed = computeIsInScope(level, pos, state);
		Boolean raced = SCOPE_CACHE.putIfAbsent(state, computed);
		return raced != null ? raced : computed;
	}

	private static boolean computeIsInScope(BlockGetter level, BlockPos pos, BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

		if (state.getBlock() instanceof ShulkerBoxBlock || path.contains("glass")) {
			return false;
		}

		return state.isCollisionShapeFullBlock(level, pos)
				|| state.getBlock() instanceof SlabBlock
				|| state.getBlock() instanceof StairBlock
				|| state.getBlock() instanceof CarpetBlock
				|| state.getBlock() instanceof SnowLayerBlock
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

	static int boostDarkTextureLightness(int rgb) {
		float value = Math.max(ARGB.red(rgb), Math.max(ARGB.green(rgb), ARGB.blue(rgb)))
				/ 255.0f * 100.0f;
		if (value > DARK_TEXTURE_VALUE_THRESHOLD) {
			return rgb;
		}

		float[] hsl = rgbToPreciseHsl(rgb);
		float[] boosted = hslToRgb(hsl[0], hsl[1], Math.min(100.0f,
				hsl[2] + DARK_TEXTURE_LIGHTNESS_BOOST));
		return ARGB.color(
				ARGB.alpha(rgb),
				Math.round(clampUnit(boosted[0]) * 255.0f),
				Math.round(clampUnit(boosted[1]) * 255.0f),
				Math.round(clampUnit(boosted[2]) * 255.0f));
	}

	private static float[] rgbToPreciseHsl(int rgb) {
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

		return new float[] {hue, saturation * 100.0f, lightness * 100.0f};
	}

	private static float[] hslToRgb(float hue, float saturation, float lightness) {
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

	private static float clampUnit(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}

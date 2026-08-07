package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import com.facet.client.mixin.SpriteContentsAccessor;

final class FacetOutlineColor {
	private static final int FALLBACK_COLOR = 0xFFFFFFFF;
	private static final Direction[] REPRESENTATIVE_ORDER = {
			Direction.UP, Direction.NORTH, Direction.SOUTH,
			Direction.WEST, Direction.EAST, Direction.DOWN
	};
	private static final TextureSample EMPTY_SAMPLE = new TextureSample(0.0, 0.0, 0.0, 0.0);
	private static final Map<TextureAtlasSprite, Map<UvRegion, TextureSample>> SAMPLE_CACHE =
			new ConcurrentHashMap<>();
	private static final Map<BlockState, FaceColorPlan> COLOR_PLANS = new ConcurrentHashMap<>();
	private static final Map<BlockState, List<BlockTintSource>> TINT_SOURCES = new ConcurrentHashMap<>();

	private FacetOutlineColor() {
	}

	static void clearCache() {
		SAMPLE_CACHE.clear();
		COLOR_PLANS.clear();
		TINT_SOURCES.clear();
	}

	static void analyze(BlockState state, BlockStateModel model) {
		if (!FacetOutlineRules.shouldAnalyze(state)) {
			return;
		}

		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(0L), parts);
		FacePlanBuilder builder = new FacePlanBuilder();
		for (BlockStateModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				part.getQuads(direction).forEach(builder::accept);
			}
			part.getQuads(null).forEach(builder::accept);
		}
		COLOR_PLANS.put(state, builder.finish(state));
	}

	static FaceColors resolve(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		FaceColorPlan plan = COLOR_PLANS.get(state);
		return plan == null
				? FaceColors.fallback()
				: plan.resolve(level, pos, state);
	}

	private static TextureSample sample(TextureAtlasSprite sprite, UvRegion region) {
		return SAMPLE_CACHE.computeIfAbsent(sprite, unused -> new ConcurrentHashMap<>())
				.computeIfAbsent(region, unused -> sample(sprite.contents(), region));
	}

	private static TextureSample sample(SpriteContents contents, UvRegion region) {
		NativeImage image = ((SpriteContentsAccessor) (Object) contents).facet$getOriginalImage();
		int frameWidth = contents.width();
		int frameHeight = contents.height();
		if (frameWidth <= 0 || frameHeight <= 0) {
			return EMPTY_SAMPLE;
		}

		int columns = image.getWidth() / frameWidth;
		int rows = image.getHeight() / frameHeight;
		if (columns <= 0 || rows <= 0) {
			return EMPTY_SAMPLE;
		}

		PixelAccumulator pixels = new PixelAccumulator();
		int minX = Math.max(0, (int) Math.floor(region.minU * frameWidth));
		int maxX = Math.min(frameWidth - 1, (int) Math.ceil(region.maxU * frameWidth) - 1);
		int minY = Math.max(0, (int) Math.floor(region.minV * frameHeight));
		int maxY = Math.min(frameHeight - 1, (int) Math.ceil(region.maxV * frameHeight) - 1);

		if (contents.isAnimated()) {
			for (int frame : contents.getUniqueFrames()) {
				sampleFrame(image, pixels, frame, columns, rows,
						frameWidth, frameHeight, minX, maxX, minY, maxY);
			}
		} else {
			sampleFrame(image, pixels, 0, columns, rows,
					frameWidth, frameHeight, minX, maxX, minY, maxY);
		}
		return pixels.finish();
	}

	private static void sampleFrame(NativeImage image, PixelAccumulator pixels,
			int frame, int columns, int rows, int frameWidth, int frameHeight,
			int minX, int maxX, int minY, int maxY) {
		if (frame < 0 || frame >= columns * rows) {
			return;
		}

		int frameX = frame % columns * frameWidth;
		int frameY = frame / columns * frameHeight;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				pixels.add(image.getPixel(frameX + x, frameY + y));
			}
		}
	}

	private static UvRegion uvRegion(TextureAtlasSprite sprite, BakedQuad quad) {
		float inverseWidth = 1.0f / (sprite.getU1() - sprite.getU0());
		float inverseHeight = 1.0f / (sprite.getV1() - sprite.getV0());
		float minU = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY;
		float minV = Float.POSITIVE_INFINITY;
		float maxV = Float.NEGATIVE_INFINITY;

		for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
			float u = (UVPair.unpackU(quad.packedUV(vertex)) - sprite.getU0()) * inverseWidth;
			float v = (UVPair.unpackV(quad.packedUV(vertex)) - sprite.getV0()) * inverseHeight;
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}

		return new UvRegion(clampUnit(minU), clampUnit(maxU), clampUnit(minV), clampUnit(maxV));
	}

	private static double quadArea(BakedQuad quad) {
		return triangleArea(quad.position(0), quad.position(1), quad.position(2))
				+ triangleArea(quad.position(0), quad.position(2), quad.position(3));
	}

	private static double triangleArea(Vector3fc a, Vector3fc b, Vector3fc c) {
		double abX = b.x() - a.x();
		double abY = b.y() - a.y();
		double abZ = b.z() - a.z();
		double acX = c.x() - a.x();
		double acY = c.y() - a.y();
		double acZ = c.z() - a.z();
		double crossX = abY * acZ - abZ * acY;
		double crossY = abZ * acX - abX * acZ;
		double crossZ = abX * acY - abY * acX;
		return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * 0.5;
	}

	private static double srgbToLinear(int channel) {
		double value = channel / 255.0;
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static float linearToSrgb(double value) {
		double clamped = Math.max(0.0, Math.min(1.0, value));
		return (float) (clamped <= 0.0031308
				? clamped * 12.92
				: 1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055);
	}

	private static float clampUnit(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	static final class FaceColors {
		private final Map<Direction, Integer> colors;
		private final int firstColor;

		private FaceColors(Map<Direction, Integer> colors) {
			this.colors = colors;
			int first = FALLBACK_COLOR;

			for (Direction direction : Direction.values()) {
				Integer color = colors.get(direction);

				if (color != null) {
					first = color;
					break;
				}
			}

			this.firstColor = first;
		}

		private static FaceColors fallback() {
			Map<Direction, Integer> colors = new EnumMap<>(Direction.class);
			for (Direction direction : Direction.values()) {
				colors.put(direction, FALLBACK_COLOR);
			}
			return new FaceColors(colors);
		}

		int color(Direction direction) {
			Integer color = colors.get(direction);
			int rgb = color == null ? firstColor : color;
			// Fixed sub-opaque alpha marker for the cutout outline channel.
			return FacetOutlineRules.withOutlineAlpha(rgb);
		}
	}

	private static final class FacePlanBuilder {
		private final Map<Direction, FaceData> faces = new EnumMap<>(Direction.class);

		private void accept(BakedQuad quad) {
			double area = quadArea(quad);
			if (area <= 0.0) {
				return;
			}

			TextureAtlasSprite sprite = quad.materialInfo().sprite();
			faces.computeIfAbsent(quad.direction(), unused -> new FaceData())
					.layers.add(new Layer(sprite, uvRegion(sprite, quad),
							quad.materialInfo().tintIndex(), area));
		}

		private FaceColorPlan finish(BlockState state) {
			Map<Direction, ColorRecipe> recipes = new EnumMap<>(Direction.class);
			if (state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				Map<FaceSignature, ColorRecipe> grouped = new LinkedHashMap<>();
				for (Map.Entry<Direction, FaceData> entry : faces.entrySet()) {
					recipes.put(entry.getKey(), grouped.computeIfAbsent(entry.getValue().signature(),
							unused -> entry.getValue().recipe()));
				}
			} else {
				for (Direction direction : REPRESENTATIVE_ORDER) {
					FaceData face = faces.get(direction);
					if (face != null) {
						ColorRecipe recipe = face.recipe();
						for (Direction target : Direction.values()) {
							recipes.put(target, recipe);
						}
						break;
					}
				}
			}
			return FaceColorPlan.create(recipes);
		}
	}

	private static final class FaceData {
		private final List<Layer> layers = new ArrayList<>();

		private FaceSignature signature() {
			return new FaceSignature(layers.stream().map(Layer::signature).toList());
		}

		private ColorRecipe recipe() {
			List<SampledLayer> sampled = new ArrayList<>();
			for (Layer layer : layers) {
				TextureSample texture = sample(layer.sprite, layer.region);
				if (texture.coverage > 0.0) {
					sampled.add(new SampledLayer(texture.red, texture.green, texture.blue,
							layer.area * texture.coverage, layer.tintIndex));
				}
			}
			return new ColorRecipe(List.copyOf(sampled));
		}
	}

	private record FaceColorPlan(Map<Direction, ColorRecipe> recipes, FaceColors staticColors) {
		private static FaceColorPlan create(Map<Direction, ColorRecipe> recipes) {
			boolean tinted = false;

			for (ColorRecipe recipe : recipes.values()) {
				for (SampledLayer layer : recipe.layers) {
					if (layer.tintIndex >= 0) {
						tinted = true;
						break;
					}
				}

				if (tinted) {
					break;
				}
			}

			FaceColors staticColors = null;

			if (!tinted) {
				Map<Direction, Integer> colors = new EnumMap<>(Direction.class);
				recipes.forEach((direction, recipe) ->
						colors.put(direction, recipe.resolveUntinted()));
				staticColors = new FaceColors(colors);
			}

			return new FaceColorPlan(recipes, staticColors);
		}

		private FaceColors resolve(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			if (staticColors != null) {
				return staticColors;
			}

			List<BlockTintSource> tintSources = TINT_SOURCES.computeIfAbsent(state,
					unused -> Minecraft.getInstance().getBlockColors().getTintSources(state));
			Map<Direction, Integer> colors = new EnumMap<>(Direction.class);
			recipes.forEach((direction, recipe) ->
					colors.put(direction, recipe.resolve(tintSources, level, pos, state)));
			return colors.isEmpty() ? FaceColors.fallback() : new FaceColors(colors);
		}
	}

	private record ColorRecipe(List<SampledLayer> layers) {
		private int resolveUntinted() {
			ColorAccumulator color = new ColorAccumulator();

			for (SampledLayer layer : layers) {
				color.add(layer.red, layer.green, layer.blue, layer.weight);
			}

			return color.weight <= 0.0
					? FALLBACK_COLOR
					: FacetOutlineRules.boostDarkTextureLightness(color.rgb());
		}

		private int resolve(List<BlockTintSource> tintSources,
				BlockAndTintGetter level, BlockPos pos, BlockState state) {
			ColorAccumulator color = new ColorAccumulator();
			for (SampledLayer layer : layers) {
				int tint = layer.tintIndex < 0 || layer.tintIndex >= tintSources.size()
						? 0xFFFFFFFF
						: tintSources.get(layer.tintIndex).colorInWorld(state, level, pos);
				color.add(
						layer.red * srgbToLinear(ARGB.red(tint)),
						layer.green * srgbToLinear(ARGB.green(tint)),
						layer.blue * srgbToLinear(ARGB.blue(tint)),
						layer.weight);
			}
			return color.weight <= 0.0
					? FALLBACK_COLOR
					: FacetOutlineRules.boostDarkTextureLightness(color.rgb());
		}
	}

	private static final class PixelAccumulator {
		private double alphaSum;
		private double red;
		private double green;
		private double blue;
		private int count;

		private void add(int color) {
			double alpha = ARGB.alpha(color) / 255.0;
			count++;
			if (alpha <= 0.0) {
				return;
			}

			alphaSum += alpha;
			red += srgbToLinear(ARGB.red(color)) * alpha;
			green += srgbToLinear(ARGB.green(color)) * alpha;
			blue += srgbToLinear(ARGB.blue(color)) * alpha;
		}

		private TextureSample finish() {
			return count == 0 || alphaSum <= 0.0
					? EMPTY_SAMPLE
					: new TextureSample(red / alphaSum, green / alphaSum, blue / alphaSum,
							alphaSum / count);
		}
	}

	private static final class ColorAccumulator {
		private double red;
		private double green;
		private double blue;
		private double weight;

		private void add(double red, double green, double blue, double weight) {
			this.red += red * weight;
			this.green += green * weight;
			this.blue += blue * weight;
			this.weight += weight;
		}

		private int rgb() {
			return ARGB.colorFromFloat(1.0f,
					linearToSrgb(red / weight),
					linearToSrgb(green / weight),
					linearToSrgb(blue / weight));
		}
	}

	private record Layer(TextureAtlasSprite sprite, UvRegion region, int tintIndex, double area) {
		private LayerSignature signature() {
			return new LayerSignature(sprite.contents().name().toString(), region, tintIndex,
					Double.doubleToLongBits(area));
		}
	}

	private record LayerSignature(String sprite, UvRegion region, int tintIndex, long area) {
	}

	private record FaceSignature(List<LayerSignature> layers) {
	}

	private record SampledLayer(double red, double green, double blue, double weight, int tintIndex) {
	}

	private record TextureSample(double red, double green, double blue, double coverage) {
	}

	private record UvRegion(float minU, float maxU, float minV, float maxV) {
	}
}

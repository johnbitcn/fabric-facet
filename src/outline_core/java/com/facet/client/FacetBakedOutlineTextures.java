package com.facet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.facet.client.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One default-width variant per opaque, static block sprite. Original sprites remain
 * untouched for items, special blocks and the outline toggle. Other widths fall back
 * to geometry, avoiding a resource reload every time the width slider changes.
 */
public final class FacetBakedOutlineTextures {
	static final int MIN_SIZE = 32;
	static final int MAX_SIZE = 256;
	static final long MAX_PIXEL_BYTES = 8L * 1024 * 1024;
	private static final Logger LOGGER = LoggerFactory.getLogger("facet");
	private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("facet.bakedOutlines"));
	private static volatile Set<Identifier> available = Set.of();

	private FacetBakedOutlineTextures() {
	}

	public static boolean matchesWidth(double width) {
		return ENABLED && Math.abs(width - FacetOutlineRules.DEFAULT_EDGE_WIDTH) < 1.0e-9;
	}

	public static Identifier variantId(Identifier source) {
		return Identifier.fromNamespaceAndPath("facet",
				"baked_outline/" + source.getNamespace() + "/" + source.getPath());
	}

	static boolean available(Identifier source) {
		return available.contains(variantId(source));
	}

	public static void publish(Batch batch) {
		available = Set.copyOf(batch.names());
		if (!batch.generated().isEmpty()) {
			LOGGER.info("Baked outline atlas: {} variants, {} KiB base pixels (8 MiB budget)",
					batch.generated().size(), batch.pixelBytes() / 1024);
		}
	}

	public static Batch prepare(List<SpriteContents> originals) {
		if (!ENABLED) {
			return new Batch(originals, List.of(), Set.of(), 0);
		}
		SpriteContents white = originals.stream()
				.filter(sprite -> sprite.name().equals(Identifier.withDefaultNamespace("block/white_concrete")))
				.findFirst().orElse(null);
		// Ordinary geometry samples this texel: keep the same contrast even on concrete.
		if (white == null || !eligible(white)) {
			return new Batch(originals, List.of(), Set.of(), 0);
		}
		NativeImage whiteImage = image(white);
		int modulation = whiteImage.getPixel(white.width() / 2, white.height() / 2);
		List<SpriteContents> candidates = originals.stream().filter(FacetBakedOutlineTextures::eligible)
				.sorted(Comparator.comparingInt((SpriteContents sprite) -> outputSize(sprite.width()))
						.thenComparing(sprite -> sprite.name().toString())).toList();
		Set<Identifier> occupied = new HashSet<>();
		originals.forEach(sprite -> occupied.add(sprite.name()));
		List<SpriteContents> generated = new ArrayList<>();
		Set<Identifier> names = new HashSet<>();
		long bytes = 0;
		try {
			for (SpriteContents source : candidates) {
				Identifier id = variantId(source.name());
				int size = outputSize(source.width());
				long cost = (long) size * size * 4;
				if (occupied.contains(id) || bytes + cost > MAX_PIXEL_BYTES) {
					continue;
				}
				NativeImage baked = bakeImage(image(source), modulation);
				if (baked == null) {
					continue;
				}
				try {
					generated.add(FacetSpriteFactory.create(id, size, baked, source));
				} catch (RuntimeException | Error failure) {
					baked.close();
					throw failure;
				}
				names.add(id);
				bytes += cost;
			}
			List<SpriteContents> combined = new ArrayList<>(originals.size() + generated.size());
			combined.addAll(originals);
			combined.addAll(generated);
			return new Batch(combined, List.copyOf(generated), Set.copyOf(names), bytes);
		} catch (RuntimeException | Error failure) {
			generated.forEach(SpriteContents::close);
			throw failure;
		}
	}

	private static NativeImage image(SpriteContents sprite) {
		return ((SpriteContentsAccessor) sprite).facet$getOriginalImage();
	}

	private static boolean eligible(SpriteContents sprite) {
		Identifier name = sprite.name();
		if (!name.getPath().startsWith("block/")
				|| (name.getNamespace().equals("facet") && !name.getPath().equals("block/netherrack_average"))
				|| sprite.width() != sprite.height() || !supportedSize(sprite.width())
				|| !sprite.transparency().isOpaque() || sprite.getUniqueFrames().size() != 1) {
			return false;
		}
		NativeImage image = image(sprite);
		return image.getWidth() == sprite.width() && image.getHeight() == sprite.height();
	}

	static boolean supportedSize(int size) {
		return size >= 1 && size <= MAX_SIZE && (size & (size - 1)) == 0;
	}

	static int outputSize(int size) {
		return Math.max(MIN_SIZE, size);
	}

	/** Nearest-neighbour enlargement preserves pixel art; alpha always stays opaque. */
	static NativeImage bakeImage(NativeImage source, int modulation) {
		int inputSize = source.getWidth();
		if (inputSize != source.getHeight() || !supportedSize(inputSize)) {
			return null;
		}
		for (int y = 0; y < inputSize; y++) {
			for (int x = 0; x < inputSize; x++) {
				if (ARGB.alpha(source.getPixel(x, y)) != 255) {
					return null;
				}
			}
		}
		int mean = FacetOutlineRules.boostDarkTextureLightness(FacetOutlineColor.meanTextureColor(source));
		int color = ARGB.color(255,
				Math.round(ARGB.red(mean) * ARGB.red(modulation) / 255.0f),
				Math.round(ARGB.green(mean) * ARGB.green(modulation) / 255.0f),
				Math.round(ARGB.blue(mean) * ARGB.blue(modulation) / 255.0f));
		int size = outputSize(inputSize);
		int border = size / 32;
		NativeImage result = new NativeImage(size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean edge = x < border || y < border || x >= size - border || y >= size - border;
				result.setPixel(x, y, edge ? color
						: source.getPixel(x * inputSize / size, y * inputSize / size));
			}
		}
		return result;
	}

	public record Batch(List<SpriteContents> sprites, List<SpriteContents> generated,
			Set<Identifier> names, long pixelBytes) {
		public void closeGenerated() {
			generated.forEach(SpriteContents::close);
		}
	}
}

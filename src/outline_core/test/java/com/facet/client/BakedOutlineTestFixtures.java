package com.facet.client;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.facet.client.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.joml.Vector3f;

/** Headless fixtures supply the accessors which Mixins provide in the actual client. */
final class BakedOutlineTestFixtures implements AutoCloseable {
	private final List<SpriteContents> owned = new ArrayList<>();

	TestContents contents(String name, int size, int color) {
		return contents(name, size, size, size, color, 0);
	}

	TestContents contents(String name, int size, int width, int height, int color, float cutoff) {
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, y, color);
			}
		}
		TestContents result = new TestContents(Identifier.parse(name), size, image, cutoff);
		owned.add(result);
		return result;
	}

	Material.Baked material(String name, int size, int color, int offset) {
		return new Material.Baked(new TestSprite(contents(name, size, color), offset), false);
	}

	static BakedQuad.MaterialInfo info(Material.Baked material, int tint, int emission, boolean shade) {
		// 26.3 replaces "shade" with a direction override and adds item glint types.
		// Reflection is test-only, keeping the fixtures shared across all targets.
		try {
			RecordComponent[] components = BakedQuad.MaterialInfo.class.getRecordComponents();
			Class<?>[] types = new Class<?>[components.length];
			Object[] args = new Object[components.length];
			for (int i = 0; i < components.length; i++) {
				types[i] = components[i].getType();
				args[i] = switch (components[i].getName()) {
					case "sprite" -> material.sprite();
					case "layer" -> ChunkSectionLayer.SOLID;
					case "tintIndex" -> tint;
					case "lightEmission" -> emission;
					case "shade" -> shade;
					case "shadeDirectionOverride" -> shade ? null : Direction.UP;
					case "ambientOcclusion" -> true;
					default -> null;
				};
			}
			return BakedQuad.MaterialInfo.class.getDeclaredConstructor(types).newInstance(args);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}

	static BakedQuad quad(Material.Baked material, Direction face, int tint, int emission, boolean shade) {
		float p = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
		Vector3f[] positions = switch (face.getAxis()) {
			case X -> new Vector3f[] {new Vector3f(p, 0, 0), new Vector3f(p, 1, 0),
					new Vector3f(p, 1, 1), new Vector3f(p, 0, 1)};
			case Y -> new Vector3f[] {new Vector3f(0, p, 0), new Vector3f(1, p, 0),
					new Vector3f(1, p, 1), new Vector3f(0, p, 1)};
			case Z -> new Vector3f[] {new Vector3f(0, 0, p), new Vector3f(1, 0, p),
					new Vector3f(1, 1, p), new Vector3f(0, 1, p)};
		};
		var sprite = material.sprite();
		return new BakedQuad(positions[0], positions[1], positions[2], positions[3],
				UVPair.pack(sprite.getU(0), sprite.getV(0)), UVPair.pack(sprite.getU(1), sprite.getV(0)),
				UVPair.pack(sprite.getU(1), sprite.getV(1)), UVPair.pack(sprite.getU(0), sprite.getV(1)),
				face, info(material, tint, emission, shade));
	}

	static SimpleModelWrapper cube(Material.Baked material) {
		QuadCollection.Builder quads = new QuadCollection.Builder();
		for (Direction face : Direction.values()) {
			quads.addCulledFace(face, quad(material, face, -1, 0, true));
		}
		return new SimpleModelWrapper(quads.build(), true, material);
	}

	@Override
	public void close() {
		owned.forEach(SpriteContents::close);
	}

	static final class TestContents extends SpriteContents implements SpriteContentsAccessor {
		private final NativeImage image;
		private final float cutoff;

		TestContents(Identifier id, int size, NativeImage image, float cutoff) {
			super(id, new FrameSize(size, size), image, Optional.empty(), List.of(),
					Optional.of(new TextureMetadataSection(false, false, MipmapStrategy.AUTO, cutoff)));
			this.image = image;
			this.cutoff = cutoff;
		}

		@Override public NativeImage facet$getOriginalImage() { return image; }
		@Override public List<MetadataSectionType.WithValue<?>> facet$getAdditionalMetadata() { return List.of(); }
		@Override public MipmapStrategy facet$getMipmapStrategy() { return MipmapStrategy.AUTO; }
		@Override public float facet$getAlphaCutoffBias() { return cutoff; }
	}

	static final class TestSprite extends TextureAtlasSprite {
		TestSprite(SpriteContents contents, int offset) {
			// TextureAtlasSprite expects a texture path, not the logical AtlasIds.BLOCKS ID.
			super(Identifier.withDefaultNamespace("textures/atlas/blocks.png"),
					contents, 512, 512, offset, offset, 0);
		}
	}
}

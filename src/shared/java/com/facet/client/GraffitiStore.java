package com.facet.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.fabricmc.loader.api.FabricLoader;

final class GraffitiStore {
	private static final Path STORE_PATH = FabricLoader.getInstance().getConfigDir().resolve("facet-graffiti.properties");
	private static final Set<GraffitiKey> GRAFFITI = new HashSet<>();
	private static String activeWorld = "";
	private static Identifier activeDimension;

	private GraffitiStore() {
	}

	static void load() {
		GRAFFITI.clear();
		Properties properties = new Properties();

		if (!Files.isRegularFile(STORE_PATH)) {
			return;
		}

		try (InputStream input = Files.newInputStream(STORE_PATH)) {
			properties.load(input);
		} catch (IOException ignored) {
			return;
		}

		for (String encoded : properties.stringPropertyNames()) {
			GraffitiKey key = decode(encoded);

			if (key != null) {
				GRAFFITI.add(key);
			}
		}
	}

	static boolean setContext(String world, Identifier dimension) {
		if (world.equals(activeWorld) && dimension.equals(activeDimension)) {
			return false;
		}

		activeWorld = world;
		activeDimension = dimension;
		return true;
	}

	static boolean has(BlockPos pos, Direction direction) {
		return activeDimension != null && GRAFFITI.contains(currentKey(pos, direction));
	}

	static boolean toggle(BlockPos pos, Direction direction) {
		GraffitiKey key = currentKey(pos, direction);
		boolean added;

		if (GRAFFITI.remove(key)) {
			added = false;
		} else {
			GRAFFITI.add(key);
			added = true;
		}

		save();
		return added;
	}

	private static GraffitiKey currentKey(BlockPos pos, Direction direction) {
		return new GraffitiKey(activeWorld, activeDimension, pos.immutable(), direction);
	}

	private static void save() {
		Properties properties = new Properties();

		for (GraffitiKey key : GRAFFITI) {
			properties.setProperty(encode(key), "true");
		}

		try {
			Files.createDirectories(STORE_PATH.getParent());

			try (OutputStream output = Files.newOutputStream(STORE_PATH)) {
				properties.store(output, "Facet client-side graffiti");
			}
		} catch (IOException ignored) {
		}
	}

	private static String encode(GraffitiKey key) {
		String world = Base64.getUrlEncoder().withoutPadding().encodeToString(key.world().getBytes(StandardCharsets.UTF_8));
		BlockPos pos = key.pos();
		return String.join("|", world, key.dimension().toString(), Integer.toString(pos.getX()), Integer.toString(pos.getY()),
				Integer.toString(pos.getZ()), key.direction().getSerializedName());
	}

	private static GraffitiKey decode(String encoded) {
		String[] fields = encoded.split("\\|", -1);

		if (fields.length != 6) {
			return null;
		}

		try {
			String world = new String(Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8);
			Identifier dimension = Identifier.tryParse(fields[1]);
			Direction direction = Direction.byName(fields[5]);

			if (dimension == null || direction == null) {
				return null;
			}

			return new GraffitiKey(world, dimension,
					new BlockPos(Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4])), direction);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private record GraffitiKey(String world, Identifier dimension, BlockPos pos, Direction direction) {
	}
}

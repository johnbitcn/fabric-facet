package com.facet.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.loader.api.FabricLoader;

public final class GraffitiStore {
	private static final Path STORE_PATH = FabricLoader.getInstance().getConfigDir().resolve("facet-graffiti.properties");
	private static final Map<GraffitiKey, Identifier> GRAFFITI = new HashMap<>();
	private static final Map<GraffitiChunkKey, Set<BlockPos>> POSITIONS_BY_CHUNK = new HashMap<>();
	private static String activeWorld = "";
	private static Identifier activeDimension;
	private static boolean dirty;

	private GraffitiStore() {
	}

	static void load() {
		GRAFFITI.clear();
		POSITIONS_BY_CHUNK.clear();
		dirty = false;
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

			if (key == null) {
				continue;
			}

			String storedValue = properties.getProperty(encoded);
			Identifier blockId = "true".equals(storedValue) ? null : Identifier.tryParse(storedValue);

			if (blockId == null && !"true".equals(storedValue)) {
				continue;
			}

			GRAFFITI.put(key, blockId);
			addToChunkIndex(key);
		}
	}

	static void setContext(String world, Identifier dimension) {
		activeWorld = world;
		activeDimension = dimension;
	}

	static boolean has(BlockPos pos, Direction direction) {
		return activeDimension != null && GRAFFITI.containsKey(currentKey(pos, direction));
	}

	static boolean toggle(BlockPos pos, Direction direction, BlockState state) {
		GraffitiKey key = currentKey(pos, direction);
		boolean added;

		if (GRAFFITI.containsKey(key)) {
			GRAFFITI.remove(key);
			removeFromChunkIndexIfEmpty(key);
			added = false;
		} else {
			GRAFFITI.put(key, blockId(state));
			addToChunkIndex(key);
			added = true;
		}

		dirty = true;
		return added;
	}

	public static void reconcileConfirmedBlock(ClientLevel level, BlockPos pos, BlockState confirmedState) {
		if (activeDimension == null || !hasAny(activeWorld, activeDimension, pos)) {
			return;
		}

		Identifier confirmedBlockId = blockId(confirmedState);

		if (confirmedState.isAir() || !confirmedState.getFluidState().isEmpty()
				|| hasDifferentStoredBlock(activeWorld, activeDimension, pos, confirmedBlockId)) {
			removeAll(pos);
			return;
		}

		bindLegacyEntries(level, pos, confirmedState, confirmedBlockId);
	}

	static void reconcileChunk(ClientLevel level, ChunkPos chunkPos) {
		if (activeDimension == null || !activeDimension.equals(level.dimension().identifier())) {
			return;
		}

		GraffitiChunkKey chunkKey = new GraffitiChunkKey(activeWorld, activeDimension, chunkPos.x(), chunkPos.z());
		Set<BlockPos> positions = POSITIONS_BY_CHUNK.get(chunkKey);

		if (positions == null || positions.isEmpty()) {
			return;
		}

		for (BlockPos pos : List.copyOf(positions)) {
			reconcileConfirmedBlock(level, pos, level.getBlockState(pos));
		}
	}

	static void flush() {
		if (!dirty) {
			return;
		}

		dirty = false;
		save();
	}

	private static void bindLegacyEntries(ClientLevel level, BlockPos pos, BlockState state, Identifier blockId) {
		boolean changed = false;

		for (Direction direction : Direction.values()) {
			GraffitiKey key = currentKey(pos, direction);

			if (!GRAFFITI.containsKey(key) || GRAFFITI.get(key) != null) {
				continue;
			}

			if (GraffitiEligibility.evaluate(level, pos, state, direction) == GraffitiEligibility.Result.ALLOWED) {
				GRAFFITI.put(key, blockId);
			} else {
				GRAFFITI.remove(key);
			}

			changed = true;
		}

		if (changed) {
			removePositionFromIndexIfEmpty(activeWorld, activeDimension, pos);
			dirty = true;
		}
	}

	private static boolean removeAll(BlockPos pos) {
		boolean removed = false;

		for (Direction direction : Direction.values()) {
			GraffitiKey key = currentKey(pos, direction);

			if (GRAFFITI.containsKey(key)) {
				GRAFFITI.remove(key);
				removed = true;
			}
		}

		if (removed) {
			removePositionFromIndex(activeWorld, activeDimension, pos);
			dirty = true;
		}

		return removed;
	}

	private static boolean hasDifferentStoredBlock(String world, Identifier dimension, BlockPos pos, Identifier blockId) {
		for (Direction direction : Direction.values()) {
			GraffitiKey key = new GraffitiKey(world, dimension, pos, direction);

			if (GRAFFITI.containsKey(key)) {
				Identifier storedBlockId = GRAFFITI.get(key);

				if (storedBlockId != null && !storedBlockId.equals(blockId)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean hasAny(String world, Identifier dimension, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (GRAFFITI.containsKey(new GraffitiKey(world, dimension, pos, direction))) {
				return true;
			}
		}

		return false;
	}

	private static void addToChunkIndex(GraffitiKey key) {
		POSITIONS_BY_CHUNK.computeIfAbsent(chunkKey(key.world(), key.dimension(), key.pos()), ignored -> new HashSet<>())
				.add(key.pos());
	}

	private static void removeFromChunkIndexIfEmpty(GraffitiKey key) {
		removePositionFromIndexIfEmpty(key.world(), key.dimension(), key.pos());
	}

	private static void removePositionFromIndexIfEmpty(String world, Identifier dimension, BlockPos pos) {
		if (!hasAny(world, dimension, pos)) {
			removePositionFromIndex(world, dimension, pos);
		}
	}

	private static void removePositionFromIndex(String world, Identifier dimension, BlockPos pos) {
		GraffitiChunkKey chunkKey = chunkKey(world, dimension, pos);
		Set<BlockPos> positions = POSITIONS_BY_CHUNK.get(chunkKey);

		if (positions == null) {
			return;
		}

		positions.remove(pos);

		if (positions.isEmpty()) {
			POSITIONS_BY_CHUNK.remove(chunkKey);
		}
	}

	private static GraffitiChunkKey chunkKey(String world, Identifier dimension, BlockPos pos) {
		return new GraffitiChunkKey(world, dimension, pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static GraffitiKey currentKey(BlockPos pos, Direction direction) {
		return new GraffitiKey(activeWorld, activeDimension, pos.immutable(), direction);
	}

	private static Identifier blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock());
	}

	private static void save() {
		Properties properties = new Properties();

		for (Map.Entry<GraffitiKey, Identifier> entry : GRAFFITI.entrySet()) {
			Identifier blockId = entry.getValue();
			properties.setProperty(encode(entry.getKey()), blockId == null ? "true" : blockId.toString());
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

	private record GraffitiChunkKey(String world, Identifier dimension, int x, int z) {
	}
}

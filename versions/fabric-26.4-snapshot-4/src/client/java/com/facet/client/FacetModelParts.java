package com.facet.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class FacetModelParts {
	private FacetModelParts() {}

	static boolean collectStatic(BlockStateModel model, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		if (model.getClass() != SingleVariant.class) return false;
		model.collectParts(random, parts);
		return true;
	}

	static void collect(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
		List<BakedQuad> unculled = new ArrayList<>();
		Map<QuadAtlas, SpriteFinder> spriteFinders = new EnumMap<>(QuadAtlas.class);

		QuadEmitter emitter = Renderer.get().quadEmitter(quad -> {
			SpriteFinder finder = spriteFinders.computeIfAbsent(quad.atlas(), atlas ->
					((FabricTextureAtlas) Minecraft.getInstance().getAtlasManager()
							.getAtlasOrThrow(atlas.getId())).spriteFinder());
			BakedQuad baked = quad.toBakedQuad(finder.find(quad));
			Direction face = quad.cullFace();
			if (face == null) {
				unculled.add(baked);
			} else {
				culled.computeIfAbsent(face, unused -> new ArrayList<>()).add(baked);
			}
		});
		((FabricBlockStateModel) model).emitQuads(emitter, level, pos, state, random, face -> false);

		if (!culled.isEmpty() || !unculled.isEmpty()) {
			parts.add(new EmittedPart(culled, unculled));
		}
	}

	static Object geometryKey(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
			BlockState state, RandomSource random) {
		return ((FabricBlockStateModel) model).createGeometryKey(level, pos, state, random);
	}

	/** This snapshot feeds color analysis only; rendering continues to use the original model. */
	private record EmittedPart(Map<Direction, List<BakedQuad>> culled, List<BakedQuad> unculled)
			implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(Direction face) {
			return face == null ? unculled : culled.getOrDefault(face, List.of());
		}

		@Override
		public boolean useAmbientOcclusion() {
			return true;
		}

		@Override
		public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
			return null;
		}

		@Override
		public int materialFlags() {
			return 0;
		}
	}
}

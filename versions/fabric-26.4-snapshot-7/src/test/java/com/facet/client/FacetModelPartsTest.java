package com.facet.client;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class FacetModelPartsTest {
	@Test
	void spriteLookupUsesTheLogicalAtlasId() throws IOException {
		var lookupPaths = new ArrayList<List<String>>();
		try (var stream = getClass().getResourceAsStream("/com/facet/client/FacetModelParts.class")) {
			assertNotNull(stream);
			new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor,
						String signature, String[] exceptions) {
					var calls = new ArrayList<String>();
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
								boolean isInterface) {
							if (owner.equals("net/minecraft/client/resources/model/sprite/AtlasManager")
									&& name.equals("getAtlasOrThrow")) {
								calls.add("lookup");
							} else if (owner.equals(
									"net/fabricmc/fabric/api/client/renderer/v1/mesh/QuadAtlas")
									&& name.equals("getId")) {
								calls.add("getId");
							} else if (owner.equals(
								"net/fabricmc/fabric/api/client/renderer/v1/mesh/QuadAtlas")
									&& name.equals("getTextureLocation")) {
								calls.add("getTextureLocation");
							}
						}

						@Override
						public void visitEnd() {
							if (calls.contains("lookup")) lookupPaths.add(calls);
						}
					};
				}
			}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		assertEquals(1, lookupPaths.size());
		var calls = lookupPaths.getFirst();
		assertTrue(calls.indexOf("getId") >= 0 && calls.indexOf("getId") < calls.indexOf("lookup"));
		assertFalse(calls.contains("getTextureLocation"));
	}

	@Test
	void unknownModelsStayOnTheContextualPath() {
		ContextModel model = new ContextModel();
		List<BlockStateModelPart> parts = new ArrayList<>();

		assertFalse(FacetModelParts.collectStatic(model, null, RandomSource.create(0L), parts));
		assertTrue(parts.isEmpty());
	}

	@Test
	void geometryKeyReceivesTheOriginalContextAndMayBeNull() {
		ContextModel model = new ContextModel();
		BlockAndTintGetter level = (BlockAndTintGetter) Proxy.newProxyInstance(
				BlockAndTintGetter.class.getClassLoader(),
				new Class<?>[] { BlockAndTintGetter.class },
				(proxy, method, args) -> null);
		BlockPos pos = new BlockPos(3, 4, 5);
		BlockState state = Blocks.STONE.defaultBlockState();
		RandomSource random = RandomSource.create(7L);

		assertNull(FacetModelParts.geometryKey(model, level, pos, state, random));
		assertSame(level, model.level);
		assertSame(pos, model.pos);
		assertSame(state, model.state);
		assertSame(random, model.random);
	}

	private static final class ContextModel implements BlockStateModel, FabricBlockStateModel {
		private BlockAndTintGetter level;
		private BlockPos pos;
		private BlockState state;
		private RandomSource random;

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			throw new AssertionError("Contextual models must not be pre-collected");
		}

		@Override
		public Material.Baked particleMaterial() {
			return null;
		}

		@Override
		public int materialFlags() {
			return 0;
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
				RandomSource random) {
			this.level = level;
			this.pos = pos;
			this.state = state;
			this.random = random;
			return null;
		}
	}
}

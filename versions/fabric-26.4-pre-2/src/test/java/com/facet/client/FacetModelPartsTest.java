package com.facet.client;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class FacetModelPartsTest {
	@Test
	void singleVariantRemainsStaticWithoutAccessingTheWorld() {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var material = fixtures.material("minecraft:block/stone", 16, 0xFF808080, 0);
			var part = BakedOutlineTestFixtures.cube(material);
			var parts = new ArrayList<BlockStateModelPart>();
			assertTrue(FacetModelParts.collectStatic(new SingleVariant(part), null, RandomSource.create(0L), parts));
			assertEquals(List.of(part), parts);
		}
	}

	@Test
	void unknownModelsStayOnTheContextualPathAndDoNotCacheWorldlessKeys() {
		ContextModel model = new ContextModel();
		List<BlockStateModelPart> parts = new ArrayList<>();

		assertFalse(FacetModelParts.collectStatic(model, null, RandomSource.create(0L), parts));
		assertEquals(0, model.calls);
		assertTrue(parts.isEmpty());

		BlockAndTintGetter level = (BlockAndTintGetter) Proxy.newProxyInstance(
				BlockAndTintGetter.class.getClassLoader(),
				new Class<?>[] { BlockAndTintGetter.class },
				(proxy, method, args) -> null);
		BlockPos pos = new BlockPos(3, 4, 5);
		RandomSource random = RandomSource.create(7L);
		FacetModelParts.collect(model, level, pos, null, random, parts);
		assertEquals(1, model.calls);
		assertNull(FacetModelParts.geometryKey(model, level, pos, null, random));
	}

	@Test
	void multipartInjectionMatchesThisVersionsConstructorAndSelector() throws ReflectiveOperationException {
		var shared = Class.forName("net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel$SharedBakedState");
		var multiPart = Class.forName("net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel");
		var constructor = multiPart.getDeclaredConstructor(shared, BlockState.class);
		assertEquals(List.class, shared.getDeclaredMethod("selectModels", BlockState.class).getReturnType());
		var mixin = Class.forName("com.facet.client.mixin.MultiPartStaticChildrenMixin");
		var injection = mixin.getDeclaredMethod("facet$selectStaticChildren",
				Object.class, BlockState.class, CallbackInfo.class).getAnnotation(Inject.class);
		assertArrayEquals(new String[] {"<init>" + Type.getConstructorDescriptor(constructor)}, injection.method());
	}

	private static final class ContextModel implements BlockStateModel {
		int calls;

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			calls++;
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

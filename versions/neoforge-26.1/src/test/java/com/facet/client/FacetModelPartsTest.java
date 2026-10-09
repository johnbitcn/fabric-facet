package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
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
			assertTrue(FacetModelParts.collectStatic(new SingleVariant(part), null, RandomSource.create(0), parts));
			assertEquals(List.of(part), parts);
		}
	}

	@Test
	void dynamicModelsAreNeverQueriedWithInventedStaticContext() {
		var model = new ContextModel();
		var parts = new ArrayList<BlockStateModelPart>();
		assertFalse(FacetModelParts.collectStatic(model, null, RandomSource.create(0), parts));
		assertEquals(0, model.calls);
		assertTrue(parts.isEmpty());
	}

	@Test
	void contextualCollectionAndGeometryKeyReceiveTheSameInputs() {
		var model = new ContextModel();
		var level = BlockAndTintGetter.EMPTY;
		var pos = new BlockPos(19, 72, -31);
		var random = RandomSource.create(7);
		var parts = new ArrayList<BlockStateModelPart>();
		FacetModelParts.collect(model, level, pos, null, random, parts);
		assertSame(level, model.level);
		assertSame(pos, model.pos);
		assertSame(random, model.random);
		assertEquals(1, model.calls);
		assertSame(model.key, FacetModelParts.geometryKey(model, level, pos, null, random));
		assertSame(level, model.level);
		assertSame(pos, model.pos);
		assertSame(random, model.random);
	}

	@Test
	void nullGeometryKeyRemainsUncacheable() {
		var model = new ContextModel();
		model.key = null;
		assertNull(FacetModelParts.geometryKey(model, BlockAndTintGetter.EMPTY,
				BlockPos.ZERO, null, RandomSource.create(0)));
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

	private static final class ContextModel extends SingleVariant {
		ContextModel() { super(null); }
		int calls;
		Object key = new Object();
		BlockAndTintGetter level;
		BlockPos pos;
		RandomSource random;

		@Override
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
				RandomSource random, List<BlockStateModelPart> parts) {
			calls++;
			this.level = level;
			this.pos = pos;
			this.random = random;
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			this.level = level;
			this.pos = pos;
			this.random = random;
			return key;
		}

	}
}

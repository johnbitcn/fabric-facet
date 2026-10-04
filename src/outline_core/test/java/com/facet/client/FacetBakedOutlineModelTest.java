package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;

import static org.junit.jupiter.api.Assertions.*;

class FacetBakedOutlineModelTest {
	private final BakedOutlineTestFixtures fixtures = new BakedOutlineTestFixtures();
	private Material.Baked source;
	private Material.Baked target;

	@BeforeEach
	void setup() {
		source = fixtures.material("minecraft:block/stone", 16, 0xFF808080, 0);
		target = fixtures.material(FacetBakedOutlineTextures.variantId(source.sprite().contents().name()).toString(),
				32, 0xFF707070, 128);
		FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(),
				Set.of(target.sprite().contents().name()), 0));
	}

	@AfterEach
	void close() {
		fixtures.close();
		FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(), Set.of(), 0));
	}

	private BlockStateModelPart bake(BlockStateModelPart part) {
		return FacetBakedOutlineModel.bakePart(part, null, id -> target, FacetBakedOutlineModel::remapQuad);
	}

	@Test
	void bakedCubeKeepsSixQuadsAndCachesThemAcrossRepeatedRequests() {
		var original = BakedOutlineTestFixtures.cube(source);
		var baked = bake(original);
		assertNotNull(baked);
		assertTrue(baked.getQuads(null).isEmpty());
		for (Direction face : Direction.values()) {
			assertEquals(1, baked.getQuads(face).size());
			assertSame(baked.getQuads(face), baked.getQuads(face));
			var quad = baked.getQuads(face).getFirst();
			assertEquals(target.sprite(), quad.materialInfo().sprite());
			assertEquals(ChunkSectionLayer.SOLID, quad.materialInfo().layer());
			assertEquals(-1, quad.materialInfo().tintIndex());
			for (int vertex = 0; vertex < 4; vertex++) {
				assertEquals(original.getQuads(face).getFirst().position(vertex), quad.position(vertex));
				assertEquals(FacetBakedOutlineModel.normalizedU(original.getQuads(face).getFirst(), vertex),
						FacetBakedOutlineModel.normalizedU(quad, vertex), 1.0e-6);
				assertEquals(FacetBakedOutlineModel.normalizedV(original.getQuads(face).getFirst(), vertex),
						FacetBakedOutlineModel.normalizedV(quad, vertex), 1.0e-6);
			}
			assertEquals(source.sprite(), original.getQuads(face).getFirst().materialInfo().sprite());
		}
		assertSame(original.particleMaterial(), baked.particleMaterial());
		assertEquals(original.useAmbientOcclusion(), baked.useAmbientOcclusion());
		assertEquals(original.materialFlags(), baked.materialFlags());
	}

	@Test
	void fullCubeChangesFromThirtyQuadsToSixAndCulledFacesRemainSeparate() {
		var baked = bake(BakedOutlineTestFixtures.cube(source));
		int[] strips = {0};
		FacetShapeEdges.forEachSurfaceStrip(Shapes.block(), FacetOutlineRules.DEFAULT_EDGE_WIDTH,
				(face, x1, y1, z1, x2, y2, z2) -> strips[0]++);
		assertEquals(24, strips[0]);
		int nativeQuads = 0;
		for (Direction face : Direction.values()) {
			nativeQuads += baked.getQuads(face).size();
		}
		assertEquals(6, nativeQuads);
		assertEquals(30, nativeQuads + strips[0]);
		assertEquals(1, baked.getQuads(Direction.UP).size());
		assertTrue(baked.getQuads(null).isEmpty());
	}

	@Test
	void tintedEmissiveUnshadedAndTransparentQuadsAreRejected() {
		assertFalse(FacetBakedOutlineModel.safeQuad(BakedOutlineTestFixtures.quad(source, Direction.UP, 0, 0, true), Direction.UP));
		assertFalse(FacetBakedOutlineModel.safeQuad(BakedOutlineTestFixtures.quad(source, Direction.UP, -1, 1, true), Direction.UP));
		assertFalse(FacetBakedOutlineModel.safeQuad(BakedOutlineTestFixtures.quad(source, Direction.UP, -1, 0, false), Direction.UP));
		var transparent = fixtures.material("minecraft:block/glass", 16, 0x80808080, 0);
		assertFalse(FacetBakedOutlineModel.safeQuad(BakedOutlineTestFixtures.quad(transparent, Direction.UP, -1, 0, true), Direction.UP));
	}

	@Test
	void croppedUvAndPartialGeometryAreRejected() {
		BakedQuad q = BakedOutlineTestFixtures.quad(source, Direction.UP, -1, 0, true);
		var cropped = new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
				UVPair.pack(source.sprite().getU(0.5f), source.sprite().getV(0)),
				q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(), q.materialInfo());
		assertFalse(FacetBakedOutlineModel.safeQuad(cropped, Direction.UP));
		var partial = new BakedQuad(new Vector3f(0, 0.5f, 0), q.position1(), q.position2(), q.position3(),
				q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(), q.materialInfo());
		assertFalse(FacetBakedOutlineModel.safeQuad(partial, Direction.UP));
		assertFalse(FacetBakedOutlineModel.safeQuad(q, Direction.DOWN));
	}

	@Test
	void multilayerUnculledAndIncompleteModelsFallBackAsAWhole() {
		var original = BakedOutlineTestFixtures.cube(source);
		var doubled = new QuadCollection.Builder().addAll(original.quads())
				.addCulledFace(Direction.UP, original.getQuads(Direction.UP).getFirst()).build();
		assertNull(bake(new SimpleModelWrapper(doubled, true, source)));
		var unculled = new QuadCollection.Builder().addAll(original.quads())
				.addUnculledFace(original.getQuads(Direction.UP).getFirst()).build();
		assertNull(bake(new SimpleModelWrapper(unculled, true, source)));
		assertNull(bake(new SimpleModelWrapper(QuadCollection.EMPTY, true, source)));
	}

	@Test
	void missingAndMismatchedVariantsFallBack() {
		var original = BakedOutlineTestFixtures.cube(source);
		assertNull(FacetBakedOutlineModel.bakePart(original, null, id -> source, FacetBakedOutlineModel::remapQuad));
		FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(), Set.of(), 0));
		assertNull(bake(original));
	}

	@Test
	void netherrackReplacementRetainsFullUvsRatherThanCenterSampling() {
		var average = fixtures.material("facet:block/netherrack_average", 16, 0xFF804030, 32);
		var variant = fixtures.material(FacetBakedOutlineTextures.variantId(average.sprite().contents().name()).toString(),
				32, 0xFF704030, 128);
		FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(),
				Set.of(variant.sprite().contents().name()), 0));
		var baked = FacetBakedOutlineModel.bakePart(BakedOutlineTestFixtures.cube(source), average,
				id -> variant, FacetBakedOutlineModel::remapQuad);
		assertNotNull(baked);
		var q = baked.getQuads(Direction.UP).getFirst();
		assertEquals(0, FacetBakedOutlineModel.normalizedU(q, 0), 1.0e-6);
		assertEquals(1, FacetBakedOutlineModel.normalizedU(q, 1), 1.0e-6);
		assertEquals(1, FacetBakedOutlineModel.normalizedV(q, 2), 1.0e-6);
	}

	@Test
	void weightedVariantsRetainEveryWeightAndRejectAnyUnsafeVariant() throws ReflectiveOperationException {
		var first = new SingleVariant(BakedOutlineTestFixtures.cube(source));
		var second = new SingleVariant(BakedOutlineTestFixtures.cube(source));
		var entries = WeightedList.<BlockStateModel>of(List.of(new Weighted<>(first, 1), new Weighted<>(second, 3)));
		var baked = FacetBakedOutlineModel.bakeVariants(entries, null, id -> target, FacetBakedOutlineModel::remapQuad);
		assertNotNull(baked);
		var field = baked.getClass().getDeclaredField("list");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		var mapped = (WeightedList<BlockStateModel>) field.get(baked);
		assertEquals(List.of(1, 3), mapped.unwrap().stream().map(Weighted::weight).toList());
		for (int seed = 0; seed < 20; seed++) {
			var expected = entries.getRandomOrThrow(RandomSource.create(seed));
			int index = expected == first ? 0 : 1;
			var selected = mapped.getRandomOrThrow(RandomSource.create(seed));
			assertSame(mapped.unwrap().get(index).value(), selected);
			List<BlockStateModelPart> parts = new ArrayList<>();
			// SingleVariant is context-free; Neo's legacy WeightedVariants overload
			// would require a running FML registry even in a headless test.
			selected.collectParts(RandomSource.create(seed), parts);
			assertEquals(1, parts.size());
			assertEquals(1, parts.getFirst().getQuads(Direction.UP).size());
		}
		var unsafe = new SingleVariant(new SimpleModelWrapper(QuadCollection.EMPTY, true, source));
		assertNull(FacetBakedOutlineModel.bakeVariants(
				WeightedList.of(List.of(new Weighted<BlockStateModel>(first, 1), new Weighted<BlockStateModel>(unsafe, 3))),
				null, id -> target, FacetBakedOutlineModel::remapQuad));
	}
}

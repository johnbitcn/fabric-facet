package com.facet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacetBakedOutlineEligibilityTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void specialBlocksAndNonFullShapesNeverSelectBakedModel() {
		try (var fixtures = new BakedOutlineTestFixtures()) {
			var source = fixtures.material("minecraft:block/stone", 16, 0xFF808080, 0);
			var target = fixtures.material(FacetBakedOutlineTextures.variantId(source.sprite().contents().name()).toString(),
					32, 0xFF707070, 128);
			FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(),
					Set.of(target.sprite().contents().name()), 0));
			var model = new SingleVariant(BakedOutlineTestFixtures.cube(source));
			for (var block : List.of(Blocks.ANCIENT_DEBRIS, Blocks.BUDDING_AMETHYST, Blocks.INFESTED_STONE,
					Blocks.POWDER_SNOW, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.STONE_SLAB,
					Blocks.STONE_STAIRS, Blocks.SNOW)) {
				assertNull(FacetBakedOutlineModel.create(block.defaultBlockState(), model, null,
						id -> target, FacetBakedOutlineModel::remapQuad));
			}
			var baked = FacetBakedOutlineModel.create(Blocks.STONE.defaultBlockState(), model, null,
					id -> target, FacetBakedOutlineModel::remapQuad);
			assertNotNull(baked);
			List<BlockStateModelPart> first = new ArrayList<>(), second = new ArrayList<>();
			baked.collectParts(RandomSource.create(1), first);
			baked.collectParts(RandomSource.create(2), second);
			assertSame(first.getFirst(), second.getFirst());
		} finally {
			FacetBakedOutlineTextures.publish(new FacetBakedOutlineTextures.Batch(List.of(), List.of(), Set.of(), 0));
		}
	}
}

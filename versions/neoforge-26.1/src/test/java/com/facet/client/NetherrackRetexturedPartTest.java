package com.facet.client;

import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetherrackRetexturedPartTest {
	@Test
	void requestsOnlyNeededFacesAndPreservesDelegateFlags() {
		int[] calls = new int[7];
		BlockStateModelPart delegate = new BlockStateModelPart() {
			public List<BakedQuad> getQuads(Direction face) {
				calls[face == null ? 6 : face.ordinal()]++;
				return List.of();
			}
			@Deprecated public boolean useAmbientOcclusion() { return false; }
			public net.minecraft.util.TriState ambientOcclusion() { return net.minecraft.util.TriState.FALSE; }
			public Material.Baked particleMaterial() { return null; }
			public int materialFlags() { return BakedQuad.FLAG_ANIMATED; }
		};
		var part = new FacetNeoForgeOutlineRenderer.RetexturedPart(delegate, null);
		var north = part.getQuads(Direction.NORTH);
		assertSame(north, part.getQuads(Direction.NORTH));
		part.getQuads(null);
		part.getQuads(null);
		for (Direction face : Direction.values()) {
			assertEquals(face == Direction.NORTH ? 1 : 0, calls[face.ordinal()]);
		}
		assertEquals(1, calls[6]);
		assertEquals(net.minecraft.util.TriState.FALSE, part.ambientOcclusion());
		assertEquals(delegate.materialFlags(), part.materialFlags());
	}
}

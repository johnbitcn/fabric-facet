package com.facet.client;

import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Vanilla multipart children selected at bake time, without any world access. */
public interface StaticModelChildren {
	List<BlockStateModel> facet$staticChildren();
}

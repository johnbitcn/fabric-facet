package com.facet.client;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrackingIceBlock;
import net.minecraft.world.level.block.state.BlockState;

final class FacetBrittleIce {
	private FacetBrittleIce() {}

	static boolean isBrittleIce(BlockState state) {
		return state.is(Blocks.BRITTLE_ICE);
	}

	static int brittleIceStage(BlockState state) {
		return isBrittleIce(state) ? state.getValue(CrackingIceBlock.AGE) : -1;
	}

	static boolean shouldUseBrittleIceOutline(BlockState state, boolean outlineEnabled) {
		return outlineEnabled && isBrittleIce(state);
	}
}

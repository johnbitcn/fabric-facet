package com.facet.client;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NeoForgePlacementRotationRules {
	private NeoForgePlacementRotationRules() {
	}

	static Kind kind(BlockState state) {
		if (state.getBlock() instanceof DirectionalBlock && state.hasProperty(DirectionalBlock.FACING)) return Kind.DIRECTIONAL;
		if (state.getBlock() instanceof DispenserBlock && state.hasProperty(DispenserBlock.FACING)) return Kind.DISPENSER;
		if (state.getBlock() instanceof HorizontalDirectionalBlock && !(state.getBlock() instanceof BedBlock)
				&& state.hasProperty(HorizontalDirectionalBlock.FACING)) return Kind.HORIZONTAL;
		if ((state.getBlock() instanceof AbstractFurnaceBlock || state.getBlock() instanceof ChiseledBookShelfBlock)
				&& state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return Kind.HORIZONTAL;
		if (state.getBlock() instanceof HopperBlock && state.hasProperty(HopperBlock.FACING)) return Kind.HOPPER;
		if (state.getBlock() instanceof CrafterBlock && state.hasProperty(BlockStateProperties.ORIENTATION)) return Kind.CRAFTER;
		return null;
	}

	static Direction facing(BlockState state, Kind kind) {
		return switch (kind) {
			case DIRECTIONAL -> state.getValue(DirectionalBlock.FACING);
			case DISPENSER -> state.getValue(DispenserBlock.FACING);
			case HORIZONTAL -> state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			case HOPPER -> state.getValue(HopperBlock.FACING);
			case CRAFTER -> state.getValue(BlockStateProperties.ORIENTATION).front();
		};
	}

	static Direction top(BlockState state, Kind kind) {
		return kind == Kind.CRAFTER ? state.getValue(BlockStateProperties.ORIENTATION).top() : null;
	}

	static BlockState withFacing(BlockState state, Kind kind, Direction facing, Direction top) {
		return switch (kind) {
			case DIRECTIONAL -> state.setValue(DirectionalBlock.FACING, facing);
			case DISPENSER -> state.setValue(DispenserBlock.FACING, facing);
			case HORIZONTAL -> facing.getAxis().isHorizontal() ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : null;
			case HOPPER -> facing != Direction.UP ? state.setValue(HopperBlock.FACING, facing) : null;
			case CRAFTER -> withCrafterFacing(state, facing, top);
		};
	}

	private static BlockState withCrafterFacing(BlockState state, Direction facing, Direction top) {
		if (top == null || facing.getAxis() == top.getAxis()) return null;
		FrontAndTop orientation = FrontAndTop.fromFrontAndTop(facing, top);
		return orientation == null ? null : state.setValue(BlockStateProperties.ORIENTATION, orientation);
	}

	static Step flip(BlockState state, Kind kind, Direction hitFace) {
		Direction facing = facing(state, kind);
		if (kind == Kind.HOPPER && facing == Direction.DOWN) return null;
		Direction.Axis axis = kind == Kind.HORIZONTAL || kind == Kind.HOPPER ? Direction.Axis.Y
				: hitFace.getAxis() != facing.getAxis() ? hitFace.getAxis()
				: facing.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
		return new Step(withFacing(state, kind, facing.getOpposite(), top(state, kind)), axis, 180.0f);
	}

	enum Kind { DIRECTIONAL, DISPENSER, HORIZONTAL, HOPPER, CRAFTER }

	record Step(BlockState state, Direction.Axis axis, float residualDegrees) { }
}

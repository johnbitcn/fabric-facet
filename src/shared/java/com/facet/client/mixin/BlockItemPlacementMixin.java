package com.facet.client.mixin;

import com.facet.client.PlacementRotationController;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockItem.class)
abstract class BlockItemPlacementMixin {
	@Shadow
	protected abstract boolean canPlace(BlockPlaceContext context, BlockState state);

	@ModifyVariable(method = "place", at = @At("STORE"), ordinal = 0)
	private BlockState facet$applyPlacementRotation(BlockState state, BlockPlaceContext context) {
		return PlacementRotationController.applyPending((BlockItem) (Object) this, state, context,
				candidate -> canPlace(context, candidate));
	}
}

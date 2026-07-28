package com.facet.client.mixin;

import com.facet.client.PlacementRotationController;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void facet$armPlacementRotation(LocalPlayer player, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callbackInfo) {
		PlacementRotationController.arm(player, hand, hit);
	}
}

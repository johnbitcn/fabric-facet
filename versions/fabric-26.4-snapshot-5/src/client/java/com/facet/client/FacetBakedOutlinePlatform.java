package com.facet.client;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;

final class FacetBakedOutlinePlatform {
	private FacetBakedOutlinePlatform() {
	}

	static boolean defaultShade(BakedQuad.MaterialInfo info) {
		return info.shadeDirectionOverride() == null;
	}

	static BakedQuad.MaterialInfo remapMaterial(BakedQuad original, Material.Baked target) {
		var old = original.materialInfo();
		return new BakedQuad.MaterialInfo(target.sprite(), old.layer(), old.itemRenderType(),
				old.itemGlintRenderType(), old.itemGlintSpecialRenderType(), old.tintIndex(), old.shadeDirectionOverride(), old.lightEmission());
	}

	static boolean safeExtraData(BakedQuad quad) {
		return true;
	}
}

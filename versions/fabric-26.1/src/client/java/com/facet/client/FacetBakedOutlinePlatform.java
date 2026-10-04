package com.facet.client;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;

final class FacetBakedOutlinePlatform {
	private FacetBakedOutlinePlatform() {
	}

	static boolean defaultShade(BakedQuad.MaterialInfo info) {
		return info.shade();
	}

	static BakedQuad.MaterialInfo remapMaterial(BakedQuad original, Material.Baked target) {
		var old = original.materialInfo();
		return new BakedQuad.MaterialInfo(target.sprite(), old.layer(), old.itemRenderType(),
				old.tintIndex(), old.shade(), old.lightEmission());
	}

	static boolean safeExtraData(BakedQuad quad) {
		return true;
	}
}

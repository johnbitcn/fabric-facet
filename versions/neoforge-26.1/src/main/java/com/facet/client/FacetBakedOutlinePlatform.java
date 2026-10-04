package com.facet.client;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

final class FacetBakedOutlinePlatform {
	private FacetBakedOutlinePlatform() {
	}

	static boolean defaultShade(BakedQuad.MaterialInfo info) {
		return FacetNeoForgePlatform.defaultShade(info);
	}

	static BakedQuad.MaterialInfo remapMaterial(BakedQuad original, Material.Baked target) {
		return FacetNeoForgePlatform.remapMaterial(original.materialInfo(), target);
	}

	static boolean safeExtraData(BakedQuad quad) {
		return quad.bakedColors().equals(BakedColors.DEFAULT)
				&& quad.bakedNormals().equals(BakedNormals.UNSPECIFIED)
				&& quad.materialInfo().ambientOcclusion();
	}
}

package com.facet.client;

import net.minecraft.resources.Identifier;

enum GraffitiType {
	SQUARE("square", 1),
	CIRCLE("circle", 2),
	CROSS("cross", 3),
	TRIANGLE("triangle", 4);

	private final String id;
	private final int number;
	private final Identifier materialId;
	private final Identifier textureId;
	private final String translationKey;

	GraffitiType(String id, int number) {
		this.id = id;
		this.number = number;
		this.materialId = Identifier.fromNamespaceAndPath("facet", "block/graffiti/" + id);
		this.textureId = Identifier.fromNamespaceAndPath("facet", "textures/block/graffiti/" + id + ".png");
		this.translationKey = "screen.facet.graffiti.type." + id;
	}

	String id() {
		return id;
	}

	int number() {
		return number;
	}

	Identifier materialId() {
		return materialId;
	}

	Identifier textureId() {
		return textureId;
	}

	String translationKey() {
		return translationKey;
	}

	static GraffitiType byId(String id) {
		for (GraffitiType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}

		return null;
	}

}

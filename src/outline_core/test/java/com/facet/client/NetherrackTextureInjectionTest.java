package com.facet.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetherrackTextureInjectionTest {
	@Test
	void explicitlyTargetsTheResourceLoadingConstructor() throws ReflectiveOperationException {
		var constructor = SpriteContents.class.getConstructor(Identifier.class, FrameSize.class,
				NativeImage.class, Optional.class, List.class, Optional.class);
		var mixin = Class.forName("com.facet.client.mixin.NetherrackTextureMixin");
		var injection = mixin.getDeclaredMethod("facet$flattenNetherrackCopy", CallbackInfo.class)
				.getAnnotation(Inject.class);
		assertNotNull(injection);
		assertArrayEquals(new String[] {"<init>" + Type.getConstructorDescriptor(constructor)},
				injection.method());
	}
}

package com.facet.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BakedOutlineAtlasInjectionTest {
	@Test
	void targetsTheLogicalAtlasLoadingEntryPoint() throws ReflectiveOperationException, IOException {
		var target = SpriteLoader.class.getDeclaredMethod("loadAndStitch", ResourceManager.class,
				Identifier.class, int.class, Executor.class, Set.class);
		var selectors = new ArrayList<String>();
		// Read annotations without loading MixinExtras, which is absent in Neo's unit-test JVM.
		try (var stream = getClass().getResourceAsStream("/com/facet/client/mixin/BakedOutlineAtlasMixin.class")) {
			assertNotNull(stream);
			new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor,
						String signature, String[] exceptions) {
					if (!name.equals("facet$identifyAtlas")) return null;
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
							if (!descriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public AnnotationVisitor visitArray(String name) {
									if (!name.equals("method")) return null;
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override public void visit(String name, Object value) { selectors.add((String) value); }
									};
								}
							};
						}
					};
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		assertEquals(java.util.List.of("loadAndStitch" + Type.getMethodDescriptor(target)), selectors);
	}
}

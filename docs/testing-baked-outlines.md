# Baked outline optimization: first phase

## Scope

Ordinary Minecraft full-cube models with one opaque, untinted, normally shaded quad per
culled face may use a generated border sprite instead of four extra strips.
Both SingleVariant and every child of a vanilla WeightedVariants must qualify.
The complete model falls back if any face or random variant is unsafe.

The original sprite and model remain intact. World rendering chooses the baked
model only while outlines are enabled at the default width, 1/32 block.
Items, particles and special blocks continue to reference their original materials.
Changing the width uses the existing geometry path immediately; no forced resource
reload is required. Resource-pack reload regenerates variants from the new images.

Retained geometry cases include:

- Rainbow, pastel, warning and infested outlines and their face symbols.
- Slabs, stairs, carpets, snow layers and other non-full models.
- Tint indices, multilayer faces, cropped/repeated UVs, custom/multipart models and modded blocks.
- Animated, transparent, non-square or unsupported-resolution sprites.
- NeoForge faces with extra colors/normals or disabled AO.
- Missing variants, budget exhaustion and atlas overflow.

## Resource cost and appearance

Sprites are power-of-two, at most 256x256. A 16x16 source is nearest-neighbour
enlarged to 32x32, giving the default width an exact one-pixel border.
Larger supported sprites retain their resolution. The border uses the existing
linear-light mean and dark-color boost, modulated by the white-concrete center
texel used by the old outline geometry. All generated alpha values are 255.

Metadata is preserved. Original images are never modified by border baking.
The separate netherrack-average sprite remains uniform inside; its baked model
uses the original full-face UV layout, not the old center-only UV.

Generated base pixel data is capped at 8 MiB per block atlas, preferring small
sprites. This is not a cap on total GPU memory: mipmaps, atlas padding, allocation
rounding and original images also cost memory. Atlas stitch overflow retries the
original sprites and disables baked variants for that reload.

Baked borders use the native face's lighting/AO and mipmap filtering. They are
not pixel-identical to the old slightly offset geometry, especially at distance.

## Automated verification

Use JDK 25 (the project targets Java 25):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --offline check build
```

Shared tests cover pixel art, physical border width, opaque alpha, originals,
metadata, reload availability, namespace collisions, budget, private Mixin hook
signatures, UVs, cached quad counts, unsafe models and weighted variants.
Fabric additionally boots vanilla registries to check special block/shape eligibility.
NeoForge tests remain headless and do not attempt to create an FML client.

## In-game A/B verification (not yet measured)

Use the same loader, game version, world, camera, render distance and resource pack.
Disable VSync and FPS caps. Warm up and finish initial chunk builds before recording.
Compare:

1. Installed mod, outlines disabled.
2. Installed mod, outlines enabled, JVM argument `-Dfacet.bakedOutlines=false`.
3. Installed mod, outlines enabled, default width, baked optimization enabled.

Changing the JVM flag requires restarting the client. Do not compare different
resource packs or loader versions. Check both a static camera and a repeatable
moving/chunk-loading route. Record CPU/GPU frame time, median FPS and P95/P99 frame
time, section-build time and GPU memory. A reduction in quad count is not a measured
FPS gain.

Fabric's `-Dfacet.outlineStats=true` reports strip counts and baked-block emission
counts during section building. These are diagnostic workload counters, not an FPS
profiler, and they are inactive on NeoForge.

Visual checklist:

- Stone, dirt, concrete, ore, logs and rotated weighted cube variants.
- Netherrack uniform interior and visible border.
- Ancient debris, budding amethyst, infested stone, powder snow and suspicious blocks.
- Grass/foliage tint and stair/slab/carpet/snow-layer geometry fallback.
- Graffiti, hover outlines, hologram boot animation and placement previews.
- Toggle off/on; default width -> another width -> default width.
- Vanilla and high-resolution/transparent/animated resource packs; reload with F3+T.
- Near/far views, mipmap settings, AO and face culling.
- Fabric 26.1/26.2/26.3 and NeoForge 26.1/26.1.2/26.2/26.3.
- Optional renderer/shader combinations require separate in-game validation.

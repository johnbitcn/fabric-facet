# Facet - Minecraft Block Counting and Outline Mod

[English](./README.md) | [简体中文](./README.zh-CN.md) | [日本語](./README.ja-JP.md)

Official project pages: [Modrinth](https://modrinth.com/project/lW1ulmxe) | [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/facet)

Facet is a lightweight client-side Fabric mod for Minecraft block counting, block outlines, distance probing, Manhattan-path measurement, and graffiti marking. It helps builders count and mark blocks for structures, flooring, borders, paths, and large projects without replacing their favorite resource pack textures.

## Purpose and Philosophy

Facet was built for a simple goal: make block counting clearer without forcing players to give up the textures they like. Many Minecraft resource packs add permanent borders directly into block textures. That can help with counting, but it also changes the look of every block all the time. Facet renders the border as a client-side overlay instead, so vanilla textures, custom resource packs, and shader-friendly textures stay untouched.

When you need a counting view, use key bindings to toggle all block outlines, the targeted-block outline, or the distance HUD. When you are done, turn them off and keep playing with your normal visual style. For longer measurements, the Manhattan path indicator marks the X, Z, and Y route from the block under the player to the targeted block, while the HUD reports X, Z, Y, and total Manhattan distance so the block count is easy to verify at a glance.

Graffiti gives builders a quick way to mark blocks during construction, and it can also serve as a trail marker while exploring. Aim at a supported block face and open the graffiti wheel to choose a marker. Graffiti is stored and rendered only on your client, so other players cannot see it. If the marked block is moved or destroyed, its graffiti is removed as well.

## Feature Updates

### v1.1.2 — Graffiti Marking (2026-07-19)

- Added four client-side graffiti markers for construction and exploration
- Added an animated graffiti wheel opened with `G`, with number-key and mouse selection
- Added per-world and per-dimension local storage; graffiti remains invisible to other players
- Added automatic cleanup when a marked block is moved, destroyed, or replaced
- Fixed the brief screen flash when opening or closing the graffiti wheel on Minecraft 26.2

### v1.0.0 — Block Counting Foundation (2026-07-13)

- Added toggleable material-colored block outlines that preserve resource pack textures
- Added a neon outline for targeted blocks, including targets outside the normal interaction range
- Added a distance HUD with X, Z, Y, and total Manhattan-distance counts
- Added a color-coded Manhattan path indicator
- Added client-side settings for outline opacity, width, and targeted-block outline style

## Screenshot

![Facet Minecraft block counting and outline mod screenshot](https://github.com/user-attachments/assets/90140d40-e72a-4002-8a0c-4284ece73deb)

## Key Bindings

- Toggle all block outlines
- Toggle targeted-block outline
- Toggle block distance HUD and Manhattan path display
- Open the graffiti wheel (`G` by default; use `1`–`4` or the mouse to choose a marker)

## License

Facet is licensed under the [MIT License](./LICENSE).

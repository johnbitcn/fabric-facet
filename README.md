# Facet - Minecraft Block Counting and Outline Mod

[English](./README.md) | [简体中文](./README.zh-CN.md) | [日本語](./README.ja-JP.md)

Official project pages: [Modrinth](https://modrinth.com/project/lW1ulmxe) | [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/facet)

Facet is a lightweight client-side Fabric mod for Minecraft block counting, block outlines, distance probing, Manhattan-path measurement, and graffiti marking. It helps builders count and mark blocks for structures, flooring, borders, paths, and large projects without replacing their favorite resource pack textures.

## Purpose and Philosophy

Facet was built for a simple goal: make block counting clearer without forcing players to give up the textures they like. Many Minecraft resource packs add permanent borders directly into block textures. That can help with counting, but it also changes the look of every block all the time. Facet renders the border as a client-side overlay instead, so vanilla textures, custom resource packs, and shader-friendly textures stay untouched.

When you need a counting view, use key bindings to toggle all block outlines, the targeted-block outline, or the distance HUD. When you are done, turn them off and keep playing with your normal visual style. For longer measurements, the Manhattan path indicator marks the X, Z, and Y route from the block under the player to the targeted block, while the HUD reports X, Z, Y, and total Manhattan distance so the block count is easy to verify at a glance.

Graffiti gives builders a quick way to mark blocks during construction, and it can also serve as a trail marker while exploring. Aim at a supported block face and open the graffiti wheel to choose a marker. Graffiti is stored and rendered only on your client, so other players cannot see it. If the marked block is moved or destroyed, its graffiti is removed as well.

## Features

- Toggleable block material outlines that preserve your own Minecraft resource pack textures
- Neon outline for the targeted block, including targets outside the normal interaction range
- Distance HUD with X, Z, Y, and total Manhattan-distance counts
- Manhattan path indicator with clear colored path segments
- Client-side graffiti markers for construction and exploration; other players cannot see them
- Client-side settings for outline opacity, outline width, and targeted-block outline style

## Screenshot

![Facet Minecraft block counting and outline mod screenshot](https://github.com/user-attachments/assets/e8ee53a9-b4e5-4eed-8d15-d981b1e03889)

## Key Bindings

- Toggle all block outlines
- Toggle targeted-block outline
- Toggle block distance HUD and Manhattan path display
- Open the graffiti wheel (`G` by default; use `1`–`4` or the mouse to choose a marker)

## License

Facet is licensed under the [MIT License](./LICENSE).

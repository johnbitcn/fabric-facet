# Facet - Minecraft Block Counting and Outline Utility Mod

[English](./README.md) | [简体中文](./README.zh-CN.md) | [日本語](./README.ja-JP.md)

Official project pages: [Modrinth](https://modrinth.com/project/lW1ulmxe) | [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/facet)

This lightweight mod was developed to make learning architecture and redstone structures feel more intuitive for my children.
It lets you preview and adjust the facing of functional blocks in Survival Mode.
Yes! Setting the direction before building it feels perfectly natural, doesn't it?
Every feature has a key binding.
Oh, and I added the sound effects my children love. They're seriously cool!
Now I'm sharing it. I hope you enjoy it!

## Latest Update
Reworked the overall outline logic and all the colors. It doesn't look so harsh now!

## Complete Feature List

- Holographic placement preview with startup sound
- Adjust the facing of functional blocks (singleplayer only)
- Targeted block counting with Manhattan path display
- Targeted-block outline and targeted-face outline
- Four graffiti markers (visible only to you)
- Block outlines

## Rotatable Holographic Placement Preview in Survival Mode

![Facet real-time holographic placement preview](https://github.com/user-attachments/assets/00d00559-bd56-49c9-a846-14532b872402)

## Screenshots

![Facet Minecraft block counting and outline utility mod screenshot](https://github.com/user-attachments/assets/90140d40-e72a-4002-8a0c-4284ece73deb)

## Supported Loaders

- Fabric
- NeoForge

## Feature Updates

### v1.3.5 - Reworked the overall outline logic and colors. It doesn't look so harsh now!

- Reworked how block colors are picked
- Redesigned the colors for every block

### v1.3.1 - Functional Block Facing Rotation (Singleplayer Only)

- Rotate the facing of functional blocks in Survival Mode in singleplayer
- Added a key binding to toggle the holographic placement preview
- Added a startup animation and sound for the holographic placement preview

### v1.2.11 — NeoForge Support and Settings Access

- Added full NeoForge builds for Minecraft 26.1, 26.1.2, and 26.2
- Made block outlines, hover outlines, distance HUD and path tools, client-side graffiti, and holographic placement preview available on NeoForge
- Added an Open Facet Settings key binding (unbound by default) and a native NeoForge mod-list settings entry

### v1.2.9 — Major Rendering Logic Refactor

- Reworked outline rendering for better performance and more natural visuals
- Updated how targeted-block outlines are expressed
- Fixed holographic placement preview rendering
- Current Loader and game-version support is listed above

### v1.2.1 — Holographic Placement Preview (2026-07-21)

- Added a real-time holographic placement preview to help players place blocks more accurately

### v1.1.2 — Client-Side Graffiti (2026-07-19)

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

## Key Bindings

- Toggle all block outlines
- Toggle targeted-block outlines
- Toggle the block-distance HUD and Manhattan path display
- Open Facet settings (unbound by default; also available from the NeoForge mod list)
- Open the graffiti wheel (`G` by default; use `1`–`4` or the mouse to choose a marker)

## License

Facet is licensed under the [MIT License](./LICENSE).

# Compact Machines Preview Fix

Standalone NeoForge 1.21.1 client-side mod that patches the severe memory
leak introduced by Compact Machines' room preview (issues
[CompactMods/CompactMachines#664](https://github.com/CompactMods/CompactMachines/issues/664),
[FTBTeam/FTB-Modpack-Issues#11675](https://github.com/FTBTeam/FTB-Modpack-Issues/issues/11675)).

Repo: <https://github.com/breakinblocks/CompactMachinesPreviewFix>

## What it does

Compact Machines uses the [Gander](https://github.com/CompactMods/gander)
library to render a 3D preview of the inside of a room. Each preview open
allocates, per chunk section:

* 2 × `SectionBufferBuilderPack` — pre-sized off-heap `ByteBufferBuilder`s
  (~10–20 MB each)
* 1 × `VertexBuffer` per `RenderType` — OpenGL buffer handle + native staging

Neither `BakedLevel`, `BakedLevelSection`, nor `SpatialRenderer` exposes a
`close()` / `dispose()` method, and the consuming code in
`MachineRoomScreen` does not call one. A 45 cubed room (≈27 sections) leaks
roughly 270–540 MB of off-heap committed memory **per preview open**.
JVM heap stays small (everything is off-heap / GL), which is why heap
profilers look fine while `htop` / Windows committed memory balloons until
the game OOM-crashes.

This mod injects into `MachineRoomScreen` via Mixin to:

1. Dispose the prior `BakedLevel` (close each `VertexBuffer` and each
   `SectionBufferBuilderPack`) **before** `updateScene` replaces the
   renderer.
2. Dispose the current `BakedLevel` **before** `onClose` runs.

All `close()` calls are routed onto the render thread via
`RenderSystem.recordRenderCall`.

## Compatibility

* Minecraft 1.21.1
* NeoForge 21.1.55+
* Compact Machines (any 21.1.x build that still uses Gander's `BakedLevel`
  record)

## License

MIT.

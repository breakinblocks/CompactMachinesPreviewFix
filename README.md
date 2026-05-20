# Compact Machines Preview Fix

Standalone NeoForge 1.21.1 client-side mod that patches the severe memory
leak introduced by Compact Machines' room preview (issues
[CompactMods/CompactMachines#664](https://github.com/CompactMods/CompactMachines/issues/664),
[FTBTeam/FTB-Modpack-Issues#11675](https://github.com/FTBTeam/FTB-Modpack-Issues/issues/11675)).

Repo: <https://github.com/breakinblocks/CompactMachinesPreviewFix>

## What it does

Compact Machines uses the [Gander](https://github.com/CompactMods/gander)
library to render a 3D preview of the inside of a room. Each preview open
leaks in two places:

**Per-chunk-section geometry** (off-heap RAM)

* 2 × `SectionBufferBuilderPack` — pre-sized `ByteBufferBuilder`s
  (~10–20 MB each)
* 1 × `VertexBuffer` per `RenderType` — OpenGL buffer handle + native
  staging

**Per-preview screen pipeline** (VRAM)

`SpatialRenderer.state` caches a `PipelineState` containing:

* `RENDER_TARGET` — a full-window `TextureTarget` (color + depth)
* `TRANSLUCENCY_CHAIN` — owns a layered texture-array `TranslucentRenderTarget`
  **and** a separate `intermediaryCopyTarget` FBO that `close()` does not
  destroy
* `RENDER_TYPE_STORE` — cached `RenderType` remap maps

Neither `BakedLevel`/`BakedLevelSection` nor the `SpatialRenderer.state`
graph is disposed by Gander, and `MachineRoomScreen` drops the renderer
without notice. A 45-cubed room (~27 sections) at 2560×1440 leaks several
hundred MB of off-heap RAM **plus** tens of MB of VRAM per preview open.
JVM heap stays small (everything is off-heap / GL), which is why heap
profilers look fine while committed RAM balloons until the JVM OOM-crashes
— and once VRAM fills the next render aborts with
`GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT` from `TranslucentRenderTarget`.

This mod injects into `MachineRoomScreen` via Mixin to:

1. Walk the prior `SpatialRenderer` (its `BakedLevel` geometry **and** its
   cached `PipelineState`) and release every closeable GPU/native handle
   **before** `updateScene` replaces the renderer.
2. Do the same cleanup **before** `onClose` runs.

All `close()` / `destroyBuffers()` calls are routed onto the render thread
via `RenderSystem.recordRenderCall`.

## Compatibility

* Minecraft 1.21.1
* NeoForge 21.1.55+
* Compact Machines (any 21.1.x build that still uses Gander's `BakedLevel`
  record)

## License

MIT.

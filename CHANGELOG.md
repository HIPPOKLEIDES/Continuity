# Changelog

## 3.0.1+26.1-expanded.1

First release of Continuity Expanded, based on Continuity 3.0.1 for Minecraft 26.1.

- Overlays fit slabs, layers, stairs, vertical slabs and other axis-aligned shapes, flush and clipped to each face.
- Inset faces and inside corners above partial blocks receive overlays.
- Full-height layers and other shaped blocks can apply overlays.
- Block tags with optional state filters in `matchBlocks` and `connectBlocks`, with warnings for unknown or empty tags.
- `connect_to_tag` and `connect_to_states` connection rules.
- Overlays default to `prioritize=true`.

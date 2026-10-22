# Continuity Expanded

Continuity Expanded is a fork of [Continuity](https://github.com/PepperCode1/Continuity) by PepperCode1, modified by HIPPOKLEIDES starting in July 2026, with contributions from WesterosContinuity as well. It replaces Continuity rather than adding to it: it keeps the `continuity` mod ID, so the two cannot be installed together. It is not affiliated with or supported by the original project, so please report problems [here](https://github.com/HIPPOKLEIDES/Continuity/issues) rather than to Continuity.

Like Continuity, it is licensed under the [GNU Lesser General Public License v3.0](LICENSE). The full list of changes is in the [changelog](CHANGELOG.md).

## Changes from Continuity

### Overlays on shaped blocks

- Overlays copy the geometry of the face they decorate, so they sit flush on inset faces such as slab and layer tops instead of floating at full block height.
- Faces that only cover part of a block side, such as slab sides, stair steps and vertical slabs, receive the matching part of the overlay, aligned to the block grid.
- Inset faces receive overlays from a neighbour even when something sits on top of it, since the neighbour's side shows above the face.
- An inside corner above a partial block takes the overlay from the block beside the partial block.
- Blocks that look full but have a smaller collision shape, such as full-height layers, apply overlays like full blocks. Other shaped blocks apply overlays onto faces whose edge they fully border, either level with the face or rising from it.

### Block tags

`matchBlocks` and `connectBlocks` accept block tags, optionally followed by block state filters like block entries:

```properties
matchBlocks=#minecraft:sand
matchBlocks=#minecraft:slabs:type=bottom,double
connectBlocks=stone #mymod:stones
```

Tags only exist once a world is loaded, so they are checked then. Unknown or empty tags, and filters that match no block in the tag, are logged as warnings.

### Connection rules

- `connect_to_tag=<tag>` connects blocks that are both in the given block tag.
- `connect_to_states=<property>,...` requires the named block state properties to have the same value. On its own it combines with `connect=block`.

### Overlay ordering

Overlays default to `prioritize=true`. Overlays only add quads, so they now run before texture replacements such as `method=repeat` from the same or lower priority packs, whether they match by tile, block or tag. An explicit `prioritize=false` is still respected.

## Original Continuity README

### Continuity

Continuity is a Minecraft mod that allows resource packs that use the OptiFine connected textures format or OptiFine emissive textures format (only for blocks and item models) to work without OptiFine.

Continuity is client-side only and includes two built-in resource packs. The Default Connected Textures pack provides connected textures for glass, sandstone, and bookshelves, similar to the built-in connected textures provided by OptiFine. The Glass Pane Culling Fix pack culls faces between vertically stacked glass panes to make them look seamless with connected textures.

Formally, Continuity implements the Continuity connected textures specification, Continuity emissive textures specification, and Continuity custom block layers specification. All of these are extensions of the corresponding OptiFine specification and were created to provide more features to resource pack authors. The documentation for the Continuity specifications can be found at the [Continuity wiki](https://github.com/PepperCode1/Continuity/wiki).

Continuity is developed as a Fabric mod and is recommended to be used with Fabric. However, [Connector](https://github.com/Sinytra/Connector) and [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI) allow Continuity to work well on other mod loaders such as NeoForge and Forge. Releases are made on CurseForge and Modrinth that are marked as working with these mod loaders; these releases contain the same code as equivalent releases for Fabric, but with additional metadata to declare Connector and Forgified Fabric API as dependencies. An official NeoForge version of Continuity that does not require Forgified Fabric API is not planned at this time due to major technical differences between the Fabric and NeoForge APIs.

### Links

[CurseForge Page](https://www.curseforge.com/minecraft/mc-mods/continuity) \
[Modrinth Page](https://modrinth.com/mod/continuity) \
[Wiki](https://github.com/PepperCode1/Continuity/wiki) \
[Discord](https://discord.gg/7rnTYXu)

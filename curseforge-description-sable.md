# Make it Compatible: Sable

**Short summary (for the CurseForge "Summary" field):**
> The Sable half of Make it Compatible — drop-in compatibility patches for Sable. LittleTiles × Sable (build on moving vehicles), Sable × Iris (no more black vehicles under shaders) and Immersive Portals × Sable (vehicles and portals coexist — visible through portals, no collision conflict, no save hang). Each patch turns on only when the mods it bridges are installed.

---

## Sable, made to get along.

**Make it Compatible: Sable** is a home for the small, fiddly compatibility patches that make
[Sable](https://www.curseforge.com/minecraft/mc-mods/sable) coexist with other mods. Install it next to whatever you
already run — each patch **activates only when the mods it bridges are both present**, and stays dormant (and
harmless) otherwise. The launch log tells you exactly which patches went active.

## 🧩 Patch: LittleTiles × Sable

[LittleTiles](https://www.curseforge.com/minecraft/mc-mods/littletiles) and Sable are both fantastic mods that
absolutely refuse to get along. Both deeply rewrite how Minecraft renders and collides blocks, and Sable flat-out
won't load with LittleTiles installed.

**This patch is the bridge.** Put your sub-block builds on a Sable sub-level and they finally render, light,
collide, and let you build on them while the vehicle moves and turns.

On a Sable vehicle, LittleTiles now:

- 🧱 **Renders** — tiles show up on moving and rotating vehicles, on both the vanilla and **Sodium** render paths.
- 💡 **Lights correctly** — no more over-dark, double-shaded faces; tiles match the vehicle like vanilla blocks.
- 🔨 **Is fully interactive** — break, place, and use individual tiles exactly like you do in the open world.
- 🚶 **Doesn't crash** — walking on tiles riding a vehicle is safe (fixes a hard crash).
- 🟦 **Previews accurately** — the placement ghost sits precisely on the vehicle, even at Sable's far-out plot coordinates.
- ✂️ **Handles selections sanely** — an area/hammer drag started on a vehicle and finished out in the world no longer tries to fill half the map (or freeze the game).
- 🧊 **Works as a single-block sub-level** — a LittleTiles build that *is* its own one-block Sable sub-level renders too.

### ✅ Zero-config — the jar lifts the incompatibility itself

Sable hard-declares LittleTiles as **`incompatible`**, and NeoForge enforces that in its loader *before any mod
loads* — a plain mod jar could never fix it. So this jar isn't a plain mod jar: it loads as an **early loader
service** that writes NeoForge's first-party escape hatch (`[dependencyOverrides] sable = ["-littletiles"]`) into
`config/fml.toml` before it's read, and then injects the actual mod (nested inside the same jar) back into mod
loading. **Drop the one jar in `mods/` next to the stock, unmodified Sable jar and it just works** — no config
edit, no companion jar, effective from the very first launch.

*(Modpack authors: nothing to ship besides the jar — or ask the Sable developer to drop the incompatibility now
that this bridge exists.)*

### 🐾 Known limitations (LittleTiles × Sable)

- The **hover outline** on vehicle tiles isn't drawn — re-projecting it onto the moving plot was too unreliable.
  Tiles in the open world keep their normal outline.
- LittleTiles' **own animations** (a door *while it's opening*, etc.) play in world space when on a vehicle; the
  static/closed state renders fine. This is an upstream float-precision limit at Sable's far-out plot coordinates,
  not something an add-on can cleanly re-project.

## 🧩 Patch: Sable × Iris

Under an Iris shaderpack, editing a Sable sub-level (vehicle) could make its blocks render **solid black** — the
just-edited section bakes in the wrong vertex format on the render thread. This patch builds the edited section on
the async worker thread (the same path every other section uses), so it always bakes the correct format and the
vehicle's blocks stay lit. Toggle it under *Mods → Make it Compatible: Sable → Config*.

## 🧩 Patch: Immersive Portals × Sable

[Immersive Portals](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-for-forge) and Sable both
rewrite the same core systems (collision, entity tracking, chunk loading, the renderer) — and stepped on each
other everywhere. With this patch active:

- 🚪 **No more collision conflict** — both mods redirect the exact same collision call; one used to silently lose
  (portal collision or vehicle collision gone). Now portal collision composes *around* Sable's vehicle collision.
- 🛰️ **Entities on vehicles stay synced** — Immersive Portals replaces vanilla entity tracking with a version that
  doesn't understand Sable's far-out plot coordinates; mobs and items riding a vehicle would freeze for other
  players. Their sync now uses the position you actually see them at.
- 🔭 **Vehicles are visible through portals** — sub-levels stay loaded and networked for players watching them
  through a portal, including from another dimension, and pop back out of view cleanly.
- 🧱 **Vehicle edits keep rendering** — editing a vehicle while Immersive Portals owns the renderer no longer
  leaves the mesh stale until relog, and sub-level block entities (chests etc.) render correctly inside portal
  views.
- 💾 **No more "stuck on saving"** — Immersive Portals' chunk tickets no longer fight Sable over plot chunks (the
  rare exit-hang with two portals + a vehicle in one chunk).
- 🤫 **No fake teleport spam** — Immersive Portals' teleport-debug no longer screams about Sable's routine
  world↔plot coordinate hops.

Based on [ImmersivePortalSableBridge](https://github.com/rebbyIf/ImmersivePortalSableBridge) by **rebbyIf** &
**Bunting_chj** (thanks!), ported to Sable 2.0 and extended. Known limit (inherited from upstream): vehicles can be
*seen* through portals but can't *drive through* them.

## 📦 Requirements

- Minecraft **1.21.1** · **NeoForge 21.1.x** (NeoForge only)
- *LittleTiles × Sable*: **LittleTiles** + **CreativeCore** + **Sable** (stock jar — the loader handles the
  incompatibility), optionally **Sodium** (works with or without it).
- *Sable × Iris*: **Sable** + **Iris**.
- *Immersive Portals × Sable*: **Sable** + **Immersive Portals** (the NeoForge build, 6.0.7+).

The mod itself only requires NeoForge + Minecraft — every bridged mod is optional.

## 🧩 Companion mods

The **Voxy** compatibility patches — including the Sable × Voxy render-distance compat that keeps sub-levels visible
over Voxy's LOD terrain — live in **Make it Compatible: Voxy**.

## 🙏 Credits

An **independent, unofficial** compatibility hub — not affiliated with or endorsed by any of the mods it patches.
All credit for the bridged mods goes to **CreativeMD & N247S** (LittleTiles / CreativeCore), **RyanHCode**
(Sable) and **qouteall & the Immersive Portals for Forge team** (Immersive Portals). The Immersive Portals × Sable
patch is based on **ImmersivePortalSableBridge** by **rebbyIf** & **Bunting_chj**. The mod logo is based on
Sable's artwork.

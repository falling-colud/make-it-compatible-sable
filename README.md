# Make it Compatible: Sable

**Sable, made to get along.** *(NeoForge 1.21.1)*

A home for the small, fiddly patches that make Sable coexist with other mods. Build LittleTiles
structures on moving vehicles, fix black/unlit vehicles under Iris shaderpacks, and let Sable and
Immersive Portals share a world — visible through portals, no collision conflict, no save hang.
Each patch turns on only when the mods it bridges are both installed.

## Building

Requires JDK 21 (Gradle auto-provisions the toolchain).

```bash
./gradlew sableDistJar     # builds the jar -> build/libs/make-it-compatible-sable-<version>.jar
./gradlew runClient    # launches a dev client (put bridged mods in run/mods/)
```

The mods this bridges are compile-only and **not** bundled — they're provided at runtime by your modpack.
To build, drop the matching jars into `libs/` (see [libs/README.md](libs/README.md)); they are gitignored
and never redistributed here.

## Compatibility model

Every patch **self-gates**: it activates only when the mods it bridges are installed, and stays dormant
(and harmless) otherwise. Safe to keep loaded with any subset of the target mods.

## License

[MIT](LICENSE) © leon.raineri

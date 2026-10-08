# PseudoHat — agent notes

Fabric **client** mod (MC 26.2, Yarn mappings, Java 25 toolchain, Fabric Loom). Draws a cosmetic
on a humanoid's head when the head slot is empty. Two render types, chosen per cosmetic in a resource
pack: a vanilla **item model**, or an animated **GeckoLib** geometry model. GeckoLib is a soft
dependency (`compileOnly` + `localRuntime`, checked at runtime by name through `GeckoLibBridge`).

## Commands

```sh
./gradlew build                                  # compile + tests + jar
./gradlew test                                   # 28 tests
./gradlew runClient --args="--quickPlaySingleplayer TEST"   # dev client, username PseudohatDev
```

`runClient` sets `-Dpseudohat.debug=true` (layer trace in the log) and `--username PseudohatDev`; add
`--args="--quickPlaySingleplayer TEST"` to land in the sample world `run/saves/TEST`.

### Verifying a cosmetic actually drew

The sample textures are flat known colours, so a screenshot is checked by pixel count:

```sh
hyprctl dispatch focuswindow address:<mc>          # get it from `hyprctl clients -j`
hyprctl dispatch sendkeys F5                        # third person (twice = front view)
grim run/shots/shot.png
python3 tools/check-shot.py run/shots/shot.png
```

`check-shot.py` tolerates entity shading: it accepts any scalar multiple of a target colour
(≥0.35×), because a correctly lit face comes out as a fraction of the flat texture (the sample brim
renders at ~0.4×). Tooling: `grim` (Wayland), `hyprctl` for focus/keys (`ydotool`/`xdotool` absent).

## GeckoLib integration facts (verified against the 5.5.5 bytecode)

Jar: `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/geckolib/5.5.5/*/geckolib-5.5.5.jar`.
Inspect with `javap -p -c -cp <jar> <class>`; keep a temp extraction outside the repo (`/tmp` gets
cleaned mid-session).

- **Model space is blocks, not pixels.** `GeometryCube.bake` divides origins/sizes by 16
  (`JsonUtil.modelToWorldUnits` = `/16`); `GeoBone.translateToPivotPoint` divides pivots by 16;
  `BoneSnapshot.translate` divides animation positions by 16 (and negates x). The renderer adds no
  scale of its own (`GeoObjectRenderer.scaleWidth/Height = 1`, `scaleModelForRender` → identity).
- **`GEO_SCALE = 1.6F` in `PseudoHatLayer`.** Vanilla's head frame applies 0.625 to a head-width
  model, and the model is already in blocks: `8 px → 0.5 * 1.6 * 0.625 = 0.5` blocks = vanilla head.
  A value of 0.1 (raw-pixel assumption) renders 16× too small.
- **`adjustRenderPose` is overridden to a no-op.** GeckoLib's default translates `(0.5, 0.51, 0.5)`
  (object-in-a-block centring); the pose handed to the renderer is already the head frame.
- **`offset` in settings is geometry pixels**; `PseudoHatLayer.applyAuthoringTransforms` divides by 16
  (same unit the model is baked in). `rotation` is degrees about the head; `scale` a multiplier.
- **Glow.** `AutoGlowingGeoLayer` looks for `<texture>_glowmask.png` (`RenderUtil.getEmissiveResource`
  replaces `.png`). A settings `glow.texture` uses `TextureLayerGeoLayer` with `RenderTypes::eyes`.
  Since 2026-10-07: `glow: true` with **no** glowmask file in the resource manager falls back to
  overlaying the base texture at full bright (whole model glows) instead of requiring a duplicated
  mask. The presence check is `HatGeoRenderer.hasAutoGlowMask` (a `ResourceManager.getResource` probe
  at renderer construction).
- **Animation names are exact keys** in the `animations` map (`BakedAnimationCache`); a miss logs an
  error every frame, so they are validated up front by `SamplePackTest`, not left to runtime.
- **Resource ids.** `assets/<ns>/geckolib/{models,animations}/<name>.{geo,animation}.json` strip to
  `<ns>:<name>`; texture defaults to `<ns>:textures/entity/<name>.png` (`CosmeticSettings.textureBeside`).

## Coordinates

Author in Blockbench against **origin = head centre**: +y up, +x/+z the head frame axes.
A vanilla head is 8 geo px (0.5 blocks). Wrap the head with cubes around `y = 4` (head top) upward.

## Sample pack (dev fixture)

Live copy the client loads: `run/resourcepacks/pseudohat-sample/assets/pseudohat/`

```
config/hats.json              nickname -> cosmetic id (PseudohatDev -> pseudohat:hat, AuroraBreeze -> pseudohat:huina,
                                          GGSHNIKK -> pseudohat:poppy)
config/models/hat.json        geckolib settings: glow, default idle, onSprint loop, onHurt once
config/models/huina.json      geckolib settings for the AuroraBreeze huina: default animation.huina.loop (spins bone "что-то")
geckolib/models/hat.geo.json  bone "hat": brim 14x2x14 at y4, crown 8x4x8 at y6
geckolib/models/huina.geo.json  bone "что-то" (pivot origin): 4 bars from Huina.bbmodel, head-centred, offset [0,15.5,0]
geckolib/animations/hat.animation.json   animation.hat.{idle,run,hurt}
geckolib/animations/huina.animation.json animation.huina.loop: full -360 y spin of bone "что-то" over 4.25s + 2px bob
textures/entity/hat.png + hat_glowmask.png  flat red brim / blue crown accent (check-shot colours)
textures/block/huina.png         yellow 32x32 flower texture, copied from the AuroraBreeze pack
```

The huina geometry/animation came from the user's own files: `~/GsckolibModels/Huina.bbmodel` +
`~/GsckolibModels/huina.animation.json` (bone is literally named `что-то`), and the flat yellow
texture from the AuroraBreeze resource pack at
`~/.local/share/FreesmLauncher/instances/BedWarsV11/minecraft/resourcepacks/AuroraBreeze`.
The pack there still ships huina as a vanilla item model — the geckolib huina lives in the dev pack
only until we mirror it into AuroraBreeze.

A separate, older `sample-pack/` at the project root is *not* what the client loads — leave it alone.

## Tests

`./gradlew test` — 30: `CosmeticSettingsTest` (19, the settings-file contract),
`HatConfigMappingTest` (6), `SamplePackTest` (4, cross-checks the sample pack: every animation a
config references exists, every animation's bone exists in the geometry, the default texture/glowmask
files exist — for both `hat` and `huina`). `SamplePackTest` reads `run/` and is skipped when the
fixture is absent.

## Not yet decided

`GeckoLibBridge` currently disables the whole geckolib path after any `Throwable`; the intent was a
warn-once-per-cosmetic fallback instead. Animations and event triggers are not unit-testable without a
running client — `SamplePackTest` covers the file-level contract, the rest is verified in-game.

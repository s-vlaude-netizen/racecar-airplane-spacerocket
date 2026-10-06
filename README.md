# Road to Orbit 🚗 ✈️ 🚀

A 3D Android game where **one vehicle transforms three times**: you start as a race car on a mountain
highway, morph into an airplane to climb above the clouds, then into a space rocket to reach the Moon –
where the rocket lands and turns back into a car for a victory drive.

Everything you see and hear is generated in code: all 3D models, the sky, the terrain, every sound
effect. There are no image, model or audio files in the app, and no dependencies beyond the Android SDK.

## Install

Grab the APK from [`dist/road-to-orbit.apk`](dist/road-to-orbit.apk) (or from the *Actions → Build* run
artifacts) and install it on an Android 8.0+ phone (sideload: allow "install unknown apps" for your
browser/file manager). It needs OpenGL ES 3.0, which every phone from the last decade has.

## How to play

| Stage | You are | Goal |
|---|---|---|
| 1 · **Grand Prix** | a race car | Weave through traffic, barriers and cones, grab coins and nitro. |
| 2 · **Sky Rally** | an airplane | Fly through glowing rings, dodge balloons, rock spires, storms and jets; climb to the edge of space. |
| 3 · **Orbit Run** | a rocket | Thread asteroid fields, rings and meteor walls, collect crystals, reach the Moon. |

* **Steer** – drag anywhere on the left half of the screen (a floating stick). Left/right in the car, left/right + up/down in the air and in space. Optional **tilt steering** on the menu.
* **Boost** – hold the BOOST button. Nitro, orbs and crystals refill the meter.
* **Transform** – near the end of the first two stages a glowing **transform zone** opens (guide arches / portal rings). Tap **TRANSFORM** while inside it. Pressing within the last 70 m of the gate is a **PERFECT LAUNCH** (+1000). If you forget, the vehicle transforms automatically at the gate, so you can never get stuck.
* You have **3 shields**. A hit costs one and gives you a moment of invulnerability; wrenches repair. Near misses and ring streaks score bonus points. Lose all shields and you can retry the current stage.
* Keyboard / gamepad also work: arrows or WASD, Shift/Space boost, T transform, P pause, Enter start.

A full run takes about three minutes.

## Building

```bash
./gradlew :core:test                 # game logic, bot playthroughs, renderer, audio synth tests
./gradlew :app:assembleRelease       # → app/build/outputs/apk/release/app-release.apk
./gradlew :app:installDebug          # install on a connected device
```

Requirements: JDK 17+ and the Android SDK (platform 35). Open the folder in Android Studio and press ▶ to
run on a device or emulator. Builds are signed with the committed, intentionally public key in
`keystore/` so every build can be installed over the previous one (this is not a Play Store key).

## How it is built

```
core/   pure Kotlin/JVM – no Android dependencies
  game/   deterministic simulation: legs, spawner patterns, collisions, scoring, transformations, finale
  gfx/    OpenGL ES 3.0 renderer behind a tiny `Gles` interface, procedural meshes, shaders, camera
  audio/  procedural sound synthesiser (22 kHz PCM)
app/    thin Android shell: GLSurfaceView, Canvas HUD/menus, touch/tilt/keyboard input, SoundPool audio
tools/render-check/   headless renderer check (see below)
```

Some of the techniques:

* **The transformation** – the fuselage is one *lofted* mesh whose cross-section (a superellipse) blends
  between a boxy car body, a slim fuselage and a round rocket tube, while about 20 parts (wheels → booster
  pods, folded wings → fins, spoiler → tailplane …) glide between three key poses with staggered easing.
* **Curved world** – the world is dead straight, but every vertex is pushed sideways/down in proportion
  to the square of its view distance, so roads wind, hills roll and, at altitude, the horizon curves away.
* **Procedural terrain** – the vertex shader builds the whole ground grid from `gl_VertexID` and noise
  anchored to world coordinates (no vertex buffer, no swimming). The same shader blends grass → cloud deck → moon dust.
* **Procedural sky** – gradient, sun glow, two star layers and a warped-noise nebula, all computed per pixel
  from the view direction; its palette slides from daylight to space with altitude.
* **Sound** – effects and engine loops are synthesised (additive tones, sweeps, filtered noise) and cached as
  WAVs on first launch; the engine pitch follows your speed.

### Testing without a device

* `core` has unit tests plus a **bot** that plays all three stages headlessly on several seeds to prove the
  game is winnable and never produces NaNs.
* `tools/render-check` records the *real renderer's* GL command stream to a trace and replays it in headless
  Chromium (WebGL2), which both compiles the GLSL ES 3.00 shaders in a real implementation and produces
  PNG screenshots of every stage:

  ```bash
  ./gradlew :core:test --tests '*JourneyTraceTest*'
  node tools/render-check/render.mjs core/build/traces/journey.bin tools/render-check/out/journey all
  ```

  (needs Node + Playwright with Chromium)

## Status

Developed and verified in a headless cloud environment: the code compiles, all tests pass, the lint is clean
and the rendered frames were inspected, but it has **not been run on a physical device**. Tilt steering in
particular is untested on hardware.

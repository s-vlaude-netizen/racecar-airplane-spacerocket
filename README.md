# Road to Orbit 🚗 ✈️ 🚀

A 3D Android game where **one vehicle transforms three times**: you start as a race car on a mountain
highway, morph into an airplane to climb above the clouds, then into a space rocket to reach the Moon –
where the rocket lands and turns back into a car for a victory drive.

Everything you see and hear is generated in code: all 3D models, the sky, the terrain, every sound
effect and the music. There are no image, model or audio files in the app, and the only runtime dependency
is the Kotlin standard library (the whole APK is about 130 KB).

<table>
  <tr>
    <td><img src="docs/screenshots/1-menu.jpg" alt="Title screen"><br><sub><b>Title screen</b> – the vehicle cycles through its three forms on the turntable</sub></td>
    <td><img src="docs/screenshots/2-grand-prix.jpg" alt="Stage 1: Grand Prix"><br><sub><b>1 · Grand Prix</b> – race to the glowing transform zone, then tap TRANSFORM</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/3-first-transform.jpg" alt="Car turning into an airplane"><br><sub><b>Transformation #1</b> – car → airplane, launched off the gate (a PERFECT LAUNCH pays +1000)</sub></td>
    <td><img src="docs/screenshots/4-sky-rally.jpg" alt="Stage 2: Sky Rally"><br><sub><b>2 · Sky Rally</b> – rings, balloons and storms above the cloud deck</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/5-second-transform.jpg" alt="Airplane turning into a rocket"><br><sub><b>Transformation #2</b> – airplane → rocket at the edge of space</sub></td>
    <td><img src="docs/screenshots/6-orbit-run.jpg" alt="Stage 3: Orbit Run"><br><sub><b>3 · Orbit Run</b> – asteroids, crystals and planets on the way to the Moon</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/7-moon-landing.jpg" alt="Rocket landing on the Moon"><br><sub><b>Moon landing</b> – the rocket touches down…</sub></td>
    <td><img src="docs/screenshots/8-mission-complete.jpg" alt="Mission complete"><br><sub><b>Mission complete</b> – …and turns back into a car for the victory drive</sub></td>
  </tr>
</table>

<sub>These are headless renders of the real renderer and the real HUD code (see
[Testing without a device](#testing-without-a-device)), not captures from a phone.</sub>

## Install

Grab the APK from [`dist/road-to-orbit.apk`](dist/road-to-orbit.apk) (or from the *Actions → Build* run
artifacts) and install it on an Android 8.0+ phone (sideload: allow "install unknown apps" for your
browser/file manager). It needs OpenGL ES 3.0, which every phone from the last decade has. The game runs
in landscape.

## How to play

| Stage | You are | Goal |
|---|---|---|
| 1 · **Grand Prix** | a race car | Weave through traffic, barriers and cones, grab coins and nitro. |
| 2 · **Sky Rally** | an airplane | Fly through glowing rings, dodge balloons, rock spires, storms and jets; climb to the edge of space. |
| 3 · **Orbit Run** | a rocket | Thread asteroid fields, rings and meteor walls, collect crystals, reach the Moon. |

* **Steer** – drag anywhere on the left half of the screen (a floating stick). Left/right in the car, left/right + up/down in the air and in space. Optional **tilt steering** on the menu: your attitude when the run starts is neutral; tilt the right edge down to steer right, the top edge toward you to climb. A finger on the stick always wins.
* **Boost** – hold the BOOST button. Nitro, orbs and crystals refill the meter.
* **Transform** – near the end of the first two stages a glowing **transform zone** opens (guide arches / portal rings). Tap **TRANSFORM** while inside it. Pressing within the last 70 m of the gate is a **PERFECT LAUNCH** (+1000). If you forget, the vehicle transforms automatically at the gate, so you can never get stuck.
* **Shields** – you have 3 per stage. A hit costs one and gives you a moment of invulnerability; wrenches repair. Lose all of them and you can retry the stage from its start.
* **Score** – distance, coins, rings (streaks pay extra), near misses and transformations, plus a hull bonus and a time bonus when you reach the Moon. 1–3 stars; the best score is saved per difficulty.
* **Difficulty** – cycle EASY / NORMAL / HARD on the menu. Easy: 4 shields, wider gaps, a bit slower, ×0.8 points. Hard: 2 shields, tighter gaps, 10 % faster, ×1.4 points.
* **Sound and music** can be switched off separately on the menu. There are five music loops: menu, race, flight, space and the finale.
* **Keyboard / gamepad** also work: arrows or WASD (D-pad) steer, Shift / Space (R1, B) boost, T (X) transforms, Enter (A) starts, resumes or retries, P / Esc (Start) pauses.

A full run takes about three minutes.

## Building

```bash
./gradlew :core:test :app:testDebugUnitTest   # game logic, bot playthroughs, renderer, audio synth, HUD tests
./gradlew :app:assembleRelease                # → app/build/outputs/apk/release/app-release.apk
./gradlew :app:installDebug                   # install on a connected device
```

Requirements: JDK 17+ and the Android SDK (platform 35). Open the folder in Android Studio and press ▶ to
run on a device or emulator. Builds are signed with the committed, intentionally public key in
`keystore/` so every build can be installed over the previous one (this is not a Play Store key).
The GitHub Actions workflow (`.github/workflows/android.yml`) runs the tests and uploads the APK on every push.

## How it is built

```
core/   pure Kotlin/JVM – no Android dependencies
  game/   deterministic simulation: legs, spawner patterns, collisions, scoring, transformations, finale
  gfx/    OpenGL ES 3.0 renderer behind a tiny `Gles` interface, procedural meshes, shaders, camera
  audio/  procedural sound-effect and music synthesiser (22 kHz PCM)
app/    thin Android shell: GLSurfaceView, Canvas HUD/menus, touch/tilt/keyboard input, audio playback
tools/render-check/   headless screenshots of the renderer and the HUD (see below)
```

Some of the techniques:

* **The transformation** – the fuselage is one *lofted* mesh whose cross-section (a superellipse) blends
  between a boxy car body, a slim fuselage and a round rocket tube, while about 20 parts (wheels → booster
  pods, folded wings → fins, spoiler → tailplane …) glide between three key poses with staggered easing.
* **Curved world** – the world is dead straight, but every vertex is pushed sideways/down in proportion
  to the square of its view distance, so roads wind, hills roll and, at altitude, the horizon curves away.
* **Procedural terrain** – the vertex shader builds the whole ground grid from `gl_VertexID` and noise
  anchored to world coordinates (no vertex buffer, no swimming). The same shader blends grass → cloud deck → moon dust.
* **Procedural sky** – gradient, sun glow, two star layers and a nebula (baked once into a small cubemap),
  all looked up per pixel from the view direction; its palette slides from daylight to space with altitude.
* **Sound and music** – effects and engine loops are synthesised (additive tones, sweeps, filtered noise) and
  cached as WAVs on first launch; the engine pitch follows your speed. The five music loops are rendered the
  same way and played gaplessly through a static `AudioTrack`.
* **Frame budget** – meshes are built on a background thread while the menu is shown, the HUD reads an
  allocation-free state snapshot, and if frames stay slow the 3D resolution drops step by step (the HUD stays
  sharp, because it is a separate view).

### Testing without a device

* `core` has unit tests plus a **bot** that plays all three stages headlessly on several seeds, on every
  difficulty and with a human-like reaction delay, to prove the game is winnable and never produces NaNs.
  The renderer, the vehicle morph, the nebula cubemap and the audio synthesisers have tests too.
* `app` has Robolectric tests that draw every HUD and menu screen to PNGs (`app/build/hud`).
* `tools/render-check` records the *real renderer's* GL command stream to a trace and replays it in headless
  Chromium (WebGL2), which both compiles the GLSL ES 3.00 shaders in a real implementation and produces
  PNG frames of every stage. The real HUD is drawn on top, so the result is what a player sees:

  ```bash
  tools/render-check/screenshots.sh        # → tools/render-check/out/shots/*.png (28 key moments)
  ```

  It needs Node with Playwright + Chromium and Python with Pillow. Under the hood it is
  `./gradlew :core:journeyTrace` (bot plays, GL trace + HUD snapshots are recorded), `render.mjs` (replay in
  Chromium), `HudOverlayTest` (HUD on transparent bitmaps) and `compose.py` (overlay). `RenderTraceTest`
  records showroom views of all three forms and of the stages of both transformations, from the front,
  side and rear, in the same way.

## Status

Developed and verified in a headless cloud environment: the code compiles, all tests pass, Android lint
reports no errors (a few warnings, mostly the deliberate fixed-landscape, non-resizable activity), and the
rendered frames were inspected. It has **not been run on a physical device or emulator**. Things that only
real hardware can confirm: the frame rate on actual GPUs (software GL was used to render the frames), tilt
steering, touch feel, and how the synthesised audio sounds (it was checked numerically and as spectrograms,
not by ear).

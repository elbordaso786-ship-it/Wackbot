# WhackBot (v0.1) - on-phone auto-player, Samsung S23 Ultra / Android 16

Runs entirely on the phone: MediaProjection (screen capture) -> detector -> Accessibility tap.
No PC, root, cloud or paid API at runtime. (A PC is only needed once, to build the APK in Android Studio.)

**Taps:** your avatar (Jasmine), friend's avatar (grey knight), hamster/mouse emoji.
**Never taps:** skull, bomb, coin pile, half-risen items, overlays, anything unrecognised.

## Build & install (no Android Studio needed)
1. On any computer with a browser: make a free GitHub account, create a new repository, and upload the contents of this folder
   (drag the files and folders in, including the hidden `.github` folder). Do not upload `tools/samples`.
2. In the repo open the **Actions** tab > **Build APK** > **Run workflow**. After ~5 minutes download the `WhackBot-apk` artifact, unzip it.
3. Copy `app-debug.apk` to the phone and open it (allow "install unknown apps" when asked).
4. Open WhackBot and press **START - TEST MODE** or **START - AUTO PLAY**. It walks you through any missing permission itself
   (test mode needs only the overlay + screen-capture permission; auto also needs the accessibility switch).
   If Android blocks the accessibility switch for a sideloaded app: Settings > Apps > WhackBot > ⋮ > **Allow restricted settings**.
5. Open the game. A bar at the top-left shows the mode (green = test, red = auto taps on); MODE switches, STOP quits.
   Test-mode decisions also go to `adb logcat -s WhackBot`.
Android Studio also works if you have it (open the folder and press Run).

Keep the same setup as your screenshots: **3-button navigation bar**, same display size, portrait. A different layout
(gesture navigation, different zoom) moves the holes; the scene guard will then block taps instead of tapping wrong places.

## How it decides
Per hole: diff against the bundled empty-board image -> biggest changed blob -> must be fully risen (height >= 85% of width),
not touching the search-box edge -> 32x32 colour fingerprint compared with templates cut from your screenshots.
Tap only if best tap-score >= 0.80 AND beats best skull/bomb score by >= 0.15, for 2 consecutive frames; then that hole is
locked for 1.2 s or until it looks empty. Everything is in `core/Config.kt`.
Scene guard: if the grass between holes stops matching the reference (results screen, dialog, other app) taps are blocked;
after ~3 s in AUTO it switches itself off. Capture-size change / projection stop / accessibility loss also stop it.

## What has actually been tested
Run on a PC with `tools/run_tests.sh` (needs JDK + kotlinc), using the 14 screenshots in `tools/samples/`:
- DONE: detector on all 14 frames x 9 holes = 126 checks, 0 wrong; 0 taps on skull, bomb, coin pile, rising items, volume-slider overlay.
- DONE: tap timing logic (confirm frames, lock, re-arm, never-tap states, min gap, highest confidence first).
- DONE: same verdicts when frames are upscaled to 1440x3088; black frame is rejected by the scene guard.
- CAVEAT: bomb, mouse, skull each have ONE sample (same render every time), and avatars were tested across rows but from one template.
  This shows the logic works on these frames, not that every animation frame in a live round is handled.
- NOT DONE: the Android layer (MainActivity, CaptureService, TapService, Overlay) has **not been compiled or run on a device**.
  Expect to fix small build errors. Real tap latency, capture fps and the accessibility gesture feeling are unmeasured.

## Still needs your gameplay data
- What the coin pile is (bonus? hit effect?). It is currently ignored.
- Results/end screen and the 3-2-1 countdown screenshots (to confirm the guard stops there).
- What a successful hit looks like (score pop-ups/effects) - could be mistaken for a target.
- Different variants of avatars/emoji if the game has more.
- Preview logs of a full round, to tune thresholds.

## Not included
Calibration/labelling UI, saving false-positive frames, on-screen boxes (they would be captured and corrupt detection).
Rebuild templates with other source frames by editing `SOURCES` in `tools/Eval.kt` and running `EvalKt build`.

Note: automating a competitive game can break TikTok's rules and risk the account.

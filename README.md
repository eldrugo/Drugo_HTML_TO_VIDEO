# DRUGO HTML TO VIDEO

Android app to convert animated HTML into vertical MP4 reels, processed locally.

## Initial technical prototype
- Import a local HTML file (CSS, SVG, JS, Canvas).
- Preview it in Android WebView.
- Choose export duration (default 45 seconds).
- Encode a 9:16 H.264 MP4 using Android MediaCodec, without MediaProjection.
- Prototype supports 720x1280 or 1080x1920 at 30 FPS. Rendering quality and timing must be verified on a real device.
- For deterministic frame synchronization, supported HTML can expose `window.drugoRenderAt(ms)`, which draws the requested point in time; arbitrary HTML may only offer live playback and must not be advertised as frame accurate.
- No cloud generation APIs, no paid services.

## Build without consuming Actions artifact storage
Workflow `.github/workflows/android.yml` has **only** `workflow_dispatch`. No builds on push or PR. It uses GitHub Releases to publish `Drugo-HTML-To-Video.apk`; it intentionally **does not** call `actions/upload-artifact`, Gradle caching or use any APK artifact retention.

**Note:** GitHub-hosted workflow builds still consume runner minutes. Release attachments do not consume Actions artifact storage. Use manual builds only when needed.

Navigate to Actions > Generar APK DRUGO HTML TO VIDEO > Run workflow. Then download from Releases.

App prototype does not yet capture Web Audio output. Start by validating silent video generation with the Apollo 13 HTML. A 1080p export may be slow or fail on some devices; test 720p first.

# Changelog

## 0.1.0 – initial port

- Kotlin library `flipbook` (minSdk 24, no dependencies) and `sample` app.
- Line-by-line port of the turn.js 4.1 page engine: sheet fold geometry, hard pages, peel, quick-tap and drag-to-turn, z-ordering, cover centring, shadows and gradients.
- PDF rendering with `PdfRenderer` on a background thread, using the web version's render-queue priority; memory-budgeted page cache; region rendering when zoomed.
- Controls ported from the web viewer: toolbar (zoom, fullscreen, sound), zoom-step panel, page slider with preview and page number, corner/side arrows, loader spinner and progress line, title card, background image.
- Zoom ported from the web viewer: double-tap, pinch, pan with bounds, 6× cap.
- `FlipbookDesign` mirrors every web design option and default; `FlipbookListener` mirrors the web page events.
- Verified on an Android 17 (API 37) emulator (1080×2400) in portrait (single page) and landscape (two-page spread).

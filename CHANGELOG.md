# Changelog

## 0.1.5 – slider position

- The page slider sat 8dp lower than on the website, touching the bottom edge. On the web the range input is inline, so its `.page-bar` box is 46px tall (38px input plus an 8px line gap), not 38px. The Android bar now uses the same 46dp box, so the track, page number and preview line up with the website.

## 0.1.4 – edge swipes no longer close the app

- A page swipe starting at the very edge of the screen could trigger Android's back gesture and close the host screen, which looked like a crash. The back-gesture exclusion now reaches from the screen edge to the page corner zone, in a 200dp band centred on the page (Android's per-edge limit). Back still works above and below that band.
- The slider preview skips a thumbnail that was already freed, instead of drawing it.

## 0.1.3 – remote PDFs

- `FlipbookSource.Url(url, headers, refresh)`, and `open(path)` now accepts `http(s)://` links. The loading spinner shows while the file downloads, as on the website. Downloads are cached in `cacheDir` and reused unless `refresh = true`.
- The library manifest declares `INTERNET`.
- The page slider stays hidden until the book has loaded.
- Sample app: pass `--es url <link>` to open a remote PDF.

## 0.1.2 – clean audio logs

- Android 14+: sounds play through `MediaPlayer` with a declared `flipbook` attribution tag (merged into the host app's manifest). This stops system_server logging `AppOps: attributionTag not declared in manifest` on every page-turn sound. Older Android versions keep using `SoundPool`.

## 0.1.1 – custom sounds

- Replaced the page-turn sounds with the custom ScorePlus sounds (`flip-sm.mp3`, `flip-md.mp3`, `flip-lg.mp3`, 48 kHz), the same files the website now uses. They're mp3 instead of wav, so the library is about 300 KB smaller.
- Sounds stay silent until the reader first touches the screen or presses a key, matching the website fix, where browsers block audio before any user interaction. An automatic turn to a saved or start page is therefore silent.

## 0.1.0 – initial port

- Kotlin library `flipbook` (minSdk 24, no dependencies) and `sample` app.
- Line-by-line port of the turn.js 4.1 page engine: sheet fold geometry, hard pages, peel, quick-tap and drag-to-turn, z-ordering, cover centring, shadows and gradients.
- PDF rendering with `PdfRenderer` on a background thread, using the web version's render-queue priority; memory-budgeted page cache; region rendering when zoomed.
- Controls ported from the web viewer: toolbar (zoom, fullscreen, sound), zoom-step panel, page slider with preview and page number, corner/side arrows, loader spinner and progress line, title card, background image.
- Zoom ported from the web viewer: double-tap, pinch, pan with bounds, 6× cap.
- `FlipbookDesign` mirrors every web design option and default; `FlipbookListener` mirrors the web page events.
- Verified on an Android 17 (API 37) emulator (1080×2400) in portrait (single page) and landscape (two-page spread).

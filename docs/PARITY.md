# Web → Android parity

This maps each part of the web flipbook to its Android port and records every intentional difference. Source of truth: the de-minified web `flipbook.js` / `flipbook.css` (turn.js 4.1 plus the ScorePlus viewer).

Legend: ✅ same behaviour/values · ⚠️ same result, different technique · 🔧 deliberate change (reason given)

## Page engine (turn.js)

| Web | Android | Status |
|---|---|---|
| `turn` plugin: display, `view`, `range`, `_fitPage`, `_turnPage`, `stop`, `update`, `calculateZ`, `_setPageLoc`, `center`, `_updateShadow`, `next`/`previous`/`peel` | `internal/turn/TurnBook.kt` | ✅ line-by-line port |
| `flip` plugin: `_cAllowed`, `_cornerActivated` (60 px corners, 1/6 hover band), `_c`/`_c2`, `_foldingPage`, `_moveFoldingPage`, `_showFoldedPage`, `hideFoldedPage` (800 ms bezier), `turnPage` (elevation 200, duration 1000 ms), event start/move/end, 200 ms quick-tap turn | `internal/turn/Flip.kt` | ✅ |
| Sheet fold maths (`_fold` sheet branch: angles, H/L/F points, recursion when the fold passes the corner) | `Flip.foldSheet` → matrices | ✅ same numbers; CSS transforms become `android.graphics.Matrix` |
| Fold gradients (`ashadow`, `bshadow`, flap `box-shadow` 0.5·O) | `GradientSpec` + `LinearGradient`, `ShadowPainter` | ⚠️ `-webkit-gradient` → `LinearGradient` with the same points and stops; `box-shadow` → pre-blurred nine-slice |
| Hard pages (`type` book/album): rotateY with `perspective: 3000px` | `MagazineView.drawHardWrap` using `android.graphics.Camera` | ⚠️ same angles and z-order swap |
| `animatef` easing (easeOutCirc) | `Easing.turnJs` | ✅ |
| Single display: blank white back page (`p-temporal`), `clip-path: inset(-100vw 0 …)` | `pageObjs[0]`, `canvas.clipRect` | ✅ |
| Live-fold path for pages containing video/iframes | — | not ported (PDF pages never contain media) |

## Viewer (spflip.viewers.magazine)

| Web | Android | Status |
|---|---|---|
| `resizeViewport`: single when narrower than 750 px and portrait, `calculateBound`, even width, render aspect ratio | `FlipbookView.resizeViewport` | ✅ (CSS px = dp) |
| Cover centring (margin-left ±w/4, 0.5 s transition) | `MagazineView.setMargin` | ✅ |
| `getVisiblePages`, `isLastPage` | same names | ✅ |
| Preloading page+4 after each turn (mobile count) | `onTurned` | ✅ |
| Page-edge strips (`show_edges`) incl. timers | `adjustPageDepth` + `DepthStrip` | ✅ visual; the hover label is desktop-only on the web too |

## Controls

| Web | Android | Status |
|---|---|---|
| Toolbar `#pnlControls` position (top 5/20, right 5/20/50), 85% white, radius 5, icon sizes 24/32/42, margins 6/3, zoom icon `margin-bottom: 2px` | `ToolbarView` | ✅ |
| Icon sprite `iconset2_6.png` (23 cells) | `IconSprite` | ✅ same image |
| Zoom: `zoomSet` maths, ×1.5 centre zoom, 1.15 increment, cap 6, pinch `pinchScale + 3(s−1)`, bounded panning, re-render by level after 150 ms | `ZoomController` | ✅ |
| Hi-res zoom rendering (whole page at 1.5–6×) | region render of the visible part only | 🔧 same sharpness, far less memory |
| Zoom step panel (top 100, right 10/50, 13 px gaps) | `ZoomStepView` | ✅ |
| Slider: 8 px track, 87×10 thumb, `positionSliderThumb`, `rangeToPage`, preview 150 px bubble with tail (thumbnail 253 px wide, 130 px tall), number label, 100 ms debounce, 500 ms fade | `PageSliderView` | ✅ |
| Corner arrows (32 px, sprite cell 6, next mirrored, bottom 5, outside ±42 or inside when ≤ 750 px), arrows 0/2/3 | `MagazineView.drawArrows` | ✅ |
| Arrows reappear on zoom-out even on page 1 (jQuery `fadeIn` shows hidden elements) | only the arrows that `showHide` allows reappear | 🔧 fixes a visible web glitch |
| Sounds: flip-sm 0.1 on corner grab, flip-md / flip-lg 0.2 on turn (lg when skipping pages) | `FlipSounds` (SoundPool) | ✅ same files and volumes |
| Fullscreen button | immersive system bars | ⚠️ |
| Keyboard ←/→, Esc | `onKeyDown` | ✅ |
| Mouse-wheel page turning, hover peel with a mouse | — | not applicable on touch (the web ignores these on touch devices too) |
| Tooltips | — | the web skips tooltips on touch devices (`$.isTouch`) |
| `#loaderLine` progress bar (shown after 2 s) | `LoaderLineView` | ✅ |
| 12-dot spinner (1.2 s, delays −1.1…−0.1 s) | `Spinner` | ✅ |

## Design options (scoreplusDesign)

| Web | Android | Status |
|---|---|---|
| `FLIPBOOK_DEFAULT_DESIGN` keys and defaults | `FlipbookDesign` | ✅ |
| Background `back5.svg` (cover, center-left, 40%, +50 px tall) | `res/drawable/flipbook_background.xml` + `BackgroundView` | ✅ vector copy of the SVG |
| Spine gradients (.even/.odd, 0.2/0.15, 95→100%, media rules at 750 px) | `drawPageGradient` | ✅ |
| Page shadow `0 0 20px #ccc`, single-display double shadow | `ShadowPainter` | ✅ |
| Rounded pages (`--fborder` = 2.4% of width) | `cornerRadii` | ⚠️ main cases; a few hard-page + RTL radius combinations are simplified |
| Binding shade (page 1 / last even page) | `drawBinding` | ✅ |
| Title card | `TitleView` | ✅ |
| `load_page = -1` (localStorage `lastpage-<name>`) | SharedPreferences, same key | ✅ |
| URL options `?page=`, `?ap=`, `?pl=`, `?dp=` | `startPage`, `autoPlaySeconds`, `peelCorner`, `showDouble` | ✅ |
| Hash navigation `#page/N` | `goToPage` / `listener` | ⚠️ no URL on Android |
| `scoreplusTurning` / `scoreplusTurned` events | `FlipbookListener` | ✅ |

## Android-only additions

- Page-corner areas are excluded from Android's edge back-gesture, so corner drags near the screen edge work.
- The PDF comes from assets, a file or a content URI, and the library needs no internet permission.

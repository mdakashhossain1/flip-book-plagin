# ScorePlus Flipbook

[![Android release](https://img.shields.io/github/v/release/mdakashhossain1/flip-book-plagin?filter=android-v*&label=android)](https://github.com/mdakashhossain1/flip-book-plagin/releases?q=android-v&expanded=true)
[![Website release](https://img.shields.io/github/v/release/mdakashhossain1/flip-book-plagin?filter=website-v*&label=website)](https://github.com/mdakashhossain1/flip-book-plagin/releases?q=website-v&expanded=true)
[![JitPack](https://jitpack.io/v/mdakashhossain1/flip-book-plagin.svg)](https://jitpack.io/#mdakashhossain1/flip-book-plagin)

A PDF flipbook viewer with page-turn animation, zoom, a swipeable thumbnail strip, a page-number badge and sound, for **Android** apps and **websites**.

Every code block below is complete and copy-ready. Version numbers in it are always the newest release: they're updated automatically whenever a new version ships.

- [Android](#android): 3 steps
- [Website](#website): 2 steps
- [Reference](#reference): every option and API, only if you need more

---

## Android

Needs `minSdk 24` or higher.

### Step 1: Add JitPack

In `settings.gradle.kts`, inside `dependencyResolutionManagement { repositories { … } }`:

```kotlin
maven {
    url = uri("https://jitpack.io")
    content { includeGroup("com.github.mdakashhossain1") }
}
```

### Step 2: Add the library

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mdakashhossain1:flip-book-plagin:android-v0.2.1")
}
```

Sync Gradle. This version is the newest release, and the line is updated automatically with every release.

### Step 3: Show a PDF

Put your PDF at `app/src/main/assets/books/sample.pdf`, then use **one** of these.

#### Jetpack Compose

Copy this whole file as `ReaderActivity.kt`:

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.scoreplus.flipbook.FlipbookDesign
import com.scoreplus.flipbook.FlipbookView

class ReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Flipbook(
                source = "books/sample.pdf",
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            ) {
                showThumbnails = 1      // thumbnail strip: 1 show, 0 hide
                showPageNumber = 1      // page badge "4-5 / 24": 1 show, 0 hide
                showSlider = 3          // bottom slider: 0 hide, 1 preview, 2 number, 3 both
                showDouble = 0          // 0 auto, 1 always two pages, 2 always one page
                controlsSize = "lg"     // toolbar size: "sm", "md", "lg"
                arrows = 1              // 0 tap areas, 1 corner arrows, 2 side bars, 3 none
                soundFlip = 1           // page-turn sound: 1 on, 0 off
                loadPage = -1           // null first page, -1 last page read, or a page number
            }
        }
    }
}

@Composable
fun Flipbook(source: String, modifier: Modifier = Modifier, design: FlipbookDesign.() -> Unit = {}) {
    AndroidView(
        modifier = modifier,
        factory = { context -> FlipbookView(context) },
        update = { view ->
            if (view.tag != source) {
                view.tag = source
                view.open(source, FlipbookDesign().apply(design))
            }
        },
        onRelease = { it.release() },
    )
}
```

#### Android Views (no Compose)

Copy this whole file as `ReaderActivity.kt`:

```kotlin
import android.app.Activity
import android.os.Bundle
import com.scoreplus.flipbook.FlipbookDesign
import com.scoreplus.flipbook.FlipbookView

class ReaderActivity : Activity() {
    private lateinit var flipbook: FlipbookView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flipbook = FlipbookView(this)
        setContentView(flipbook)

        flipbook.open("books/sample.pdf", FlipbookDesign().apply {
            showThumbnails = 1      // thumbnail strip: 1 show, 0 hide
            showPageNumber = 1      // page badge "4-5 / 24": 1 show, 0 hide
            showSlider = 3          // bottom slider: 0 hide, 1 preview, 2 number, 3 both
            showDouble = 0          // 0 auto, 1 always two pages, 2 always one page
            controlsSize = "lg"     // toolbar size: "sm", "md", "lg"
            arrows = 1              // 0 tap areas, 1 corner arrows, 2 side bars, 3 none
            soundFlip = 1           // page-turn sound: 1 on, 0 off
            loadPage = -1           // null first page, -1 last page read, or a page number
        })
    }

    override fun onDestroy() {
        flipbook.release()
        super.onDestroy()
    }
}
```

#### Register the activity

Add your app's `package` line at the top of the file, then declare the activity in `AndroidManifest.xml`. The `configChanges` line keeps the reader on the same page when the phone rotates:

```xml
<activity
    android:name=".ReaderActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
```

**Remote PDF:** use an HTTPS link as the source, e.g. `"https://your-domain.com/books/sample.pdf"`. A spinner shows while it downloads, and the file is cached for next time.

---

## Website

### Step 1: Copy this page

Save it as `flipbook.html` on your website. It loads the viewer from the jsDelivr CDN, so there's nothing else to download or host.

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
    <title>PDF flipbook</title>
    <script>
      window.CDN_PATH = "https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website-v0.2.0/assets";
    </script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website-v0.2.0/assets/css/flipbook.min.css" />
    <script src="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website-v0.2.0/assets/pdfjs/pdf.min.js"></script>
  </head>
  <body>
    <div id="loaderLine" class="loader-line" style="display: none"></div>
    <div class="logo-backs"></div>

    <div class="flipbook-title" style="display: none">
      <h1></h1>
      <h2></h2>
      <p></p>
    </div>

    <div id="canvas">
      <div id="pnlControls" class="controls-pdf controls-md" data-disable-events="true" style="display: none">
        <a class="zoom-icon zoom-icon-in scoreplus-icon" style="display: none"></a>
        <a id="btnFullscreen" class="fullscreen-button scoreplus-icon scoreplus-icn-fullscreen-on"></a>
        <a id="btnSoundOff" class="scoreplus-icon scoreplus-icn-sound-on" style="display: none"></a>
      </div>

      <div id="pnlZoomStep" class="controls-pdf controls-zoom-step controls-md" style="display: none">
        <a class="btnZoomMore scoreplus-icon scoreplus-icn-zoom-more" data-disable-events="true" data-disable-zoom="true"></a>
        <a class="btnZoomLess scoreplus-icon scoreplus-icn-zoom-less" data-disable-events="true" data-disable-zoom="true"></a>
      </div>

      <div class="page-number-badge"></div>

      <div id="magazineViewport" class="magazine-viewport">
        <div class="magazine-viewport-loader" style="display: none"></div>
      </div>
    </div>

    <script id="tplMagazine" type="text/x-template">
      <div class="container">
        <div class="magazine">
          <div ignore="1" class="page-depth page-depth-left" style="display: none"></div>
          <div ignore="1" class="page-depth page-depth-right" style="display: none"></div>
          <div ignore="1" class="next-button btnNext" data-disable-events="true" data-disable-zoom="true"></div>
          <div ignore="1" class="previous-button btnPrevious" data-disable-events="true" data-disable-zoom="true"></div>
        </div>
      </div>
      <div class="bottom control-bottom">
        <div class="thumb-strip" data-disable-events="true" data-disable-zoom="true"><div class="thumb-strip-track"></div></div>
        <div class="page-bar">
          <span class="page-bar-value" style="display: none"></span>
          <span class="page-bar-num" style="display: none"></span>
          <input class="page-bar-range" type="range" value="0" min="0" max="50" step="1" />
        </div>
      </div>
      <div ignore="1" class="page-depth-label" style="display: none"></div>
    </script>

    <script src="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website-v0.2.0/assets/js/flipbook.min.js"></script>
    <script>
      $(() => openFlipbook("/books/sample.pdf", {
        show_thumbnails: 1,     // thumbnail strip: 1 show, 0 hide
        show_page_number: 1,    // page badge "4-5 / 24": 1 show, 0 hide
        show_slider: 3,         // bottom slider: 0 hide, 1 preview, 2 number, 3 both
        show_double: 0,         // 0 auto, 1 always two pages, 2 always one page
        controls_size: "lg",    // toolbar size: "sm", "md", "lg"
        arrows: 1,              // 0 tap areas, 1 corner arrows, 2 side bars, 3 none
        sound_flip: 1,          // page-turn sound: 1 on, 0 off
        load_page: -1           // null first page, -1 last page read, or a page number
      }));
    </script>
  </body>
</html>
```

### Step 2: Point it at your PDF

Change `"/books/sample.pdf"` to your PDF's path or URL, and open the page through your web server (not by double-clicking the file). A PDF on another domain must allow CORS.

**Show it inside another page:**

```html
<iframe src="/flipbook.html" title="PDF flipbook" style="width: 100%; height: 80vh; border: 0" allow="fullscreen"></iframe>
```

Add `#page/10` to the URL to open at page 10.

**Prefer self-hosting?** Download the ZIP from the newest [website release](https://github.com/mdakashhossain1/flip-book-plagin/releases?q=website-v&expanded=true), copy `index.html` and `assets/` to your site, and change the `openFlipbook(...)` path at the bottom of `index.html`.

---

## Reference

Everything below is optional detail.

### Automatic updates

#### Android (Gradle + JitPack)

| Version string | You get | Changes by itself? | Best for |
| --- | --- | --- | --- |
| `android-vX.Y.Z` | That exact release | No | Production apps (recommended) |
| `latest.release` | The newest published release | Yes, at build time | Always staying current |
| `android-v+` | The newest `android-v…` release | Yes, at build time | Same as above, limited to Android tags |
| `android-SNAPSHOT` | The newest commit on the `android` branch, released or not | Yes, every commit | Testing unreleased fixes |

All four were tested against JitPack: `latest.release` and `android-v+` resolved to the newest release, and `android-SNAPSHOT` to the newest `android` branch build.

Things to know:

- **Gradle caches dynamic versions for 24 hours.** A new release can take up to a day to show up in your build. To pick it up now, run a build with `--refresh-dependencies`, or shorten the cache in `app/build.gradle.kts`:

  ```kotlin
  configurations.all {
      resolutionStrategy.cacheDynamicVersionsFor(10, "minutes")
      resolutionStrategy.cacheChangingModulesFor(10, "minutes")
  }
  ```

  `cacheDynamicVersionsFor` applies to `latest.release` and `android-v+`; `cacheChangingModulesFor` applies to `android-SNAPSHOT`.
- **JitPack builds a version the first time someone asks for it.** After a new tag is pushed, the first build that requests it (or a click on **Get it** on the [JitPack page](https://jitpack.io/#mdakashhossain1/flip-book-plagin)) makes JitPack build it, which can take a few minutes. Until then, dynamic versions still resolve to the previous release.
- **Automatic updates make builds non-reproducible:** two builds from the same code can contain different library versions. If you want automatic lookups but controlled upgrades, keep `latest.release` and turn on Gradle dependency locking in `app/build.gradle.kts`:

  ```kotlin
  dependencyLocking {
      lockAllConfigurations()
  }
  ```

  Then run `./gradlew :app:dependencies --write-locks` once and commit the generated `gradle.lockfile`. Builds use the locked version until you run `--write-locks` again to upgrade.

#### Website (jsDelivr)

| URL version | You get | Changes by itself? | Best for |
| --- | --- | --- | --- |
| `@website-vX.Y.Z` | That exact release | No | Production sites (recommended) |
| `@website` | The newest commit on the `website` branch | Yes | Always staying current |
| `@<commit-sha>` | One exact commit | No | Pinning an unreleased fix |

Things to know:

- jsDelivr caches branch URLs, so a new commit on `website` can take up to about 12 hours to reach `@website`. To refresh immediately, purge the files that changed:

  ```sh
  curl https://purge.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets/js/flipbook.min.js
  curl https://purge.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets/css/flipbook.min.css
  ```

- Browsers cache too. After an update, a hard refresh (Ctrl+F5) shows the new version immediately.
- Keep the same `@…` in all four URLs of the page. Mixing versions can break the viewer.
- The tags have a platform prefix (`website-v…`), so jsDelivr's version ranges such as `@0.2` don't apply. Use a full tag, the branch, or a commit.

### Design options

Android sets these on `FlipbookDesign`; the website passes them as the second argument of `openFlipbook(path, options)`. Options you leave out keep their defaults. Values are integers unless noted.

| Android (`FlipbookDesign`) | Website key | Default | What it does |
| --- | --- | --- | --- |
| `showThumbnails` | `show_thumbnails` | `1` | Swipeable thumbnail strip above the slider. `0` hides it and gives the space back to the book. |
| `showPageNumber` | `show_page_number` | `1` | Page badge in the top-left corner, e.g. `4-5 / 24`. |
| `showSlider` | `show_slider` | `3` | Bottom page slider. `0` hidden, `1` page preview while dragging, `2` page number while dragging, `3` both. |
| `showDouble` | `show_double` | `0` | `0` automatic (one page on narrow portrait screens, two otherwise), `1` always two pages, `2` always one page. |
| `showZoom` | `show_zoom` | `1` | Zoom button in the toolbar. With `showZoom = 0` and `clickZoom = 0`, zoom is off. |
| `clickZoom` | `click_zoom` | `2` | `0` off, `1` mouse wheel zooms (web), `2` double-tap / Ctrl+wheel zooms. |
| `showFullscreen` | `show_fullscreen` | `1` | Fullscreen button in the toolbar. |
| `soundFlip` | `sound_flip` | `1` | Page-turn sound and the sound button. Sounds start after the reader's first touch or key press. |
| `controlsSize` (String) | `controls_size` | `null` (`"md"`) | Toolbar size: `"sm"`, `"md"` or `"lg"`. Small screens automatically use smaller controls. |
| `arrows` | `arrows` | `1` | `0` invisible tap areas, `1` corner arrows, `2` side bars, `3` none. |
| `rtl` | `rtl` | `0` | `1` for right-to-left books (pages turn the other way). |
| `loadPage` (Int?) | `load_page` | `null` | Page to open at. `null` first page, `-1` the last page the reader saw (remembered per file), or a page number. |
| `type` (String?) | `type` | `null` | `null` soft pages; `"book"` hard covers; `"notebook"` hard covers with rounded pages; `"album"` every page hard. |
| `showShadow` | `show_shadow` | `1` | Shadow around the pages. |
| `showDepth` | `show_depth` | `1` | Shading along the spine. |
| `showEdges` | `show_edges` | `0` | Page-edge strips that show how many pages remain on each side. |
| `showRound` | `show_round` | `0` | Rounded page corners. |
| `showBinding` | `show_binding` | `0` | Draws a binding along the spine. |
| `showCenter` | `show_center` | `1` | Centres the book when only the front or back cover is showing. |
| `showText` | `show_text` | `1` | Shows the title card when `title`, `subtitle` or `description` is set. |
| `title`, `subtitle`, `description` (String) | same | `""` | Text for the title card. |
| `background` (drawable res id, `0` = none) | `background` (image URL) | built-in pattern | Background image behind the book. |
| `backgroundColor` (Int?) | `background_color` | `null` (white) | Background colour. |
| `backgroundSize`, `backgroundTransparency` | `background_style` | `"cover"`, `null` | Background size (`"cover"`, `"contain"`, `"auto"`) and opacity (0–100). |
| `startPage` (Int?) | — | `null` | Android only: page to open at, applied after `loadPage`. |
| `autoPlaySeconds` (Int?) | — | `null` | Android only: turn the page automatically every N seconds. |
| `peelCorner` (String?) | — | `null` | Android only: peel a corner (`"tr"`, `"br"`, `"tl"`, `"bl"`) every 3 s as a hint. |

### Android API

**PDF sources.** `open(path)` treats `http://` and `https://` as URLs and anything else as an asset path. For other sources, pass a `FlipbookSource`:

```kotlin
flipbook.open(FlipbookSource.Asset("books/sample.pdf"))
flipbook.open(FlipbookSource.LocalFile(File(filesDir, "book.pdf")))
flipbook.open(FlipbookSource.ContentUri(uri))
flipbook.open(FlipbookSource.Url(
    url = "https://your-domain.com/book.pdf",
    headers = mapOf("Authorization" to "Bearer $token"),
    refresh = false,
))
```

- `Asset`: copied once into the app cache (`PdfRenderer` needs a real file), and re-copied if the asset's size changes.
- `Url`: downloaded once into the app cache while the spinner shows, following up to 5 redirects. Later opens reuse the cached file; pass `refresh = true` to download again. An HTTP error or network failure calls `onError`.
- `ContentUri`: works with the system file picker, e.g. `ActivityResultContracts.OpenDocument()`.

**Control and state.**

| Member | Description |
| --- | --- |
| `goToPage(page)` | Turn to a page (1-based). |
| `nextPage()` / `previousPage()` | Turn one page. Respects `rtl`. |
| `zoomIn()` / `zoomOut()` | Zoom around the centre. |
| `pageCount` | Number of pages; `0` until loaded. |
| `currentPage` | Current page; `0` until loaded. |
| `soundEnabled` | Read or toggle the page-turn sound. |
| `listener` | A `FlipbookListener` for events (below). |
| `release()` | Frees the renderer and bitmaps. Also runs automatically when the view is detached. |

Call the control methods after `onLoaded`; before that they do nothing.

**Events.** Every method has a default, so override only what you need:

```kotlin
flipbook.listener = object : FlipbookListener {
    override fun onLoaded(numPages: Int) {}
    override fun onTurning(page: Int?) {}                          // turn started; null while a corner is being dragged
    override fun onTurned(page: Int, visiblePages: IntArray) {}    // turn finished
    override fun onError(error: Throwable) {}                      // PDF could not be opened or downloaded
}
```

**Memory.** Pages render on one background thread into a memory-budgeted cache (1/6 of the app heap, clamped to 32–96 MB). Zoomed views render only the visible part of the page, and the thumbnail strip keeps at most 40 small bitmaps.

**R8 / ProGuard.** No extra rules needed.

### Website API

**Open a PDF:** `openFlipbook(path, options)`. Call it once per page load, after the DOM is ready (`$(() => ...)`). `path` is relative to the page or an absolute URL; `options` are the [design options](#design-options).

**Events** are jQuery events on `document`:

```js
$(document).on("scoreplusTurning", (event, fileName, page) => {
  // a turn started
});
$(document).on("scoreplusTurned", (event, fileName, page) => {
  // a turn finished
});
```

**Control the book** through the underlying turn.js element:

```js
$(".magazine").turn("next");
$(".magazine").turn("previous");
$(".magazine").turn("page", 10);      // go to page 10
$(".magazine").turn("page");          // current page
$(".magazine").turn("pages");         // number of pages
```

**Deep links:** the URL hash follows the book (`#page/6`), and opening a URL with `#page/N` starts at page N.

**Global settings**, read when `flipbook.min.js` loads, so set them in a `<script>` before it:

| Variable | Default | Purpose |
| --- | --- | --- |
| `window.CDN_PATH` | `"assets"` | Base URL of the viewer's `assets/` folder (images, sounds, PDF worker). |
| `window.PDFJS_WORKER` | `CDN_PATH + "/pdfjs/pdf.worker.min.js"` | Custom location of the pdf.js worker. |

### Opening the website viewer from disk (file://)

Browsers don't let a page opened with a double-click read a PDF file directly. The viewer handles this by loading a JavaScript copy of the PDF, named `<your pdf>.js` and stored next to the PDF. Create it once per PDF:

**Windows (PowerShell)**, in the PDF's folder:

```powershell
$pdf = "sample.pdf"
$b = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $pdf)))
[IO.File]::WriteAllText("$PWD\$pdf.js", "window.FLIPBOOK_PDF_DATA = '$b';")
```

**macOS / Linux:**

```sh
printf "window.FLIPBOOK_PDF_DATA = '%s';" "$(base64 < sample.pdf | tr -d '\n')" > sample.pdf.js
```

Keep `openFlipbook("assets/books/sample.pdf")` pointing at the `.pdf`; the viewer adds `.js` itself when opened from disk. Over HTTP/HTTPS the `.js` file is not used. On a real website, serve the page over HTTP instead; the `.js` copy is about a third larger than the PDF.

### Network, CORS and permissions

- **Android:** the library declares `INTERNET` itself. Android blocks plain `http://` by default, so use `https://`, or allow the host in your app's [network security config](https://developer.android.com/privacy-and-security/security-config).
- **Website:** a PDF on another domain must be served with CORS headers, for example `Access-Control-Allow-Origin: https://your-site.com`. Without them the browser blocks the download and the spinner never finishes. PDFs on the same domain as the page need nothing.
- **Website via CDN:** jsDelivr sends the right CORS headers for the viewer files; only your own PDF needs attention.

### Troubleshooting

| Symptom | Fix |
| --- | --- |
| Gradle can't find `flip-book-plagin` | Check that the JitPack repository is in `settings.gradle.kts` and that the version is `android-v0.2.1` or later. The first request for a new version can take a few minutes while JitPack builds it. |
| A new release doesn't appear with `latest.release` | Gradle caches dynamic versions for 24 hours. Build with `--refresh-dependencies`. |
| Android app goes back or closes on an edge swipe | Update to the latest release; the edge-gesture handling was fixed. Swipes that start above or below the page's middle band still go to the system, by design. |
| Android spinner never ends for a URL | Check `onError`. Common causes: `http://` blocked by Android, a server error, or an expired auth header. |
| Web spinner never ends | Open the browser console. A CORS error means the PDF server needs CORS headers; a 404 means the PDF path is wrong (it is relative to the page). |
| Web viewer shows no icons or sounds (CDN setup) | `window.CDN_PATH` must be set before `flipbook.min.js`, and must end in `/assets` with no trailing slash. |
| `Missing ….pdf.js` in the console | The page was opened from disk. Create the `.js` copy ([file://](#opening-the-website-viewer-from-disk-file)) or serve the page over HTTP. |
| No sound at first | Browsers and the library stay silent until the reader first touches, clicks or presses a key. |

### How releases work

Each branch releases itself with [release-please](https://github.com/googleapis/release-please) in GitHub Actions. Nobody edits version numbers by hand:

1. Commits use the [Conventional Commits](https://www.conventionalcommits.org/) format. `fix: …` makes a patch release, `feat: …` a minor release, and `docs:`, `chore:` or `ci:` no release.
2. Every push to `android` or `website` opens or updates a **release PR** on that branch. It bumps the branch's `version.txt` and adds the new section to its `CHANGELOG.md`.
3. Merging the release PR creates the tag (`android-vX.Y.Z` or `website-vX.Y.Z`) and the GitHub Release. The Android workflow attaches the AAR and asks JitPack to build the tag; the website workflow attaches the ZIP.
4. After the release, the workflow updates the version in this README (the Android dependency line and the website CDN URLs) and commits it to `main`. The badges update too, and users who watch the repository's releases are notified.

A **CI** workflow on each branch runs on every push and pull request: Android builds the library and the sample app and checks the JitPack publication; the website checks that the scripts parse and that `index.html` only references files that exist.

## Build from source

Android:

```sh
git clone --branch android --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-android
cd flipbook-android
```

Open it in Android Studio (compileSdk 36; Gradle is configured for JDK 25). Build with `.\gradlew.bat :flipbook:assembleRelease` on Windows or `bash gradlew :flipbook:assembleRelease` on macOS/Linux. The AAR is written to `flipbook/build/outputs/aar/flipbook-release.aar`. To check the Maven publication locally, run `.\gradlew.bat :flipbook:publishReleasePublicationToMavenLocal "-Pversion=android-vX.Y.Z"`. JitPack runs the command in `jitpack.yml` for each requested tag ([JitPack Android guide](https://docs.jitpack.io/android/)).

Website:

```sh
git clone --branch website --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-website
```

The viewer needs no build; serve the folder with any static web server.

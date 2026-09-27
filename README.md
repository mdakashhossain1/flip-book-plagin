# ScorePlus Flipbook

A PDF flipbook viewer for Android apps and websites, with page-turn animation, zoom, a swipeable thumbnail strip, a page-number badge, and sound.

- **Android**: a native Kotlin library, installed from JitPack. Works with Android Views and Jetpack Compose.
- **Website**: a static HTML/JS/CSS viewer. Load it from the jsDelivr CDN, or self-host the ZIP.

## Contents

- [Choose your version](#choose-your-version)
- [Releases](#releases)
- [Android](#android)
  - [1. Add the JitPack repository](#1-add-the-jitpack-repository)
  - [2. Add the dependency](#2-add-the-dependency)
  - [3a. Show a PDF with Android Views](#3a-show-a-pdf-with-android-views)
  - [3b. Show a PDF with Jetpack Compose](#3b-show-a-pdf-with-jetpack-compose)
  - [3c. Java](#3c-java)
- [Website](#website)
  - [Option A: Load from the CDN (auto-updates)](#option-a-load-from-the-cdn-auto-updates)
  - [Option B: Self-host the ZIP](#option-b-self-host-the-zip)
  - [Embed in another page](#embed-in-another-page)
- [Advanced](#advanced)
  - [Automatic updates](#automatic-updates)
  - [Design options](#design-options)
  - [Android API](#android-api)
  - [Website API](#website-api)
  - [Opening the website viewer from disk (file://)](#opening-the-website-viewer-from-disk-file)
  - [Network, CORS and permissions](#network-cors-and-permissions)
  - [Troubleshooting](#troubleshooting)
- [Build from source](#build-from-source)

## Choose your version

| Branch | Contents | Use in your project |
| --- | --- | --- |
| [`android`](https://github.com/mdakashhossain1/flip-book-plagin/tree/android) | Native Kotlin library and sample app | Add the Maven dependency through JitPack |
| [`website`](https://github.com/mdakashhossain1/flip-book-plagin/tree/website) | Static HTML, JavaScript, CSS, and assets | Load it from jsDelivr, or download the ZIP release and host it |
| `main` | This guide | Start here and choose a platform |

## Releases

| Platform | Release notes | Install |
| --- | --- | --- |
| Android | [Android v0.2.1](https://github.com/mdakashhossain1/flip-book-plagin/releases/tag/android-v0.2.1) | [Gradle + JitPack](#android) |
| Website | [Website v0.2.0](https://github.com/mdakashhossain1/flip-book-plagin/releases/tag/website-v0.2.0) | [CDN](#option-a-load-from-the-cdn-auto-updates) or [ZIP](https://github.com/mdakashhossain1/flip-book-plagin/releases/download/website-v0.2.0/scoreplus-flipbook-website-0.2.0.zip) |

Releases use platform-specific tags: `android-vX.Y.Z` and `website-vX.Y.Z`. See [all releases](https://github.com/mdakashhossain1/flip-book-plagin/releases).

## Android

Requirements: `minSdk 24` (Android 7.0) or higher. The library has no third-party dependencies.

### 1. Add the JitPack repository

In `settings.gradle.kts`, add JitPack inside `dependencyResolutionManagement.repositories`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.mdakashhossain1")
            }
        }
    }
}
```

The `content { includeGroup(...) }` filter makes Gradle look up only this library on JitPack, which keeps builds fast.

<details>
<summary>Groovy (<code>settings.gradle</code>)</summary>

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url 'https://jitpack.io'
            content { includeGroup 'com.github.mdakashhossain1' }
        }
    }
}
```

</details>

### 2. Add the dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mdakashhossain1:flip-book-plagin:android-v0.2.1")
}
```

To always get the newest release without editing this line, use `latest.release` instead of the version. See [Automatic updates](#automatic-updates) for what each option does.

```kotlin
implementation("com.github.mdakashhossain1:flip-book-plagin:latest.release")
```

Sync Gradle. The version is the Android Git tag. Use `android-v0.2.1` or later; the older `android-v0.2.0` release was an AAR download only. [JitPack build page](https://jitpack.io/#mdakashhossain1/flip-book-plagin).

### 3a. Show a PDF with Android Views

Put a PDF at `app/src/main/assets/books/sample.pdf`, then:

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
            showThumbnails = 1
            showPageNumber = 1
            controlsSize = "lg"
        })
    }

    override fun onDestroy() {
        flipbook.release()
        super.onDestroy()
    }
}
```

Declare the activity in your manifest. For a remote PDF, pass an HTTPS URL instead: `flipbook.open("https://your-domain.com/books/sample.pdf")`. The spinner shows while it downloads, and the file is cached for next time.

To keep the book on the same page when the screen rotates, add this to the activity in the manifest. The view re-lays itself out for the new size.

```xml
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
```

### 3b. Show a PDF with Jetpack Compose

The library is a regular Android `View`, so Compose shows it through `AndroidView`. Copy this `Flipbook` composable into your project once and use it anywhere:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.scoreplus.flipbook.FlipbookDesign
import com.scoreplus.flipbook.FlipbookListener
import com.scoreplus.flipbook.FlipbookView

@Composable
fun Flipbook(
    source: String,
    modifier: Modifier = Modifier,
    design: FlipbookDesign.() -> Unit = {},
    onLoaded: (pageCount: Int) -> Unit = {},
    onPageChanged: (page: Int, visiblePages: IntArray) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = {},
    onViewReady: (FlipbookView) -> Unit = {},
) {
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)
    val latestOnError by rememberUpdatedState(onError)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FlipbookView(context).apply {
                listener = object : FlipbookListener {
                    override fun onLoaded(numPages: Int) = latestOnLoaded(numPages)
                    override fun onTurned(page: Int, visiblePages: IntArray) = latestOnPageChanged(page, visiblePages)
                    override fun onError(error: Throwable) = latestOnError(error)
                }
                onViewReady(this)
            }
        },
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

Use it in a screen:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ReaderScreen() {
    Flipbook(
        source = "books/sample.pdf",
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        design = {
            showThumbnails = 1
            showPageNumber = 1
            controlsSize = "lg"
        },
    )
}
```

Call it from your activity with `setContent { MaterialTheme { ReaderScreen() } }`.

How the wrapper behaves:

- The PDF opens once. Changing `source` opens the new PDF; other recompositions leave the book where it is.
- `design` is read when a PDF opens. To apply new options, change `source`, or call `view.open(...)` yourself through `onViewReady`.
- The viewer's memory is freed in `onRelease` when the composable leaves the screen.
- `source` accepts anything `FlipbookView.open(path)` does: an asset path, or an `http(s)://` URL. For files and content URIs, call `view.open(FlipbookSource.LocalFile(file))` or `view.open(FlipbookSource.ContentUri(uri))` from `onViewReady`.

This code was tested with Compose BOM `2025.10.00`, `activity-compose 1.11.0`, AGP 9.3.2 and the Compose compiler plugin `2.2.10`, pulling the library from JitPack with `latest.release`. Your project needs Compose set up already (`buildFeatures { compose = true }` and the `org.jetbrains.kotlin.plugin.compose` plugin).

### 3c. Java

```java
FlipbookView flipbook = new FlipbookView(this);
setContentView(flipbook);

FlipbookDesign design = new FlipbookDesign();
design.setShowThumbnails(1);
design.setControlsSize("lg");
flipbook.open("books/sample.pdf", design);
```

## Website

The viewer is a static page, with no build step and no npm. It needs the markup in its `index.html`, one stylesheet, and two scripts.

### Option A: Load from the CDN (auto-updates)

jsDelivr serves the `website` branch straight from GitHub, so you don't host any viewer files. Only your PDF lives on your server. Save this as a page on your site (for example `public/flipbook.html`) and change the PDF path at the bottom:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
    <title>PDF flipbook</title>

    <!-- @website follows the newest commit; use @website-v0.2.0 to pin a release -->
    <script>
      window.CDN_PATH = "https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets";
    </script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets/css/flipbook.min.css" />
    <script src="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets/pdfjs/pdf.min.js"></script>
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

    <script src="https://cdn.jsdelivr.net/gh/mdakashhossain1/flip-book-plagin@website/assets/js/flipbook.min.js"></script>
    <script>
      $(() => openFlipbook("/books/sample.pdf", {
        show_thumbnails: 1,
        show_page_number: 1,
        controls_size: "lg"
      }));
    </script>
  </body>
</html>
```

Rules for this setup:

- `window.CDN_PATH` must be set **before** `flipbook.min.js` loads. The viewer loads its images, sounds and PDF worker from that address.
- Use the same `@…` version in all four URLs.
- Serve the page over HTTP or HTTPS. A page opened with a double-click can't read your PDF (see [file://](#opening-the-website-viewer-from-disk-file)).
- The PDF path is resolved from the page URL. A PDF on another domain needs CORS (see [Network, CORS and permissions](#network-cors-and-permissions)).

This exact page was tested served from a local web server: all viewer files loaded from jsDelivr, the pdf.js worker started, and thumbnails, the page badge and page turns all worked.

### Option B: Self-host the ZIP

1. Download [`scoreplus-flipbook-website-0.2.0.zip`](https://github.com/mdakashhossain1/flip-book-plagin/releases/download/website-v0.2.0/scoreplus-flipbook-website-0.2.0.zip) and extract it.
2. Copy `index.html` and the whole `assets/` folder into a public folder of your site, such as `public/flipbook/`. Keep the folder structure.
3. Put your PDF in `assets/books/`, and replace the last inline script in `index.html`:

```html
<script>
  $(() => openFlipbook("assets/books/sample.pdf", {
    show_thumbnails: 1,
    show_page_number: 1,
    controls_size: "lg",
    sound_flip: 1
  }));
</script>
```

A self-hosted copy never changes by itself. To update, download the new ZIP and replace `assets/` (keep your `books/`).

### Embed in another page

Either option gives you a standalone page. Embed it with an iframe:

```html
<iframe
  src="/flipbook/index.html"
  title="PDF flipbook"
  style="width: 100%; height: 80vh; border: 0"
  allow="fullscreen"
></iframe>
```

In React JSX, write the style as `style={{ width: "100%", height: "80vh", border: 0 }}`. To start at a specific page, add a hash to the URL: `/flipbook/index.html#page/10`.

## Advanced

### Automatic updates

#### Android (Gradle + JitPack)

| Version string | You get | Changes by itself? | Best for |
| --- | --- | --- | --- |
| `android-v0.2.1` | That exact release | No | Production apps (recommended) |
| `latest.release` | The newest published release | Yes, at build time | Always staying current |
| `android-v+` | The newest `android-v…` release | Yes, at build time | Same as above, limited to Android tags |
| `android-SNAPSHOT` | The newest commit on the `android` branch, released or not | Yes, every commit | Testing unreleased fixes |

All four were tested against JitPack. `latest.release` and `android-v+` resolved to `android-v0.2.1`, and `android-SNAPSHOT` resolved to the newest `android` branch build.

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
| `@website-v0.2.0` | That exact release | No | Production sites (recommended) |
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

## Build from source

Android:

```sh
git clone --branch android --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-android
cd flipbook-android
```

Open it in Android Studio (compileSdk 36; Gradle is configured for JDK 25). Build with `.\gradlew.bat :flipbook:assembleRelease` on Windows or `bash gradlew :flipbook:assembleRelease` on macOS/Linux. The AAR is written to `flipbook/build/outputs/aar/flipbook-release.aar`. To check the Maven publication locally, run `.\gradlew.bat :flipbook:publishReleasePublicationToMavenLocal "-Pversion=android-v0.2.1"`. JitPack runs the command in `jitpack.yml` for each requested tag ([JitPack Android guide](https://docs.jitpack.io/android/)).

Website:

```sh
git clone --branch website --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-website
```

The viewer needs no build; serve the folder with any static web server.

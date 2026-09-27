# ScorePlus Flipbook for Android

A native Kotlin port of the ScorePlus web flipbook (turn.js + pdf.js). It uses the same page-curl engine, layout, controls, icons, sounds and options as the web version, with no WebView and no third-party dependencies. PDF pages are rendered with Android's built-in `PdfRenderer`.

## Install with Gradle (JitPack)

Add JitPack to the repositories in your project's `settings.gradle.kts`:

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

Add the library in your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mdakashhossain1:flip-book-plagin:android-v0.2.1")
}
```

Use `minSdk` 24 or higher and sync Gradle. Gradle downloads the library and its declared dependencies; no manual AAR copy is needed.

The version is the Android Git tag. The older `android-v0.2.0` tag predates Maven publishing. See the [JitPack build](https://jitpack.io/#mdakashhossain1/flip-book-plagin/android-v0.2.1) and [release notes](https://github.com/mdakashhossain1/flip-book-plagin/releases/tag/android-v0.2.1).

## Quick start

```kotlin
val flipbook = FlipbookView(this)
setContentView(flipbook)
flipbook.open("books/sample.pdf")          // a file in your app's assets/
```

Other sources:

```kotlin
flipbook.open(FlipbookSource.LocalFile(file))
flipbook.open(FlipbookSource.ContentUri(uri))
```

Remote PDFs (a server or CDN) work the same way. The viewer shows its loading spinner while the file downloads, then caches it, so the next open is instant:

```kotlin
flipbook.open("https://cdn.example.com/books/sample.pdf")
flipbook.open(FlipbookSource.Url(url, headers = mapOf("Authorization" to "Bearer …"), refresh = true))
```

The library declares the `INTERNET` permission for you. Android blocks plain `http://` by default, so use `https://` or allow the host in your app's network security config.

Options, which match the web `FLIPBOOK_DEFAULT_DESIGN`:

```kotlin
flipbook.open("book.pdf", FlipbookDesign().apply {
    showRound = 1         // rounded pages
    controlsSize = "lg"   // bigger toolbar
    loadPage = -1         // reopen at the last page the reader saw
    showThumbnails = 0    // hide the thumbnail strip above the slider
    showPageNumber = 0    // hide the page number badge
})
```

Events and control:

```kotlin
flipbook.listener = object : FlipbookListener {
    override fun onTurned(page: Int, visiblePages: IntArray) { /* ... */ }
}
flipbook.goToPage(10)
flipbook.nextPage()
flipbook.zoomIn()
```

Java works too: `new FlipbookView(context)`, `view.open("book.pdf", new FlipbookDesign())`.

## Modules

| Module | What it is |
|---|---|
| `flipbook/` | The library (AAR). `minSdk 24`, no dependencies. |
| `sample/` | A demo app that opens `sample/src/main/assets/sample.pdf`. |

## Build

Open the folder in Android Studio, or run:

```
gradlew assembleDebug                 # builds the library and the sample app
gradlew :flipbook:assembleRelease     # flipbook/build/outputs/aar/flipbook-release.aar
```

## Maven publishing

The `flipbook` module publishes its release AAR, POM, Gradle metadata, and Kotlin sources with Gradle's `maven-publish` plugin. Verify publication locally with:

```powershell
.\gradlew.bat :flipbook:publishReleasePublicationToMavenLocal "-Pversion=android-v0.2.1"
```

On macOS/Linux use `bash gradlew` in place of `.\gradlew.bat`. JitPack runs the command configured in `jitpack.yml` for the requested Git tag. See [JitPack's Android publishing guide](https://docs.jitpack.io/android/).

## Options

Every field of `FlipbookDesign` has the same name and default as the web design key: `show_slider` → `showSlider`, and so on. See [docs/PARITY.md](docs/PARITY.md) for how each web feature maps to Android and which details differ.

| Option | Default | Values |
|---|---|---|
| `showSlider` | 3 | 0 hidden · 1 preview · 2 page number · 3 both |
| `showDouble` | 0 | 0 auto (two pages when wider than 750 dp or landscape) · 1 always two · 2 always one |
| `arrows` | 1 | 0 invisible tap strips · 1 corner arrows · 2 side bars · 3 none |
| `showZoom`, `clickZoom` | 1, 2 | zoom button; double-tap zoom |
| `showFullscreen` | 1 | immersive mode |
| `soundFlip` | 1 | page-turn sounds |
| `showShadow`, `showDepth`, `showCenter` | 1 | page shadow, spine gradient, centred cover |
| `showEdges`, `showRound`, `showBinding` | 0 | page-edge strips, rounded pages, binding shade |
| `type` | null | `"book"`, `"notebook"`, `"album"` (hard pages) |
| `rtl` | 0 | right-to-left books |
| `controlsSize` | null | `"sm"`, `"md"`, `"lg"` |
| `loadPage` | null | page number, or -1 for the last page read |
| `background`, `backgroundColor`, `backgroundSize`, `backgroundTransparency` | built-in | background image and colour |
| `title`, `subtitle`, `description` | "" | title card |
| `startPage`, `autoPlaySeconds`, `peelCorner` | null | the web `?page=`, `?ap=`, `?pl=` URL options |

## Memory

Pages are rendered at screen resolution, and only the pages around the current one are kept. They're evicted farthest-first once the cache passes its budget (maxMemory / 6, between 32 and 96 MB). When zoomed in, only the visible part of each page is re-rendered at high resolution, instead of a whole page 6× larger as in the web version.

## History

See [CHANGELOG.md](CHANGELOG.md).

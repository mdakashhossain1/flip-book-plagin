# ScorePlus Flipbook

A PDF flipbook viewer for Android apps and websites, with page-turn animation, zoom, thumbnails, page numbers, and sound controls.

## Choose your version

| Branch | Contents | Use in your project |
| --- | --- | --- |
| [`android`](https://github.com/mdakashhossain1/flip-book-plagin/tree/android) | Native Kotlin library and sample app | Add the Maven dependency through JitPack |
| [`website`](https://github.com/mdakashhossain1/flip-book-plagin/tree/website) | Static HTML, JavaScript, CSS, and assets | Download the ZIP release, host it, and embed it with an iframe |
| `main` | This getting-started guide | Start here and choose a platform |

## Releases

| Platform | Release notes | Ready-to-use download |
| --- | --- | --- |
| Android | [Android v0.2.1](https://github.com/mdakashhossain1/flip-book-plagin/releases/tag/android-v0.2.1) | [Install through JitPack](#android) |
| Website | [Website v0.2.0](https://github.com/mdakashhossain1/flip-book-plagin/releases/tag/website-v0.2.0) | [Download the ZIP](https://github.com/mdakashhossain1/flip-book-plagin/releases/download/website-v0.2.0/scoreplus-flipbook-website-0.2.0.zip) |

Install Android through Gradle using the JitPack Maven repository. For the website, download the ZIP from the GitHub release's **Assets** section. You do not need to clone or build the repository. Each platform's source remains on its own branch.

## Android

### 1. Add the JitPack Maven repository

In your project's `settings.gradle.kts`, add JitPack inside the existing `dependencyResolutionManagement.repositories` block:

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

### 2. Add it to your app

In `app/build.gradle.kts`, add this dependency to your existing dependencies block:

```kotlin
dependencies {
    implementation("com.github.mdakashhossain1:flip-book-plagin:android-v0.2.1")
}
```

Set your app's `minSdk` to at least `24` (Android 7.0), then sync Gradle. Gradle downloads the library and its declared dependencies automatically.

The version matches the Android Git tag. Use `android-v0.2.1` or a later Android release configured for Maven publishing; the older `android-v0.2.0` release was distributed as a manual AAR download. [View the JitPack build](https://jitpack.io/#mdakashhossain1/flip-book-plagin/android-v0.2.1).

### 3. Display a PDF

Place a PDF at `app/src/main/assets/books/sample.pdf`. This activity opens it with thumbnails and page numbers enabled:

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

        val design = FlipbookDesign().apply {
            showThumbnails = 1
            showPageNumber = 1
            controlsSize = "lg"
        }
        flipbook.open("books/sample.pdf", design)
    }

    override fun onDestroy() {
        flipbook.release()
        super.onDestroy()
    }
}
```

Declare `ReaderActivity` in your app's manifest using its package-qualified name. To load a remote PDF instead, replace the `open` call with your HTTPS URL:

```kotlin
flipbook.open("https://your-domain.com/books/sample.pdf")
```

The library includes the internet permission. Once a PDF is loaded, you can connect your own buttons to `flipbook.nextPage()`, `flipbook.previousPage()`, `flipbook.goToPage(5)`, and `flipbook.zoomIn()`.

See the [Android documentation](https://github.com/mdakashhossain1/flip-book-plagin/blob/android/README.md) for more options and PDF sources, or the [sample app](https://github.com/mdakashhossain1/flip-book-plagin/tree/android/sample) for an integration example.

## Website

### 1. Download the viewer

Download [`scoreplus-flipbook-website-0.2.0.zip`](https://github.com/mdakashhossain1/flip-book-plagin/releases/download/website-v0.2.0/scoreplus-flipbook-website-0.2.0.zip) from the Website GitHub Release and extract it.

Copy the extracted `index.html` and the entire `assets/` directory into a publicly served folder in your project, such as `public/flipbook/`. Keep the directory structure intact. No npm install or compilation is required.

### 2. Choose a PDF and options

Put your PDF at `assets/books/sample.pdf` inside the viewer folder. Replace the final inline script in the supplied `index.html` with:

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

Keep the existing HTML markup, stylesheet, and script imports in `index.html`; the viewer needs them. You can also pass an HTTPS PDF URL. If the PDF is hosted on another origin, that server must allow CORS requests from your website.

### 3. Embed it in a page

Serve the viewer through your project's HTTP or HTTPS server. If its public URL is `/flipbook/index.html`, add this to your page:

```html
<iframe
  src="/flipbook/index.html"
  title="PDF flipbook"
  style="width: 100%; height: 80vh; border: 0"
  allow="fullscreen"
></iframe>
```

This works in sites that can host static files and embed an iframe. For React JSX, use `style={{ width: "100%", height: "80vh", border: 0 }}` on the iframe. Adjust the `src` if your app uses a different public path.

## Build from source (optional)

For Android development, clone the Android branch:

```sh
git clone --branch android --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-android
cd flipbook-android
```

Open the project in Android Studio and configure the Android SDK. The source project uses `compileSdk 36` and configures Gradle to use JDK 25.

Build on Windows with `.\gradlew.bat :flipbook:assembleRelease` or on macOS/Linux with `bash gradlew :flipbook:assembleRelease`. The output is `flipbook/build/outputs/aar/flipbook-release.aar`.

To verify Maven publication locally, run `.\gradlew.bat :flipbook:publishReleasePublicationToMavenLocal "-Pversion=android-v0.2.1"`. JitPack uses the publication configured on the Android branch to serve the library from each supported Android Git tag. See [JitPack's Android publishing guide](https://docs.jitpack.io/android/) for the publishing workflow.

For website development, clone the static viewer source:

```sh
git clone --branch website --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-website
```

Published releases use platform-specific tags: `android-v0.2.1` and `website-v0.2.0`. Browse [all GitHub Releases](https://github.com/mdakashhossain1/flip-book-plagin/releases) for installation notes and website downloads.

# ScorePlus Flipbook

A PDF flipbook viewer for Android apps and websites, with page-turn animation, zoom, thumbnails, page numbers, and sound controls.

## Choose your version

| Branch | Contents | Use in your project |
| --- | --- | --- |
| [`android`](https://github.com/mdakashhossain1/flip-book-plagin/tree/android) | Native Kotlin library and sample app | Build an AAR and add it to your Android app |
| [`website`](https://github.com/mdakashhossain1/flip-book-plagin/tree/website) | Static HTML, JavaScript, CSS, and assets | Host the viewer and embed it with an iframe |
| `main` | This getting-started guide | Start here and choose a platform |

Each platform branch has its project files at the repository root. The instructions below use the source branches; they do not require a published package or release.

## Android

### 1. Download and build the library

```sh
git clone --branch android --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-android
cd flipbook-android
```

Open the project in Android Studio and configure the Android SDK. The current library uses `compileSdk 36`, requires Android 7.0 / API 24 or later, and configures Gradle to use JDK 25.

Build the release AAR on Windows:

```powershell
.\gradlew.bat :flipbook:assembleRelease
```

On macOS or Linux:

```sh
bash gradlew :flipbook:assembleRelease
```

Output: `flipbook/build/outputs/aar/flipbook-release.aar`.

### 2. Add it to your app

Copy the AAR to your app's `app/libs/` folder. In `app/build.gradle.kts`, add this dependency to your existing dependencies block:

```kotlin
dependencies {
    implementation(files("libs/flipbook-release.aar"))
}
```

Set your app's `minSdk` to at least `24`, then sync Gradle.

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

```sh
git clone --branch website --single-branch https://github.com/mdakashhossain1/flip-book-plagin.git flipbook-website
```

Copy `index.html` and the entire `assets/` directory into a publicly served folder in your project, such as `public/flipbook/`. Keep the directory structure intact. No npm install or compilation is required.

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

## Package your own releases

| Platform | Build or package | File to attach to a GitHub release |
| --- | --- | --- |
| Android | Run `:flipbook:assembleRelease` using the Gradle wrapper on the `android` branch | `flipbook/build/outputs/aar/flipbook-release.aar` |
| Website | Run `git archive --format=zip --output=flipbook-website.zip HEAD` from a checkout of the `website` branch | `flipbook-website.zip` |

Use separate tags targeting the corresponding branches, for example `android-v0.2.0` and `website-v0.1.0`. These are suggested tag names. A downloaded Android AAR can be installed using the steps above; a website ZIP can be extracted and hosted with its assets.

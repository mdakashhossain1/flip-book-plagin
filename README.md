# ScorePlus Flipbook for the web

A static PDF flipbook viewer. This branch contains the web distribution at the repository root.

## Use in a project

1. Copy `index.html` and the entire `assets/` directory into a public directory in your project, for example `public/flipbook/`.
2. Change the PDF path passed to `openFlipbook(...)` at the bottom of `index.html` to your PDF URL. Relative paths resolve from the viewer page. Cross-origin PDFs require the PDF server to allow CORS.
3. Serve the files over HTTP or HTTPS through your application's web server.
4. Open the viewer directly or embed it in an existing page:

```html
<iframe
  src="/flipbook/index.html"
  title="PDF flipbook"
  style="width: 100%; height: 80vh; border: 0"
  allow="fullscreen"
></iframe>
```

Keep the asset directories together so the viewer can load its scripts, styles, PDF worker, images, fonts, and sounds. The included PDF is a demo; replace it for your project.

## Package a release

No compilation or npm install is required. From a checkout of this branch:

```sh
git archive --format=zip --output=flipbook-website.zip HEAD
```

The ZIP contains the viewer and its assets and can be attached to a GitHub release. Use a web-specific tag such as `website-v0.1.0` targeting this branch when creating a release.

## Android version

The native Android library is on the `android` branch of this repository. Its README includes the AAR build instructions.

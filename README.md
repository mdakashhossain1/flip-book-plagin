# ScorePlus Flipbook for the web

[![Latest website release](https://img.shields.io/github/v/release/mdakashhossain1/flip-book-plagin?filter=website-v*&label=release)](https://github.com/mdakashhossain1/flip-book-plagin/releases?q=website-v&expanded=true)
[![CI](https://github.com/mdakashhossain1/flip-book-plagin/actions/workflows/ci.yml/badge.svg?branch=website)](https://github.com/mdakashhossain1/flip-book-plagin/actions/workflows/ci.yml?query=branch%3Awebsite)

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

## Releasing

Releases are automated with [release-please](https://github.com/googleapis/release-please). Nobody edits version numbers by hand.

1. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) format:
   - `fix: …` → patch release (0.2.0 → 0.2.1)
   - `feat: …` → minor release (0.2.x → 0.3.0)
   - `feat!: …` or a `BREAKING CHANGE:` footer → while below 1.0, a minor release; from 1.0, a major release
   - `docs:`, `chore:`, `ci:`, `refactor:`, `test:` → no release
2. On every push to `website`, the **Release** workflow opens or updates a release PR. It bumps `version.txt` and adds the new section to `CHANGELOG.md`.
3. Merge the release PR. The workflow then creates the `website-vX.Y.Z` tag and GitHub Release, and attaches `scoreplus-flipbook-website-X.Y.Z.zip`, built with `git archive`.

The **CI** workflow checks that the scripts parse and that `index.html` only references files that exist, on every push and pull request to `website`.

## Android version

The native Android library is on the `android` branch of this repository. Its README includes the AAR build instructions.

# Known gaps

Every unsupported construct the engine meets is counted in `CoverageReport` and listed
here. Nothing is dropped silently.

## M0

Nothing is implemented yet, so the honest list is: everything. The engine has no
tokenizer, no tree builder, no cascade and no layout. `app-demo` shows a placeholder
screen and says so.

## Standing gaps (by design, not by accident)

- No JavaScript, ever. Script content is inert text.
- No network. The demo has no `INTERNET` permission.
- No platform renderer: no `android.webkit.*`, no `WebView`, no GeckoView.
- No flexbox, grid, floats, positioning, transforms, animations, video, audio, forms,
  `iframe`, SVG or canvas rendering.

# Chess Analyzer — browser extension

Sends **finished** games from Chess.com, Lichess or any web page to your Chess Analyzer.

| Where | What you get |
|---|---|
| Chess.com / Lichess **game pages** | a floating **♞ Analyse** button (bottom-right) |
| **Any** page | select a PGN or FEN → right-click → *Analyse selected PGN/FEN with Chess Analyzer* |
| Links to Chess.com / Lichess games | right-click the link → *Analyse this game with Chess Analyzer* |
| Toolbar icon | *Analyse this page's game*, a paste box, and the **Server** setting |

The extension doesn't call the API itself. It opens `<server>/import#text=<link or PGN>`,
and the website does the rest. The **server** refuses games that are still being
played (fair-play rules), so the button can't be used to cheat in a live game.

## Install (developer mode, no store needed)

**Chrome / Edge / Brave**
1. Open `chrome://extensions` (Edge: `edge://extensions`).
2. Turn on **Developer mode** (top-right).
3. **Load unpacked** → choose this `extension/` folder.
4. Click the puzzle-piece icon → pin **Chess Analyzer**.

**Firefox (desktop)**
1. Open `about:debugging#/runtime/this-firefox`.
2. **Load Temporary Add-on…** → choose `extension/manifest.json`.
   (Temporary add-ons are removed when Firefox restarts. For a permanent install, see "Publishing" below.)

**Set the server once:** click the toolbar icon → **Server** → enter your address
(`http://localhost:8080` while developing, or your deployed `https://…` address) → **Save**.

## Phones

- **Firefox for Android** supports extensions, but only ones **signed by Mozilla**
  (publish it on addons.mozilla.org, see below).
- **Chrome for Android / Safari on iPhone**: no developer extensions. Use the
  website's own phone features instead: on Android, install the site as an app and
  use the **Share** button; on iPhone, use **📋 Paste from clipboard**.
- The Chess.com **phone app** can't run extensions. Use its **Share** button.

## Publishing (optional)

- Zip the folder: `cd extension && zip -r ../chess-analyzer-extension.zip .`
- **Chrome Web Store**: developer account (one-time fee), upload the zip.
- **Firefox Add-ons (AMO)**: free account, upload the zip; once signed it also
  installs on **Firefox for Android**. The `browser_specific_settings.gecko.id` in
  `manifest.json` must be changed to an id you own (e.g. `chess-analyzer@yourname.dev`).

## Files

| File | Role |
|---|---|
| `manifest.json` | Manifest V3: permissions, which sites get the button, background script, popup |
| `common.js` | Server setting + "is this a game page?" (shared) |
| `content.js` / `content.css` | The floating button on Chess.com / Lichess (re-checks the URL every second, since both sites change pages without reloading) |
| `background.js` | Right-click menu entries |
| `popup.html` / `popup.js` / `popup.css` | The toolbar popup |

## Tested

The content script was run on the **live Chess.com page** in real Chrome (through
the DevTools protocol): no button on a profile page, the button appears after
moving to a game without a reload, and clicking it opened `/import`, which
fetched the finished game and showed its analysis.

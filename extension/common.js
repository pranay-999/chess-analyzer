// Shared by the content script, the popup and the background script.
//
// The extension never talks to the API itself. It just opens a tab at
//   <your server>/import#text=<link or PGN>
// and the normal website does the rest (login cookies, CSRF, error messages).
// The "#..." part never leaves the browser, so long PGNs don't end up in server logs.

const DEFAULT_SERVER = 'http://localhost:8080';

/** The Chess Analyzer address saved in the popup (e.g. https://chess-analyzer.onrender.com). */
async function getServer() {
  const { server } = await chrome.storage.sync.get({ server: DEFAULT_SERVER });
  return server.replace(/\/+$/, '');
}

async function importUrl(text) {
  return `${await getServer()}/import#text=${encodeURIComponent(text)}`;
}

/** Game pages we recognise (the server accepts the same links). */
function isGamePage(url) {
  return /chess\.com\/(analysis\/)?(game\/(live|daily)\/|(live|daily)\/game\/|game\/)\d+/.test(url)
    || /lichess\.org\/(?!analysis|training|practice|tutorial|streamer|insights|password|features)[A-Za-z0-9]{8}([A-Za-z0-9]{4})?(\/(white|black))?([#?].*)?$/.test(url);
}

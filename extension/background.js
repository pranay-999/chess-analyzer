// Background script: adds "Analyse with Chess Analyzer" to the right-click menu,
// so it works on ANY website:
//   - selected text (a PGN or a FEN)      -> analyse that text
//   - a link to a Chess.com/Lichess game  -> analyse that game
//   - anywhere on a game page             -> analyse this page's game

// Chrome runs this as a service worker (needs importScripts); Firefox loads it as a
// normal script, where importScripts doesn't exist but common.js isn't needed twice.
if (typeof importScripts === 'function') {
  importScripts('common.js');
}

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'analyse-selection',
    title: 'Analyse selected PGN/FEN with Chess Analyzer',
    contexts: ['selection'],
  });
  chrome.contextMenus.create({
    id: 'analyse-link',
    title: 'Analyse this game with Chess Analyzer',
    contexts: ['link'],
    targetUrlPatterns: ['https://www.chess.com/*', 'https://lichess.org/*'],
  });
  chrome.contextMenus.create({
    id: 'analyse-page',
    title: 'Analyse this game with Chess Analyzer',
    contexts: ['page'],
    documentUrlPatterns: ['https://www.chess.com/*', 'https://lichess.org/*'],
  });
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  const text =
    info.menuItemId === 'analyse-selection' ? info.selectionText
      : info.menuItemId === 'analyse-link' ? info.linkUrl
        : tab?.url;
  if (!text) return;
  chrome.tabs.create({ url: await importUrl(text) });
});

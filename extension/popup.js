// The window that opens when you click the extension's icon in the toolbar.

const $ = (id) => document.getElementById(id);

async function openImport(text) {
  await chrome.tabs.create({ url: await importUrl(text) });
  window.close();
}

(async () => {
  // "activeTab" permission: we may read the current tab's address because YOU clicked our icon.
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const pageUrl = tab?.url ?? '';
  if (isGamePage(pageUrl)) {
    $('page-hint').textContent = 'Game found on this page.';
  } else {
    $('analyse-page').disabled = true;
    $('page-hint').textContent = 'Open a finished game on Chess.com or Lichess to use this button.';
  }

  $('analyse-page').addEventListener('click', () => openImport(pageUrl));
  $('analyse-text').addEventListener('click', () => {
    const text = $('text').value.trim();
    if (text) openImport(text);
  });

  $('server').value = await getServer();
  $('save-server').addEventListener('click', async () => {
    const value = $('server').value.trim();
    if (!/^https?:\/\//.test(value)) {
      $('saved').textContent = 'Start with http:// or https://';
      return;
    }
    await chrome.storage.sync.set({ server: value });
    $('saved').textContent = 'Saved.';
  });
})();

// Runs inside chess.com and lichess.org pages. Shows a small "Analyse" button on
// game pages. Both sites change pages WITHOUT reloading (single-page apps), so we
// re-check the address every second instead of only once.
//
// Fair play: the button sends the game's LINK; the server only imports FINISHED
// games (it checks with Chess.com / Lichess). Clicking during a live game just
// shows "This game is still being played".

(() => {
  let button = null;

  function update() {
    const onGame = isGamePage(location.href);
    if (onGame && !button) {
      button = document.createElement('button');
      button.className = 'chess-analyzer-btn';
      button.type = 'button';
      button.title = 'Open this finished game in Chess Analyzer (Stockfish analysis)';
      button.textContent = '♞ Analyse';
      button.addEventListener('click', async () => {
        window.open(await importUrl(location.href), '_blank', 'noopener');
      });
      document.body.appendChild(button);
    } else if (!onGame && button) {
      button.remove();
      button = null;
    }
  }

  update();
  setInterval(update, 1000);
})();

// Service worker: a small script the browser runs in the background for this site.
// Browsers only offer "Install app" (and, on Android, a place in the Share menu)
// for sites that have one. We don't cache anything: every request still goes to the
// server, so you always see fresh data (this app needs the server anyway).

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
self.addEventListener('fetch', () => {
  // No respondWith(): the browser handles the request normally (straight to the network).
})

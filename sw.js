// Offline support: the whole app and its lessons are cached on first visit.
// Bump VERSION whenever any file below changes so devices pick up the update.
const VERSION = 'speakeasy-v6';
const FILES = [
  './',
  'index.html',
  'styles.css',
  'manifest.webmanifest',
  'icons/icon.svg',
  'icons/apple-touch-icon.png',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'data/content.json',
  'data/pronunciation.json',
  'data/stories.json',
  'data/vocab.json',
  'js/app.js',
  'js/logic.js',
  'js/store.js',
  'js/speech.js',
  'js/ai.js',
  'js/ui.js',
  'js/dict.js',
  'js/news.js',
  'js/wordsheet.js',
  'js/audio.js',
  'js/recorder.js',
  'js/screens/home.js',
  'js/screens/lesson.js',
  'js/screens/games.js',
  'js/screens/review.js',
  'js/screens/lab.js',
  'js/screens/stories.js',
  'js/screens/ai.js',
  'js/screens/progress.js',
  'js/screens/vocab.js',
  'js/screens/news.js',
  'js/screens/shadow.js',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Same-origin files: cache first, then refresh the cache in the background.
// Everything else (Gemini) goes straight to the network.
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  e.respondWith(
    caches.open(VERSION).then(async (cache) => {
      const hit = await cache.match(e.request, { ignoreSearch: true });
      const fresh = fetch(e.request)
        .then((res) => {
          if (res.ok) cache.put(e.request, res.clone());
          return res;
        })
        .catch(() => hit);
      return hit || fresh;
    }),
  );
});

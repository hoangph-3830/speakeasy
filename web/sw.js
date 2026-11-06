// Offline support: the whole app and its lessons are cached on first visit.
// Bump VERSION whenever any file below changes so devices pick up the update.
const VERSION = 'speakeasy-v15';
// Picture-book illustrations come from a CDN; pages read once stay available offline.
const IMAGES = 'speakeasy-images';
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
  'data/picturebooks.json',
  'data/videos.json',
  'js/app.js',
  'js/logic.js',
  'js/store.js',
  'js/speech.js',
  'js/ai.js',
  'js/ui.js',
  'js/dict.js',
  'js/news.js',
  'js/wordsheet.js',
  'js/challenges.js',
  'js/learning-plan.js',
  'js/screens/challenge-words.js',
  'js/screens/placement-test.js',
  'js/screens/study-plan-widgets.js',
  'js/screens/sound-check.js',
  'js/online-voice.js',
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
  'js/screens/books.js',
  'js/screens/videos.js',
  'js/screens/challenge-timed.js',
  'js/screens/challenge-puzzles.js',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION && k !== IMAGES).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Same-origin files: cache first, then refresh the cache in the background.
// Everything else (Gemini) goes straight to the network.
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method === 'GET' && url.hostname === 'cdn.jsdelivr.net' && url.pathname.startsWith('/gh/global-asp/')) {
    e.respondWith(
      caches.open(IMAGES).then(async (cache) => {
        const hit = await cache.match(e.request);
        if (hit) return hit;
        const res = await fetch(e.request);
        if (res.ok || res.type === 'opaque') cache.put(e.request, res.clone());
        return res;
      }),
    );
    return;
  }
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

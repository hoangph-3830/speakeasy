// App shell: hash router, bottom tabs, event delegation and the "now speaking" highlight.
import { inAppBrowser, openInBrowser, speaker } from './speech.js';
import { Progress, onStoreChange } from './store.js';
import { esc } from './ui.js';
import * as home from './screens/home.js';
import * as lesson from './screens/lesson.js';
import * as games from './screens/games.js';
import * as review from './screens/review.js';
import * as lab from './screens/lab.js';
import * as stories from './screens/stories.js';
import * as aiChat from './screens/ai.js';
import * as me from './screens/progress.js';
import * as vocab from './screens/vocab.js';
import * as news from './screens/news.js';
import * as shadow from './screens/shadow.js';
import { buildIndex } from './dict.js';

const TABS = [
  { route: 'home', label: 'Học', icon: '<path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>' },
  { route: 'play', label: 'Chơi', icon: '<path d="M21.6 15.2 20 8.4A4 4 0 0 0 16.1 5H7.9A4 4 0 0 0 4 8.4l-1.6 6.8A2.6 2.6 0 0 0 7 17.6L9 15h6l2 2.6a2.6 2.6 0 0 0 4.6-2.4zM10 11H8v2H7v-2H5v-1h2V8h1v2h2zm5 1a1 1 0 1 1 0-2 1 1 0 0 1 0 2zm2-2a1 1 0 1 1 0-2 1 1 0 0 1 0 2z"/>' },
  { route: 'news', label: 'Đọc báo', icon: '<path d="M20 3H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm-9 14H5v-2h6zm8-4H5v-2h14zm0-4H5V7h14z"/>' },
  { route: 'ai', label: 'Nói với AI', icon: '<path d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zM6 9h12v2H6zm8 5H6v-2h8zm4-6H6V6h12z"/>' },
  { route: 'me', label: 'Tiến độ', icon: '<path d="M21 8a2 2 0 0 0-1.9 2.6l-3.6 3.5a2 2 0 0 0-1 0l-2.5-2.5A2 2 0 1 0 8 11.6l-4.5 4.5A2 2 0 1 0 5 18a2 2 0 0 0-.1-.5l4.5-4.5a2 2 0 0 0 1 0l2.5 2.5A2 2 0 1 0 16.8 16l3.6-3.5A2 2 0 1 0 21 8zM15 9l.9-2L18 6l-2.1-1L15 3l-.9 2-2.1 1 2.1 1zM3.5 11 4 9l2-.5L4 8l-.5-2L3 8l-2 .5L3 9z"/>' },
];

const ROUTES = [
  [/^home$/, home.homeScreen],
  [/^play$/, games.hubScreen],
  [/^ai$/, aiChat.hubScreen],
  [/^me$/, me.progressScreen],
  [/^news$/, news.listScreen],
  [/^article$/, news.articleScreen],
  [/^vocab$/, vocab.hubScreen],
  [/^vocab\/(.+)$/, vocab.groupScreen],
  [/^review$/, review.reviewScreen],
  [/^lab$/, lab.hubScreen],
  [/^lab\/(.+)$/, lab.lessonScreen],
  [/^stories$/, stories.hubScreen],
  [/^story\/(.+)$/, stories.storyScreen],
  [/^ai\/(.+)$/, aiChat.chatScreen],
  [/^topic\/([^/]+)$/, lesson.topicScreen],
  [/^topic\/([^/]+)\/phrases$/, lesson.phrasesScreen],
  [/^topic\/([^/]+)\/dialogue$/, lesson.dialogueScreen],
  [/^topic\/([^/]+)\/speak$/, lesson.speakScreen],
  [/^topic\/([^/]+)\/shadow$/, shadow.shadowScreen],
  [/^game\/([^/]+)\/([^/]+)$/, games.gameScreen],
];

const root = document.getElementById('view');
const tabsEl = document.getElementById('tabs');
let screen = null;
let route = '';
// Our own back stack: on a home-screen app there is no browser back button.
const stack = [];

// no-cache revalidates with the server (a cheap 304 when unchanged), so new lessons show up at once.
const load = (name) => fetch(`data/${name}.json`, { cache: 'no-cache' }).then((r) => r.json());

export const app = {
  content: null,
  pronunciation: null,
  stories: null,
  vocab: null,
  topic: (id) => app.content.topics.find((t) => t.id === id),
  /** The 3000-word list is large, so it loads on first use (and in the background after start). */
  loadVocab() {
    app.vocabLoading ??= load('vocab').then((v) => {
      app.vocab = v;
      buildIndex(v, app.content);
      return v;
    }).catch((e) => {
      app.vocabLoading = null;
      throw e;
    });
    return app.vocabLoading;
  },
  /** The reader needs the whole list item (title, summary, source), so it travels outside the URL. */
  get article() {
    if (app._article) return app._article;
    try {
      return JSON.parse(sessionStorage.getItem('se.article') || 'null');
    } catch {
      return null;
    }
  },
  openArticle(item) {
    try {
      sessionStorage.setItem('se.article', JSON.stringify(item));
    } catch {
      // private mode: the article still opens this time
    }
    app._article = item;
    app.nav('article');
  },
  category: (id) => app.content.categories.find((c) => c.id === id),
  nav: (path) => (location.hash = `#/${path}`),
  back() {
    if (stack.length > 1) history.back();
    else location.replace('#/home');
  },
  /** Re-renders the current screen, keeping each scroll area where it was. */
  update() {
    if (!screen) return;
    const scrolls = [...root.querySelectorAll('[data-scroll]')].map((el) => [el.dataset.scroll, el.scrollTop, el.scrollLeft]);
    root.innerHTML = screen.view();
    scrolls.forEach(([key, top, left]) => {
      const el = root.querySelector(`[data-scroll="${key}"]`);
      if (el) {
        el.scrollTop = top;
        el.scrollLeft = left;
      }
    });
    highlight(speaker.speaking);
    screen.after?.(root);
  },
};

function renderTabs() {
  const visible = TABS.some((t) => t.route === route);
  tabsEl.hidden = !visible;
  if (!visible) return;
  tabsEl.innerHTML = TABS.map(
    (t) => `<button class="tab ${t.route === route ? 'on' : ''}" data-tab="${t.route}">
      <span class="tab-ico"><svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">${t.icon}</svg></span>${esc(t.label)}</button>`,
  ).join('');
}

function open() {
  const path = location.hash.replace(/^#\/?/, '') || 'home';
  if (stack[stack.length - 2] === path) stack.pop();
  else if (stack[stack.length - 1] !== path) stack.push(path);

  screen?.leave?.();
  speaker.stop();
  // Sheets and dialogs belong to the screen being left.
  document.querySelectorAll('.sheet-back, .dialog-back').forEach((el) => el.remove());
  route = path;
  const match = ROUTES.map(([re, make]) => [path.match(re), make]).find(([m]) => m);
  if (!match) return location.replace('#/home');
  const [m, make] = match;
  screen = make(app, ...m.slice(1).map(decodeURIComponent));
  if (!screen) return location.replace('#/home');
  renderTabs();
  root.innerHTML = '';
  app.update();
  screen.enter?.();
}

/** Marks every speaker button (and line) whose text is being read right now. */
function highlight(text) {
  document.querySelectorAll('[data-say],[data-hl]').forEach((el) => {
    el.classList.toggle('speaking', text != null && (el.dataset.say ?? el.dataset.hl) === text);
  });
}

root.addEventListener('click', (e) => {
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled || !screen) return;
  const act = el.dataset.act;
  if (act === 'say') {
    return speaker.say(el.dataset.say, { slow: !!el.dataset.slow, pitch: el.dataset.pitch ? +el.dataset.pitch : 1 });
  }
  if (act === 'back') return screen.actions?.back ? screen.actions.back() : app.back();
  if (act === 'nav') return app.nav(el.dataset.to);
  const fn = screen.actions?.[act];
  if (fn && fn(el.dataset, el, e) !== false) app.update();
});

// Text inputs keep their own value; screens just read it, so typing never re-renders.
root.addEventListener('input', (e) => {
  const el = e.target.closest('[data-input]');
  if (el) screen?.actions?.[el.dataset.input]?.(el.value, el, e);
});
root.addEventListener('keydown', (e) => {
  const el = e.target.closest('[data-enter]');
  if (el && e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    if (screen?.actions?.[el.dataset.enter]?.(el.value, el, e) !== false) app.update();
  }
});

tabsEl.addEventListener('click', (e) => {
  const t = e.target.closest('[data-tab]');
  if (!t || t.dataset.tab === route) return;
  // Tabs replace each other instead of piling up in history.
  stack.length = 0;
  location.replace(`#/${t.dataset.tab}`);
});

// iOS only lets a page speak after a tap; the first tap anywhere unlocks speech.
document.addEventListener('pointerdown', () => speaker.unlock(), { once: true, capture: true });

speaker.onChange(highlight);
onStoreChange(() => screen?.live && app.update());
window.addEventListener('hashchange', open);
window.addEventListener('se-voices', () => route === 'me' && app.update());

/** A thin bar telling Zalo/Facebook users to open the app in a real browser, where speech works. */
function inAppBanner() {
  if (!inAppBrowser || sessionStorage.getItem('se.bannerClosed')) return;
  const bar = document.createElement('div');
  bar.className = 'inapp';
  bar.innerHTML = inAppBrowser.ios
    ? `<span>🔇 Trong ${inAppBrowser.name} không nghe được giọng đọc. Bấm <b>⋯</b> rồi chọn <b>Mở bằng Safari</b>.</span><button data-x="close" aria-label="Đóng">✕</button>`
    : `<span>🔇 Trong ${inAppBrowser.name} không nghe được giọng đọc và micro.</span><button class="btn small-btn" data-x="open">Mở bằng Chrome</button><button data-x="close" aria-label="Đóng">✕</button>`;
  bar.addEventListener('click', (e) => {
    const x = e.target.closest('[data-x]')?.dataset.x;
    if (x === 'open') openInBrowser();
    if (x === 'close') { sessionStorage.setItem('se.bannerClosed', '1'); bar.remove(); }
  });
  document.getElementById('shell').prepend(bar);
}

async function start() {
  inAppBanner();
  try {
    [app.content, app.pronunciation, app.stories] = await Promise.all([load('content'), load('pronunciation'), load('stories')]);
  } catch {
    root.innerHTML = '<div class="empty"><div class="huge">📡</div><p>Không tải được bài học. Mở lại khi có mạng một lần để app lưu offline.</p></div>';
    return;
  }
  const p = Progress.get();
  speaker.rate = p.speechRate;
  speaker.accent = p.accent;
  speaker.voiceName = p.voiceName || '';
  buildIndex(null, app.content);
  open();
  setTimeout(() => app.loadVocab().catch(() => {}), 1500);
}

// Offline cache for the installed app; skipped on localhost so edits show up on reload.
if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

start();

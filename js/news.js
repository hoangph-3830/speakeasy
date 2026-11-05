// Live news for reading practice: BBC News RSS and CNN Lite headlines, plus a reader mode that
// pulls an article's paragraphs on demand. Browsers block cross-site reads of these pages (CORS),
// so requests go through public proxies in turn, or the learner's own proxy if set in Settings.
import { AiSettings } from './store.js';

export const SOURCES = [
  { id: 'bbc', label: 'BBC Top', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/rss.xml' },
  { id: 'bbc-world', label: 'BBC World', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/world/rss.xml' },
  { id: 'bbc-business', label: 'BBC Business', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/business/rss.xml' },
  { id: 'bbc-tech', label: 'BBC Tech', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/technology/rss.xml' },
  { id: 'bbc-health', label: 'BBC Health', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/health/rss.xml' },
  { id: 'bbc-science', label: 'BBC Science', brand: 'BBC News', rss: 'https://feeds.bbci.co.uk/news/science_and_environment/rss.xml' },
  { id: 'cnn', label: 'CNN', brand: 'CNN', html: 'https://lite.cnn.com/' },
];
export const source = (id) => SOURCES.find((s) => s.id === id) || SOURCES[0];

// Jina Reader returns the page HTML with CORS enabled and gets through to BBC, where others time out.
const jina = (u) => ({ url: `https://r.jina.ai/${u}`, headers: { 'X-Return-Format': 'html' } });
const PROXIES = [
  jina,
  (u) => `https://api.allorigins.win/raw?url=${encodeURIComponent(u)}`,
  (u) => `https://cors.eu.org/${u}`,
  (u) => `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(u)}`,
  (u) => `https://api.cors.lol/?url=${encodeURIComponent(u)}`,
];
let workingProxy = 0;

const withTimeout = (ms) => (AbortSignal.timeout ? AbortSignal.timeout(ms) : undefined);

/** The learner's own proxy (e.g. the Cloudflare Worker in web/tools), "{url}" marks where the address goes. */
function customProxy() {
  const p = (AiSettings.get().newsProxy || '').trim();
  if (!p) return null;
  return (u) => (p.includes('{url}') ? p.replace('{url}', encodeURIComponent(u)) : p + encodeURIComponent(u));
}

/** Page text through the first proxy that answers, starting with the one that worked last time. */
async function fetchText(url) {
  const custom = customProxy();
  const order = [...(custom ? [custom] : []), ...PROXIES.slice(workingProxy), ...PROXIES.slice(0, workingProxy)];
  for (const proxy of order) {
    try {
      const target = proxy(url);
      const req = typeof target === 'string' ? { url: target } : target;
      const res = await fetch(req.url, { headers: req.headers, signal: withTimeout(15000) });
      if (!res.ok) continue;
      const text = await res.text();
      if (text.length < 200) continue;
      if (proxy !== custom) workingProxy = PROXIES.indexOf(proxy);
      return text;
    } catch {
      // try the next proxy
    }
  }
  throw new Error('Không tải được tin. Các máy chủ trung gian đang bận, thử lại sau ít phút hoặc đặt proxy riêng trong Cài đặt.');
}

const decode = (s) => {
  const el = document.createElement('textarea');
  el.innerHTML = s;
  return el.value;
};
const clean = (s) => decode(String(s || '').replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();

/** BBC links carry RSS tracking parameters; drop them so the same story has one key. */
const tidyLink = (link) => decode(link).replace(/\?at_medium=RSS.*$/, '');

function parseRss(xml) {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  return [...doc.querySelectorAll('item')].map((it) => ({
    title: clean(it.querySelector('title')?.textContent),
    summary: clean(it.querySelector('description')?.textContent),
    link: tidyLink(it.querySelector('link')?.textContent || ''),
    time: Date.parse(it.querySelector('pubDate')?.textContent || '') || null,
    image: it.getElementsByTagName('media:thumbnail')[0]?.getAttribute('url') || null,
  }));
}

/** rss2json reads RSS feeds with CORS enabled, so BBC lists usually load without a generic proxy. */
async function viaRss2json(rss) {
  const res = await fetch(`https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(rss)}`, { signal: withTimeout(12000) });
  const data = await res.json();
  if (data.status !== 'ok' || !data.items?.length) throw new Error('rss2json');
  return data.items.map((it) => ({
    title: clean(it.title),
    summary: clean(it.description),
    link: tidyLink(it.link),
    // rss2json gives UTC as "YYYY-MM-DD HH:mm:ss".
    time: Date.parse(`${it.pubDate.replace(' ', 'T')}Z`) || null,
    image: it.thumbnail || it.enclosure?.link || null,
  }));
}

export function parseCnnHome(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const seen = new Set();
  return [...doc.querySelectorAll('li.card--lite a')]
    .map((a) => ({ title: clean(a.textContent), summary: '', link: `https://lite.cnn.com${a.getAttribute('href')}`, time: null, image: null }))
    .filter((x) => x.title && !seen.has(x.link) && seen.add(x.link));
}

const cacheKey = (id) => `se.news.${id}`;

/** Last list saved for a source, shown straight away while a fresh one loads. */
export function cached(id) {
  try {
    return JSON.parse(localStorage.getItem(cacheKey(id)) || 'null');
  } catch {
    return null;
  }
}

export async function loadList(id) {
  const s = source(id);
  let items;
  if (s.rss) {
    try {
      items = await viaRss2json(s.rss);
    } catch {
      items = parseRss(await fetchText(s.rss));
    }
  } else {
    items = parseCnnHome(await fetchText(s.html));
  }
  const entry = { at: Date.now(), items: items.slice(0, 40) };
  try {
    localStorage.setItem(cacheKey(id), JSON.stringify(entry));
  } catch {
    // storage full: the list still shows
  }
  return entry;
}

/** Paragraphs of an article page: CNN Lite and BBC markup first, any long <p> otherwise. */
export function extractArticle(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.querySelectorAll('figure, aside, nav, footer, [data-component="links-block"], [data-component="related-content"]').forEach((el) => el.remove());
  // Video placeholders, captions and "related story" headlines are not part of the article.
  const junk = (t) => /video can not be played|^(getty images|watch:|related|read more)/i.test(t) || (t.length < 90 && !/[.!?…"”’)]$/.test(t));
  const pick = (sel) => [...doc.querySelectorAll(sel)].map((p) => clean(p.textContent)).filter((t) => t.length > 25 && !junk(t));
  let paragraphs = pick('p.paragraph--lite');
  if (!paragraphs.length) paragraphs = pick('[data-component="text-block"] p');
  if (!paragraphs.length) paragraphs = pick('article p');
  if (!paragraphs.length) paragraphs = pick('p').filter((t) => t.length > 40);
  const title = clean(doc.querySelector('h1')?.textContent) || clean(doc.querySelector('title')?.textContent);
  return { title, paragraphs: paragraphs.filter((t, i, all) => all.indexOf(t) === i) };
}

const articles = new Map();

export async function loadArticle(link) {
  if (articles.has(link)) return articles.get(link);
  const article = extractArticle(await fetchText(link));
  if (!article.paragraphs.length) throw new Error('Không đọc được nội dung bài này.');
  articles.set(link, article);
  return article;
}

/** "15 phút trước"-style age of a story. */
export function ago(time, now = Date.now()) {
  if (!time) return '';
  const m = Math.max(0, Math.round((now - time) / 60000));
  if (m < 1) return 'vừa xong';
  if (m < 60) return `${m} phút trước`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} giờ trước`;
  return `${Math.round(h / 24)} ngày trước`;
}

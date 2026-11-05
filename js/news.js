// Live news for reading practice: RSS/Atom feeds of well-known English newsrooms, CNN Lite headlines
// and Reuters headlines, plus a reader mode that pulls an article's paragraphs on demand. Browsers
// block cross-site reads of these pages (CORS), so requests go through public proxies in turn, or
// the learner's own proxy if set in Settings.
import { AiSettings } from './store.js';

/**
 * Reuters has no public RSS and blocks automated reading, so its headlines come from Google News.
 * A 3-hour window: over a whole day Google ranks by relevance and the newest story can be hours old.
 */
const googleNews = (query) =>
  `https://news.google.com/rss/search?q=${encodeURIComponent(`${query} when:3h`)}&hl=en-US&gl=US&ceid=US:en`;

const bbc = (path) => `https://feeds.bbci.co.uk/news/${path}rss.xml`;
const guardian = (path) => `https://www.theguardian.com/${path}/rss`;
const npr = (id) => `https://feeds.npr.org/${id}/rss.xml`;
const abc = (id) => `https://www.abc.net.au/news/feed/${id}/rss.xml`;

/** Every source is one section of a publisher (`brand`); the reader picks a brand, then a section. */
export const SOURCES = [
  { id: 'bbc', brand: 'BBC News', label: 'Nổi bật', rss: bbc('') },
  { id: 'bbc-world', brand: 'BBC News', label: 'Thế giới', rss: bbc('world/') },
  { id: 'bbc-business', brand: 'BBC News', label: 'Kinh doanh', rss: bbc('business/') },
  { id: 'bbc-tech', brand: 'BBC News', label: 'Công nghệ', rss: bbc('technology/') },
  { id: 'bbc-health', brand: 'BBC News', label: 'Sức khỏe', rss: bbc('health/') },
  { id: 'bbc-science', brand: 'BBC News', label: 'Khoa học', rss: bbc('science_and_environment/') },
  { id: 'cnn', brand: 'CNN', label: 'Nổi bật', html: 'https://lite.cnn.com/' },
  // Headlines only: the full story is read on reuters.com itself.
  { id: 'reuters', brand: 'Reuters', label: 'Nổi bật', rss: googleNews('site:reuters.com'), headlinesOnly: true },
  { id: 'reuters-world', brand: 'Reuters', label: 'Thế giới', rss: googleNews('site:reuters.com/world'), headlinesOnly: true },
  { id: 'reuters-business', brand: 'Reuters', label: 'Kinh doanh', rss: googleNews('site:reuters.com/business'), headlinesOnly: true },
  { id: 'reuters-markets', brand: 'Reuters', label: 'Thị trường', rss: googleNews('site:reuters.com/markets'), headlinesOnly: true },
  { id: 'guardian-world', brand: 'The Guardian', label: 'Thế giới', rss: guardian('world') },
  { id: 'guardian-business', brand: 'The Guardian', label: 'Kinh doanh', rss: guardian('uk/business') },
  { id: 'guardian-tech', brand: 'The Guardian', label: 'Công nghệ', rss: guardian('uk/technology') },
  { id: 'guardian-environment', brand: 'The Guardian', label: 'Môi trường', rss: guardian('uk/environment') },
  { id: 'guardian-science', brand: 'The Guardian', label: 'Khoa học', rss: guardian('science') },
  { id: 'npr', brand: 'NPR', label: 'Nổi bật', rss: npr(1001) },
  { id: 'npr-world', brand: 'NPR', label: 'Thế giới', rss: npr(1004) },
  { id: 'npr-business', brand: 'NPR', label: 'Kinh doanh', rss: npr(1006) },
  { id: 'npr-tech', brand: 'NPR', label: 'Công nghệ', rss: npr(1019) },
  { id: 'npr-health', brand: 'NPR', label: 'Sức khỏe', rss: npr(1128) },
  { id: 'aljazeera', brand: 'Al Jazeera', label: 'Nổi bật', rss: 'https://www.aljazeera.com/xml/rss/all.xml' },
  { id: 'abc', brand: 'ABC News (Úc)', label: 'Nổi bật', rss: abc(45910) },
  { id: 'abc-latest', brand: 'ABC News (Úc)', label: 'Mới nhất', rss: abc(51120) },
  { id: 'abc-business', brand: 'ABC News (Úc)', label: 'Kinh doanh', rss: abc(51892) },
  // Atom feed with the whole article inside, written by academics and journalists.
  { id: 'conversation', brand: 'The Conversation', label: 'Nổi bật', rss: 'https://theconversation.com/global/articles.atom', fullText: true },
];
export const source = (id) => SOURCES.find((s) => s.id === id) || SOURCES[0];
export const BRANDS = [...new Set(SOURCES.map((s) => s.brand))];
export const sectionsOf = (brand) => SOURCES.filter((s) => s.brand === brand);

// Jina Reader returns the page HTML with CORS enabled and gets through to BBC, where others time out.
// X-No-Cache makes it fetch the page now instead of serving a copy that may be an hour old.
const jina = (u) => ({ url: `https://r.jina.ai/${u}`, headers: { 'X-Return-Format': 'html', 'X-No-Cache': 'true' } });
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

/** Guardian descriptions hold several paragraphs; a list card needs a couple of sentences. */
const shorten = (s, max = 300) => (s.length <= max ? s : `${s.slice(0, max).replace(/\s+\S*$/, '')}…`);

/** BBC links carry RSS tracking parameters; drop them so the same story has one key. */
const tidyLink = (link) => decode(link).replace(/\?at_medium=RSS.*$/, '');

/** RSS <item>s or Atom <entry>s; `content` is the full article HTML when the feed carries it. */
export function parseFeed(xml) {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  const tag = (el, name) => el.getElementsByTagName(name)[0];
  const items = [...doc.querySelectorAll('item')].map((it) => ({
    title: clean(tag(it, 'title')?.textContent),
    summary: clean(tag(it, 'description')?.textContent),
    link: tidyLink(tag(it, 'link')?.textContent || ''),
    time: Date.parse(tag(it, 'pubDate')?.textContent || '') || null,
    image: (tag(it, 'media:thumbnail') || tag(it, 'media:content'))?.getAttribute('url') || null,
    content: tag(it, 'content:encoded')?.textContent || '',
  }));
  const entries = [...doc.querySelectorAll('entry')].map((e) => ({
    title: clean(tag(e, 'title')?.textContent),
    summary: clean(tag(e, 'summary')?.textContent),
    link: ([...e.getElementsByTagName('link')].find((l) => (l.getAttribute('rel') || 'alternate') === 'alternate') || tag(e, 'link'))?.getAttribute('href') || '',
    time: Date.parse(tag(e, 'published')?.textContent || tag(e, 'updated')?.textContent || '') || null,
    image: null,
    content: tag(e, 'content')?.textContent || '',
  }));
  return [...items, ...entries];
}

/** rss2json reads feeds with CORS enabled, so most lists load without a generic proxy. */
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
    content: it.content || '',
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

const articles = new Map();

/** Cleans a freshly loaded list the same way for every feed. */
export function tidyItems(s, raw) {
  let items = raw;
  // Google News appends " - Reuters" to titles and repeats the title as the description.
  if (s.headlinesOnly)
    items = items.map((it) => {
      const title = it.title.replace(/\s+-\s+Reuters$/, '');
      return { ...it, title, summary: it.summary.startsWith(title) ? '' : it.summary, image: null };
    });
  // Video and photo-gallery pages have no article text to read.
  items = items.filter((it) => it.title && it.link && !/\/(video|videos|gallery|galleries|podcasts?|audio)\//.test(it.link));
  return items
    .map((it) => ({ ...it, summary: shorten(it.summary) }))
    .sort((a, b) => (b.time || 0) - (a.time || 0));
}

export async function loadList(id) {
  const s = source(id);
  let items;
  if (s.rss) {
    // The live feed first: rss2json caches feeds for about an hour and returns only 10 items, so it is
    // just the fallback when every proxy is down.
    // Public page readers refuse Google News (Reuters), so it goes through rss2json, about an hour behind.
    const live = async () => {
      const list = parseFeed(await fetchText(s.rss));
      if (!list.length) throw new Error('empty feed');
      return list;
    };
    // With the learner's own proxy, Google News is read live too.
    const [first, second] = s.headlinesOnly && !customProxy() ? [() => viaRss2json(s.rss), live] : [live, () => viaRss2json(s.rss)];
    try {
      items = await first();
    } catch (e) {
      try {
        items = await second();
      } catch {
        throw e;
      }
    }
  } else {
    items = parseCnnHome(await fetchText(s.html));
  }
  items = tidyItems(s, items).slice(0, 40);
  // Feeds that carry the whole article (The Conversation) need no page fetch; the text stays in memory.
  for (const it of items)
    if (s.fullText && it.content) {
      const a = extractArticle(it.content);
      if (a.paragraphs.length) articles.set(it.link, { title: it.title, paragraphs: a.paragraphs });
    }
  const entry = { at: Date.now(), items: items.map(({ content, ...rest }) => rest) };
  try {
    localStorage.setItem(cacheKey(id), JSON.stringify(entry));
  } catch {
    // storage full: the list still shows
  }
  return entry;
}

/** Each newsroom's article body, tried in order before falling back to any long <p>. */
const BODY_SELECTORS = [
  'p.paragraph--lite', // CNN Lite
  '[data-component="text-block"] p', // BBC
  '#storytext p', // NPR
  '.wysiwyg p', // Al Jazeera
  '[data-gu-name="body"] p, .article-body-commercial-selector p', // The Guardian
  'article p',
];

/** Paragraphs of an article page (or of a feed's full-text HTML). */
export function extractArticle(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.querySelectorAll('figure, figcaption, aside, nav, footer, form, [data-component="links-block"], [data-component="related-content"], .byline, [class*="newsletter"], [class*="promo"]').forEach((el) => el.remove());
  // Video placeholders, captions, promos and "related story" headlines are not part of the article.
  const junk = (t) =>
    /video can not be played|^(getty images|watch:|related|read more|sign up|advertisement|support (the|our)|listen to this)/i.test(t) ||
    // Donation asks at the end of reader-funded newsrooms (The Guardian, NPR).
    /fending off legal risks|reader[- ]funded|(support|fund|power)(s|ing)? (our|independent|this) journalism|make a (contribution|donation)|become a (supporter|member)/i.test(t) ||
    (t.length < 90 && !/[.!?…"”’)]$/.test(t));
  const pick = (sel) => [...doc.querySelectorAll(sel)].map((p) => clean(p.textContent)).filter((t) => t.length > 25 && !junk(t));
  let paragraphs = [];
  for (const sel of BODY_SELECTORS) {
    paragraphs = pick(sel);
    if (paragraphs.length >= 2) break;
  }
  if (paragraphs.length < 2) paragraphs = pick('p').filter((t) => t.length > 40);
  const title = clean(doc.querySelector('h1')?.textContent) || clean(doc.querySelector('title')?.textContent);
  return { title, paragraphs: paragraphs.filter((t, i, all) => all.indexOf(t) === i) };
}

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

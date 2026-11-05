// "Đọc báo": live BBC/CNN headlines and a reader where every word can be tapped for its meaning,
// with an optional Vietnamese line under each paragraph.
import { lookup, translate } from '../dict.js';
import { QUEST } from '../logic.js';
import { BRANDS, ago, cached, loadArticle, loadList, sectionsOf, source } from '../news.js';
import { speaker, toast } from '../speech.js';
import { Progress, Review } from '../store.js';
import { accent, bigBtn, esc, sayBtn, toggleChip, topBar } from '../ui.js';
import { openWordSheet, wordCard } from '../wordsheet.js';

const WORD = /([A-Za-z][A-Za-z’'-]*)/;

/**
 * Where the learner stands with a word, LingQ-style: "known" (marked learned), "learning" (in the
 * review deck), "new" (in the dictionary but neither), or "" for names and words not in the list.
 */
// Grammar words ("the", "of", "she", "and") are known long before they are learned on purpose.
const GRAMMAR = new Set(['prep', 'pron', 'det', 'conj', 'num']);

export function wordStatus(raw) {
  const e = lookup(raw);
  if (!e || GRAMMAR.has(e.pos)) return { status: '', entry: null };
  if (Progress.get().learnedWords.includes(e.w)) return { status: 'known', entry: e };
  return { status: Review.has(`v:${e.w}`) ? 'learning' : 'new', entry: e };
}

/** Wraps each English word so a tap can look it up; `colour` adds its status highlight. */
export const tappable = (text, colour = false) =>
  String(text)
    .split(WORD)
    .map((part, i) => {
      if (!(i % 2)) return esc(part);
      const cls = colour ? wordStatus(part).status : '';
      return `<span class="w ${cls}" data-act="word" data-w="${esc(part)}">${esc(part)}</span>`;
    })
    .join('');

/** Distinct dictionary words of a text grouped by status, for the "you know X%" summary. */
export function wordStats(texts) {
  const by = { known: new Map(), learning: new Map(), new: new Map() };
  for (const t of texts)
    for (const part of String(t).split(WORD).filter((_, i) => i % 2)) {
      const { status, entry } = wordStatus(part);
      if (status) by[status].set(entry.w, entry);
    }
  const total = by.known.size + by.learning.size + by.new.size;
  return { ...by, total, percent: total ? Math.round((by.known.size * 100) / total) : 0 };
}

/** Age of the most recent story in a list, e.g. "5 phút trước". */
const newest = (items) => {
  const t = Math.max(0, ...items.map((it) => it.time || 0));
  return t ? ago(t) : '';
};

const clock = (t) => new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });

// The last source read is remembered between visits and app launches.
let lastSource = (() => {
  try {
    return localStorage.getItem('se.newsSource') || 'bbc';
  } catch {
    return 'bbc';
  }
})();
// Translations of headlines, shared between list visits.
const titleVi = new Map();

export function listScreen(app) {
  let id = lastSource;
  let entry = cached(id);
  let loading = false;
  let error = null;
  let bilingual = false;
  let timer = null;
  let alive = true;
  // Set when the source changes, so the chosen chips are scrolled into view once.
  let revealChips = true;

  const onVisible = () => {
    if (document.visibilityState === 'visible' && !loading && (!entry || Date.now() - entry.at > 60 * 1000)) refresh();
  };

  /** Switches to a source: cached headlines at once, fresh ones after. */
  function pick(next) {
    id = lastSource = next;
    try {
      localStorage.setItem('se.newsSource', next);
    } catch {
      // private mode: the choice lasts for this visit
    }
    entry = cached(id);
    revealChips = true;
    refresh();
  }

  async function refresh() {
    loading = true;
    error = null;
    app.update();
    const want = id;
    try {
      const fresh = await loadList(want);
      if (!alive || want !== id) return;
      entry = fresh;
    } catch (e) {
      if (!alive || want !== id) return;
      error = e.message;
    }
    loading = false;
    app.update();
    if (bilingual) translateTitles();
  }

  /** Fills in Vietnamese headlines one at a time, top first, so the visible ones come in quickly. */
  async function translateTitles() {
    for (const it of entry?.items || []) {
      if (!alive || !bilingual) return;
      if (titleVi.has(it.title)) continue;
      try {
        titleVi.set(it.title, await translate(it.title));
        app.update();
      } catch {
        return;
      }
    }
  }

  const card = (it, i) => `
    <button class="card news-card" data-act="open" data-i="${i}">
      ${it.image ? `<img class="news-img" src="${esc(it.image)}" alt="" loading="lazy">` : ''}
      <div class="grow">
        <div class="t-sub">${esc(it.title)}</div>
        ${bilingual ? `<div class="small" style="color:var(--a)">${esc(titleVi.get(it.title) || '…')}</div>` : ''}
        ${it.summary ? `<div class="muted small mt-xs">${esc(it.summary)}</div>` : ''}
        <div class="tiny muted mt-xs">${esc(source(id).brand)}${it.time ? ` · ${ago(it.time)}` : ''}</div>
      </div>
    </button>`;

  return {
    enter() {
      refresh();
      // Near real time: refresh every 2 minutes while the list is open, and as soon as the app comes
      // back to the foreground (phones pause timers in the background).
      timer = setInterval(refresh, 2 * 60 * 1000);
      document.addEventListener('visibilitychange', onVisible);
    },
    leave() {
      alive = false;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    },
    /** Keeps the selected brand and section visible in their rows. */
    after(root) {
      if (!revealChips) return;
      revealChips = false;
      root.querySelectorAll('.chips').forEach((row) => {
        const on = row.querySelector('.on');
        if (!on) return;
        const left = on.offsetLeft - row.offsetLeft;
        if (left < row.scrollLeft || left + on.offsetWidth > row.scrollLeft + row.clientWidth)
          row.scrollLeft = left - (row.clientWidth - on.offsetWidth) / 2;
      });
    },
    view() {
      const items = entry?.items || [];
      return `<div class="screen" ${accent('coral')}>
        <div class="pad-x news-head">
          <div class="row center-v gap-s">
            <h1 class="grow">Đọc báo 📰</h1>
            ${toggleChip(bilingual ? 'Song ngữ ✓' : 'Song ngữ', bilingual, 'bilingual')}
            <button class="chip-toggle" data-act="refresh" ${loading ? 'disabled' : ''}>${loading ? '⏳' : '↻'}</button>
          </div>
          <div class="muted small">Tin mới từ BBC, CNN, Reuters, The Guardian, NPR, Al Jazeera, ABC News & The Conversation. Chạm vào bài để đọc, chạm vào từ để xem nghĩa.</div>
          <div class="chips mt-s" data-scroll="brands">${BRANDS.map((b) => `<button class="cat-chip ${b === source(id).brand ? 'on' : ''}" data-act="brand" data-b="${esc(b)}">${esc(b)}</button>`).join('')}</div>
          ${sectionsOf(source(id).brand).length > 1 ? `<div class="chips sections" data-scroll="sections">${sectionsOf(source(id).brand).map((s) => `<button class="sec-chip ${s.id === id ? 'on' : ''}" data-act="source" data-id="${s.id}">${esc(s.label)}</button>`).join('')}</div>` : ''}
        </div>
        <div class="scroll pad" data-scroll="news-${id}">
          <div class="muted tiny">${loading ? 'Đang cập nhật tin mới nhất…' : entry ? `Cập nhật lúc ${clock(entry.at)}${newest(items) ? ` · tin mới nhất ${newest(items)}` : ''}` : ''}</div>
          ${error ? `<div class="err">😕 ${esc(error)}${entry ? '<br><span class="small">Đang hiện tin đã lưu lần trước.</span>' : ''}<br><button class="link bold" data-act="refresh" style="color:var(--coral)">Thử lại</button></div>` : ''}
          ${!items.length && loading ? '<div class="empty"><div class="huge">📰</div><p class="muted">Đang tải tin mới…</p></div>' : ''}
          <div class="grid2">${items.map(card).join('')}</div>
        </div>
      </div>`;
    },
    actions: {
      brand: (d) => {
        if (d.b === source(id).brand) return false;
        pick(sectionsOf(d.b)[0].id);
        return false;
      },
      source: (d) => {
        pick(d.id);
        return false;
      },
      refresh: () => { refresh(); return false; },
      bilingual: () => {
        bilingual = !bilingual;
        if (bilingual) translateTitles();
      },
      open: (d) => {
        const it = entry.items[+d.i];
        app.openArticle({ ...it, source: id });
        return false;
      },
    },
  };
}

export function articleScreen(app) {
  const it = app.article;
  if (!it) return null;
  const s = source(it.source);
  let article = null;
  let error = null;
  let bilingual = false;
  const vi = new Map();
  let alive = true;
  let readingAll = false;
  let readToken = 0;
  const doneKey = `news:${it.link}`;

  /** Reads the title and every paragraph in turn; the paragraph being read is highlighted. */
  async function readAll() {
    const my = ++readToken;
    readingAll = true;
    app.update();
    for (const text of [article.title || it.title, ...article.paragraphs]) {
      if (!alive || my !== readToken) return;
      const el = [...document.querySelectorAll('[data-hl]')].find((x) => x.dataset.hl === text);
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      await speaker.sayAndWait(text);
      // Something else started speaking (a tapped word, a paragraph's own button): stop here.
      if (speaker.speaking && speaker.speaking !== text) {
        readingAll = false;
        if (alive) app.update();
        return;
      }
    }
    if (my === readToken) { readingAll = false; if (alive) app.update(); }
  }
  const stopReading = () => { readToken++; readingAll = false; speaker.stop(); };

  async function load() {
    // Word colours need the 3000-word list.
    app.loadVocab().then(() => alive && app.update()).catch(() => {});
    if (s.headlinesOnly) return;
    try {
      article = await loadArticle(it.link);
    } catch (e) {
      error = e.message;
    }
    if (alive) app.update();
  }

  async function translateAll() {
    for (const p of article?.paragraphs || []) {
      if (!alive || !bilingual) return;
      if (vi.has(p)) continue;
      try {
        vi.set(p, await translate(p));
        app.update();
      } catch {
        return;
      }
    }
  }

  const para = (p, i) => `
    <div class="para">
      <div class="row top gap-s">
        <p class="grow reading" data-hl="${esc(p)}">${tappable(p, true)}</p>
        ${sayBtn(p, { size: 32 })}
      </div>
      ${bilingual ? `<p class="para-vi">${esc(vi.get(p) || (i === 0 || vi.size ? 'Đang dịch…' : ''))}</p>` : ''}
    </div>`;

  return {
    enter: load,
    leave: () => { alive = false; readToken++; },
    view() {
      const title = article?.title || it.title;
      const done = Progress.get().done.includes(doneKey);
      const stats = app.vocab && (article || s.headlinesOnly) ? wordStats([title, ...(article?.paragraphs || [it.summary])]) : null;
      const summary = stats?.total ? `
        <div class="card known-card">
          <div class="row center-v gap">
            <div class="known-pct">${stats.percent}%</div>
            <div class="grow">
              <div class="t-sub">Bạn đã biết ${stats.percent}% từ trong bài</div>
              <div class="muted small">${stats.new.size} từ mới · ${stats.learning.size} từ đang học · ${stats.known.size} từ đã thuộc</div>
            </div>
          </div>
          <div class="row gap-s wrap legend"><span class="w new">Mới</span><span class="w learning">Đang học</span><span class="w known">Đã thuộc</span></div>
          ${stats.new.size ? `<button class="btn outline" data-act="enrollNew">🔁 Thêm ${stats.new.size} từ mới vào Ôn tập</button>` : ''}
        </div>` : '';
      return `<div class="screen" ${accent('coral')}>
        ${topBar(s.brand, it.time ? ago(it.time) : 'Đọc báo', toggleChip(bilingual ? 'Song ngữ ✓' : 'Song ngữ', bilingual, 'bilingual'))}
        <div class="scroll pad reader" data-scroll="article">
          <h2 class="article-title" data-hl="${esc(title)}">${tappable(title, true)}</h2>
          ${bilingual ? `<p class="para-vi">${esc(titleVi.get(title) || vi.get(title) || '…')}</p>` : ''}
          <div class="row center-v gap-s wrap">
            <div class="muted tiny grow">Chạm vào một từ để xem nghĩa · 🔊 để nghe cả đoạn</div>
            ${article ? `<button class="btn small-btn" data-act="readAll" style="--a:var(--sky)">${readingAll ? '⏸ Dừng đọc' : '▶ Đọc cả bài'}</button>` : ''}
          </div>
          ${s.headlinesOnly ? `<div class="note" style="background:var(--sun-l)">📰 ${esc(s.brand)} chỉ cho đọc toàn bài trên trang của họ. Học từ trong tiêu đề ở đây, rồi bấm nút bên dưới để đọc tiếp.</div>
            ${it.summary ? `<div class="para"><p class="reading">${tappable(it.summary, true)}</p></div>` : ''}
            <a class="btn big" href="${esc(it.link)}" target="_blank" rel="noopener">Đọc toàn bài trên ${esc(s.brand)} ↗</a>` : ''}
          ${!s.headlinesOnly && !article && !error ? '<div class="empty"><div class="huge">⏳</div><p class="muted">Đang tải bài…</p></div>' : ''}
          ${error ? `<div class="err">😕 ${esc(error)}</div>${it.summary ? `<div class="para"><p class="reading">${tappable(it.summary)}</p></div>` : ''}` : ''}
          ${summary}
          ${article ? article.paragraphs.map(para).join('') : ''}
          ${s.headlinesOnly ? '' : `<a class="btn outline" href="${esc(it.link)}" target="_blank" rel="noopener">Mở bài gốc trên ${esc(s.brand)} ↗</a>`}
          <div class="muted tiny center">Nguồn: ${esc(s.brand)}${s.headlinesOnly ? ' (tiêu đề qua Google News)' : '. Bài được tải trực tiếp về máy bạn để đọc, SpeakEasy không lưu lại nội dung'}.</div>
          ${article ? bigBtn(done ? 'Đã đọc xong ✅' : 'Đọc xong ✅ +5 XP', 'finish', { color: 'mint', disabled: done }) : ''}
        </div>
      </div>`;
    },
    actions: {
      word: (d) => { openWordSheet(d.w, app.update); return false; },
      readAll: () => {
        if (readingAll) stopReading();
        else { readAll(); return false; }
      },
      enrollNew: () => {
        const stats = wordStats([article?.title || it.title, ...(article?.paragraphs || [it.summary])]);
        const n = Review.add([...stats.new.values()].map(wordCard));
        toast(`Đã thêm ${n} từ vào Ôn tập hôm nay 🔁`);
      },
      bilingual: () => {
        bilingual = !bilingual;
        speaker.stop();
        if (bilingual) {
          const title = article?.title || it.title;
          if (!titleVi.has(title)) translate(title).then((t) => { vi.set(title, t); app.update(); }).catch(() => {});
          translateAll();
        }
      },
      finish: () => {
        Progress.markDone(doneKey);
        Progress.addXp(5);
        Progress.count(QUEST.LISTEN);
      },
    },
  };
}

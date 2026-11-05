// "Đọc báo": live BBC/CNN headlines and a reader where every word can be tapped for its meaning,
// with an optional Vietnamese line under each paragraph.
import { lookup, translate } from '../dict.js';
import { QUEST } from '../logic.js';
import { SOURCES, ago, cached, loadArticle, loadList, source } from '../news.js';
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

const clock = (t) => new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });

let lastSource = 'bbc';
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
      // "Realtime" enough for headlines: refresh every 5 minutes while the list is open.
      timer = setInterval(refresh, 5 * 60 * 1000);
    },
    leave() {
      alive = false;
      clearInterval(timer);
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
          <div class="muted small">Tin mới từ BBC & CNN. Chạm vào bài để đọc, chạm vào từ để xem nghĩa.</div>
          <div class="chips mt-s">${SOURCES.map((s) => `<button class="cat-chip ${s.id === id ? 'on' : ''}" data-act="source" data-id="${s.id}">${esc(s.label)}</button>`).join('')}</div>
        </div>
        <div class="scroll pad" data-scroll="news-${id}">
          <div class="muted tiny">${loading ? 'Đang cập nhật…' : entry ? `Cập nhật lúc ${clock(entry.at)}` : ''}</div>
          ${error ? `<div class="err">😕 ${esc(error)}${entry ? '<br><span class="small">Đang hiện tin đã lưu lần trước.</span>' : ''}<br><button class="link bold" data-act="refresh" style="color:var(--coral)">Thử lại</button></div>` : ''}
          ${!items.length && loading ? '<div class="empty"><div class="huge">📰</div><p class="muted">Đang tải tin mới…</p></div>' : ''}
          <div class="grid2">${items.map(card).join('')}</div>
        </div>
      </div>`;
    },
    actions: {
      source: (d) => {
        id = lastSource = d.id;
        entry = cached(id);
        refresh();
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
  const doneKey = `news:${it.link}`;

  async function load() {
    // Word colours need the 3000-word list.
    app.loadVocab().then(() => alive && app.update()).catch(() => {});
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
        <p class="grow reading">${tappable(p, true)}</p>
        ${sayBtn(p, { size: 32 })}
      </div>
      ${bilingual ? `<p class="para-vi">${esc(vi.get(p) || (i === 0 || vi.size ? 'Đang dịch…' : ''))}</p>` : ''}
    </div>`;

  return {
    enter: load,
    leave: () => { alive = false; },
    view() {
      const title = article?.title || it.title;
      const done = Progress.get().done.includes(doneKey);
      const stats = article && app.vocab ? wordStats([title, ...article.paragraphs]) : null;
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
          <h2 class="article-title">${tappable(title, true)}</h2>
          ${bilingual ? `<p class="para-vi">${esc(titleVi.get(title) || vi.get(title) || '…')}</p>` : ''}
          <div class="muted tiny">Chạm vào một từ để xem nghĩa · 🔊 để nghe cả đoạn</div>
          ${!article && !error ? '<div class="empty"><div class="huge">⏳</div><p class="muted">Đang tải bài…</p></div>' : ''}
          ${error ? `<div class="err">😕 ${esc(error)}</div>${it.summary ? `<div class="para"><p class="reading">${tappable(it.summary)}</p></div>` : ''}` : ''}
          ${summary}
          ${article ? article.paragraphs.map(para).join('') : ''}
          <a class="btn outline" href="${esc(it.link)}" target="_blank" rel="noopener">Mở bài gốc trên ${esc(s.brand)} ↗</a>
          <div class="muted tiny center">Nguồn: ${esc(s.brand)}. Bài được tải trực tiếp về máy bạn để đọc, SpeakEasy không lưu lại nội dung.</div>
          ${article ? bigBtn(done ? 'Đã đọc xong ✅' : 'Đọc xong ✅ +5 XP', 'finish', { color: 'mint', disabled: done }) : ''}
        </div>
      </div>`;
    },
    actions: {
      word: (d) => { openWordSheet(d.w, app.update); return false; },
      enrollNew: () => {
        const stats = wordStats([article.title || it.title, ...article.paragraphs]);
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

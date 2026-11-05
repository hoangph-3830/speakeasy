// Bilingual picture books from African Storybook / Global Storybooks (CC BY, CC BY-NC): real children's
// stories with their illustrations and a human Vietnamese translation, read one page at a time.
import { LEVELS, QUEST } from '../logic.js';
import { speaker, toast } from '../speech.js';
import { Progress } from '../store.js';
import { bigBtn, esc, pill, sayBtn, sleep, toggleChip, topBar } from '../ui.js';
import { openWordSheet } from '../wordsheet.js';
import { tappable } from './news.js';

/** The library list, shown as the second tab of the stories hub. */
export function libraryView(app) {
  if (!app.books) {
    app.loadBooks().then(app.update).catch(() => toast('Không tải được thư viện truyện. Kiểm tra mạng rồi mở lại.'));
    return '<div class="empty"><div class="huge">🖼️</div><p class="muted">Đang tải thư viện truyện…</p></div>';
  }
  const p = Progress.get();
  const groups = Object.entries(LEVELS)
    .map(([lvl, info]) => [lvl, info, app.books.books.filter((b) => b.level === lvl)])
    .filter(([, , list]) => list.length);
  return `<p class="muted small">Truyện tranh có sẵn từ thư viện mở African Storybook &amp; Global Storybooks, bản dịch tiếng Việt do người dịch thật.</p>
    ${groups.map(([lvl, info, list]) => `
      <div class="section"><h2>${pill(lvl, info.color, `var(--${info.color}-l)`)} ${esc(info.name)}</h2></div>
      <div class="books">${list.map((b) => `
        <button class="book" data-act="nav" data-to="book/${b.id}">
          ${b.cover ? `<img src="${esc(b.cover)}" alt="" loading="lazy">` : '<div class="no-cover">📖</div>'}
          <div class="book-info">
            <div class="semi small">${esc(b.title)}</div>
            <div class="muted tiny">${esc(b.titleVi)}</div>
            <div class="tiny muted">${b.pages.length} trang ${p.done.includes(`book:${b.id}`) ? '· ⭐' : ''}</div>
          </div>
        </button>`).join('')}</div>`).join('')}`;
}

export function bookScreen(app, id) {
  let page = 0;
  let bilingual = true;
  let autoRead = false;
  let token = 0;
  let alive = true;

  const book = () => app.books?.books.find((b) => b.id === id);

  /** Reads the page aloud, then turns to the next one while "Tự đọc" is on. */
  async function readOn() {
    const my = ++token;
    while (autoRead && alive && my === token) {
      const b = book();
      if (page >= b.pages.length) { autoRead = false; break; }
      await speaker.sayAndWait(b.pages[page].en);
      if (!autoRead || my !== token || !alive) return;
      await sleep(700);
      if (!autoRead || my !== token) return;
      page++;
      app.update();
    }
    if (alive) app.update();
  }
  const stopReading = () => { autoRead = false; token++; speaker.stop(); };

  const credits = (b) => {
    const done = Progress.get().done.includes(`book:${b.id}`);
    return `<div class="card credits">
      <div class="huge center">📚</div>
      <div class="t-title center">${esc(b.title)}</div>
      <div class="muted center">${esc(b.titleVi)}</div>
      <div class="small stack">
        ${b.text ? `<div>✍️ Tác giả: ${esc(b.text)}</div>` : ''}
        ${b.illustration ? `<div>🎨 Tranh: ${esc(b.illustration)}</div>` : ''}
        ${b.translation ? `<div>🇻🇳 Dịch: ${esc(b.translation)}</div>` : ''}
        <div>📄 Giấy phép: ${esc(b.license)}</div>
        <div>📖 Nguồn: <a href="https://www.africanstorybook.org" target="_blank" rel="noopener">African Storybook</a> · <a href="https://globalstorybooks.net" target="_blank" rel="noopener">Global Storybooks</a></div>
      </div>
      ${bigBtn(done ? 'Đã đọc xong ✅' : 'Đọc xong ✅ +5 XP', 'finish', { color: 'mint', disabled: done })}
      <button class="btn outline" data-act="restart">↺ Đọc lại từ đầu</button>
    </div>`;
  };

  return {
    enter() {
      if (!app.books) app.loadBooks().then(() => alive && app.update()).catch(() => {});
      // Word colours and offline meanings need the 3000-word list.
      app.loadVocab().then(() => alive && app.update()).catch(() => {});
    },
    leave() { alive = false; token++; },
    /** Swipe left / right on the page to turn it, like a real book. */
    after(root) {
      const el = root.querySelector('.book-page');
      if (!el) return;
      let x0 = null;
      el.addEventListener('touchstart', (e) => { x0 = e.touches[0].clientX; }, { passive: true });
      el.addEventListener('touchend', (e) => {
        if (x0 == null) return;
        const dx = e.changedTouches[0].clientX - x0;
        x0 = null;
        const b = book();
        if (!b || Math.abs(dx) < 60) return;
        stopReading();
        page = dx < 0 ? Math.min(b.pages.length, page + 1) : Math.max(0, page - 1);
        app.update();
      });
    },
    view() {
      const b = book();
      if (!b) return `<div class="screen">${topBar('Truyện tranh')}<div class="empty"><div class="huge">🖼️</div><p class="muted">Đang tải truyện…</p></div></div>`;
      const last = page >= b.pages.length;
      const pg = b.pages[page];
      const trailing = `<div class="row gap-xs">${toggleChip(autoRead ? '⏸ Dừng' : '▶ Tự đọc', autoRead, 'auto')}${toggleChip('Song ngữ', bilingual, 'bilingual')}</div>`;
      return `<div class="screen" style="--a:var(--mint);--al:var(--mint-l)">
        ${topBar(b.title, last ? 'Hết truyện' : `Trang ${page + 1} / ${b.pages.length}`, trailing)}
        <div class="scroll pad book-page" data-scroll="book-${page}">
          ${last ? credits(b) : `
            ${pg.img ? `<img class="page-img" src="${esc(pg.img)}" alt="Tranh trang ${page + 1}">` : ''}
            <div class="row top gap-s">
              <p class="grow page-en reading" data-hl="${esc(pg.en)}">${tappable(pg.en, true)}</p>
              ${sayBtn(pg.en, { size: 40 })}
            </div>
            ${bilingual ? `<p class="para-vi">${esc(pg.vi)}</p>` : ''}`}
        </div>
        <div class="bottom">
          <div class="page-dots">${b.pages.map((_, i) => `<i class="${i === page ? 'on' : i < page ? 'past' : ''}"></i>`).join('')}<i class="${last ? 'on' : ''}"></i></div>
          <div class="row gap">
            <button class="btn outline" data-act="prev" ${page === 0 ? 'disabled' : ''}>← Trước</button>
            ${last ? bigBtn('Về thư viện', 'back', { color: 'mint' }) : bigBtn(page === b.pages.length - 1 ? 'Hết truyện →' : 'Trang sau →', 'next', { color: 'mint' })}
          </div>
        </div>
      </div>`;
    },
    actions: {
      word: (d) => { stopReading(); openWordSheet(d.w, app.update); return false; },
      bilingual: () => { bilingual = !bilingual; },
      auto: () => {
        if (autoRead) stopReading();
        else { autoRead = true; readOn(); return false; }
      },
      prev: () => { stopReading(); page = Math.max(0, page - 1); },
      next: () => { stopReading(); page++; },
      restart: () => { page = 0; },
      finish: () => {
        Progress.markDone(`book:${id}`);
        Progress.addXp(5);
        Progress.count(QUEST.LISTEN);
        toast('Giỏi quá! +5 XP 🎉');
      },
    },
  };
}

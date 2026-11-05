// "Học qua video": recent videos from official English-learning YouTube channels, played in YouTube's
// own embedded player, with the title readable word by word and translatable.
import { translate } from '../dict.js';
import { LEVELS, QUEST } from '../logic.js';
import { toast } from '../speech.js';
import { Progress } from '../store.js';
import { bigBtn, esc, pill, toggleChip, topBar } from '../ui.js';
import { openWordSheet } from '../wordsheet.js';
import { tappable } from './news.js';

const thumb = (id) => `https://i.ytimg.com/vi/${id}/mqdefault.jpg`;
let lastChannel = 0;

function needVideos(app) {
  if (app.videos) return true;
  app.loadVideos().then(app.update).catch(() => toast('Không tải được danh sách video. Kiểm tra mạng rồi mở lại.'));
  return false;
}

const waiting = (title) => `<div class="screen">${topBar(title)}<div class="empty"><div class="huge">🎬</div><p class="muted">Đang tải video…</p></div></div>`;

export function hubScreen(app) {
  let ch = lastChannel;
  let reveal = true;
  return {
    view() {
      if (!needVideos(app)) return waiting('Học qua video');
      const channels = app.videos.channels;
      const c = channels[ch];
      const p = Progress.get();
      const lv = LEVELS[c.level];
      return `<div class="screen" style="--a:var(--coral);--al:var(--coral-l)">
        ${topBar('Học qua video', 'Video từ kênh chính thức trên YouTube')}
        <div class="chips pad-x" data-scroll="channels">${channels.map((x, i) => `<button class="cat-chip ${i === ch ? 'on' : ''}" data-act="channel" data-i="${i}">${x.kids ? '👶 ' : ''}${esc(x.name)}</button>`).join('')}</div>
        <div class="scroll pad" data-scroll="videos-${ch}">
          <div class="row center-v gap-s wrap">${pill(`${c.level} · ${lv.name}`, lv.color, `var(--${lv.color}-l)`)}${c.kids ? pill('Thiếu nhi', 'sun', 'var(--sun-l)') : ''}<span class="muted small">${esc(c.about)}</span></div>
          <div class="videos">${c.videos.map((v) => `
            <button class="video-card" data-act="nav" data-to="video/${v.id}">
              <div class="thumb"><img src="${thumb(v.id)}" alt="" loading="lazy"><span class="play">▶</span>${p.done.includes(`video:${v.id}`) ? '<span class="seen">✅</span>' : ''}</div>
              <div class="semi small clamp2">${esc(v.title)}</div>
            </button>`).join('')}</div>
          <p class="muted tiny center">Video phát bằng trình phát của YouTube; bản quyền thuộc các kênh. Cập nhật danh sách: ${esc(app.videos.updated)}.</p>
        </div>
      </div>`;
    },
    /** The chosen channel stays visible in its chip row. */
    after(root) {
      if (!reveal) return;
      reveal = false;
      const row = root.querySelector('[data-scroll=channels]');
      const on = row?.querySelector('.on');
      if (on) row.scrollLeft = on.offsetLeft - row.offsetLeft - (row.clientWidth - on.offsetWidth) / 2;
    },
    actions: {
      channel: (d) => { ch = lastChannel = +d.i; reveal = true; },
    },
  };
}

export function videoScreen(app, id) {
  let bilingual = false;
  let titleVi = '';
  let alive = true;

  const find = () => {
    for (const c of app.videos?.channels || []) {
      const v = c.videos.find((x) => x.id === id);
      if (v) return { c, v };
    }
    return null;
  };

  return {
    enter() {
      if (!app.videos) app.loadVideos().then(() => alive && app.update()).catch(() => {});
      app.loadVocab().then(() => alive && app.update()).catch(() => {});
    },
    leave() { alive = false; },
    view() {
      if (!needVideos(app)) return waiting('Video');
      const hit = find();
      if (!hit) return waiting('Video');
      const { c, v } = hit;
      const lv = LEVELS[c.level];
      const done = Progress.get().done.includes(`video:${id}`);
      // English captions on by default, interface in Vietnamese; youtube-nocookie avoids tracking cookies until play.
      const src = `https://www.youtube-nocookie.com/embed/${id}?playsinline=1&rel=0&cc_load_policy=1&cc_lang_pref=en&hl=vi`;
      return `<div class="screen" style="--a:var(--coral);--al:var(--coral-l)">
        ${topBar(c.name, `${c.level} · ${lv.name}`, toggleChip('Song ngữ', bilingual, 'bilingual'))}
        <div class="scroll pad video-page" data-scroll="video">
          <div class="player"><iframe src="${src}" title="${esc(v.title)}" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe></div>
          <h2 class="article-title">${tappable(v.title, true)}</h2>
          ${bilingual ? `<p class="para-vi">${esc(titleVi || 'Đang dịch…')}</p>` : ''}
          <div class="note" style="background:var(--sun-l)">💡 Bật phụ đề tiếng Anh (nút CC trong trình phát) để vừa nghe vừa đọc. Chạm vào từ trong tiêu đề để xem nghĩa.</div>
          <div class="row gap">
            <a class="btn outline" href="https://www.youtube.com/watch?v=${id}" target="_blank" rel="noopener">Xem trên YouTube ↗</a>
            ${bigBtn(done ? 'Đã xem ✅' : 'Đã xem ✅ +3 XP', 'watched', { color: 'mint', disabled: done })}
          </div>
          <div class="section"><h2>Video khác của kênh</h2></div>
          <div class="videos">${c.videos.filter((x) => x.id !== id).slice(0, 8).map((x) => `
            <button class="video-card" data-act="nav" data-to="video/${x.id}">
              <div class="thumb"><img src="${thumb(x.id)}" alt="" loading="lazy"><span class="play">▶</span></div>
              <div class="semi small clamp2">${esc(x.title)}</div>
            </button>`).join('')}</div>
        </div>
      </div>`;
    },
    actions: {
      word: (d) => { openWordSheet(d.w, app.update); return false; },
      bilingual: () => {
        bilingual = !bilingual;
        if (bilingual && !titleVi)
          translate(find().v.title).then((t) => { titleVi = t; if (alive) app.update(); }).catch(() => { titleVi = '(Chưa dịch được)'; app.update(); });
      },
      watched: () => {
        Progress.markDone(`video:${id}`);
        Progress.addXp(3);
        Progress.count(QUEST.LISTEN);
      },
    },
  };
}

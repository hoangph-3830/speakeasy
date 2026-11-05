import {
  ACTIVITIES, doneCount, goalProgress, level, questCount, questsToday, speakingScore, topicOfTheDay,
} from '../logic.js';
import { Progress, Review } from '../store.js';
import { badge, bar, catColors, esc, openDialog, pill, ring, section } from '../ui.js';

export function homeScreen(app) {
  let category = null;
  const today = topicOfTheDay(app.content.topics);

  const greeting = (p) => {
    const h = new Date().getHours();
    const hello = h >= 5 && h <= 10 ? 'Good morning' : h >= 11 && h <= 17 ? 'Good afternoon' : 'Good evening';
    return `<div class="row center-v">
      <div class="grow">
        <h1>${esc(hello)}${p.name ? `, ${esc(p.name)}` : ''}! 👋</h1>
        <div class="muted">Hôm nay mình nói tiếng Anh nhé?</div>
      </div>
      <div class="streak">🔥 <b>${p.streak}</b>${p.freezes > 0 ? `<span class="freeze">🧊${p.freezes}</span>` : ''}</div>
    </div>`;
  };

  const goalCard = (p) => `
    <div class="goal-card">
      ${ring(goalProgress(p), 74, '#fff', `<b>${Math.floor(goalProgress(p) * 100)}%</b>`)}
      <div class="grow">
        <div class="o85">Mục tiêu hôm nay</div>
        <div class="t-title">${p.todayXp} / ${p.dailyGoal} XP</div>
        <div class="small">${goalProgress(p) >= 1 ? 'Đạt mục tiêu rồi, giỏi quá! 🎯' : `Cấp ${level(p)} · còn ${100 - (p.xp % 100)} XP để lên cấp`}</div>
      </div>
    </div>`;

  /** Duolingo-style daily quests: finish one, then tap to open its chest. */
  const quests = (p) => `
    <div class="card">
      <div class="row center-v wrap gap-s">
        <div class="t-sub grow">📜 Nhiệm vụ hôm nay</div>
        <div class="muted tiny">🧊 đạt mục tiêu = +1 đóng băng</div>
      </div>
      ${questsToday(p).map((q) => {
        const n = Math.min(questCount(p, q.id), q.target);
        const done = n >= q.target;
        const claimed = p.claimed.includes(q.id);
        return `<div class="quest">
          <span class="emo">${q.emoji}</span>
          <div class="grow"><div class="semi small">${esc(q.title)}</div>${bar(n / q.target, done ? 'mint' : 'sun')}</div>
          ${claimed ? '<span class="emo">✅</span>'
            : done ? `<button class="chest" data-act="claim" data-id="${q.id}">🎁 +${q.reward}</button>`
            : `<span class="muted tiny">${n}/${q.target}</span>`}
        </div>`;
      }).join('')}
    </div>`;

  const reviewCard = () => {
    const cards = Review.all();
    if (!cards.length) return '';
    const due = Review.due().length;
    return `<button class="card tile" style="background:var(--lavender-l)" data-act="nav" data-to="review">
      ${badge('🔁', '#fff', 50)}
      <div class="grow">
        <div class="t-sub">${due > 0 ? `Ôn tập hôm nay: ${due} thẻ` : 'Đã ôn xong hôm nay ✅'}</div>
        <div class="muted small">Bộ nhớ có ${cards.length} thẻ · ôn đúng lúc để nhớ lâu</div>
      </div>
      ${due > 0 ? pill('Ôn ngay', 'white', 'var(--lavender)') : ''}
    </button>`;
  };

  const feature = (emoji, title, sub, bg, to) => `
    <button class="card feature" style="background:var(--${bg})" data-act="nav" data-to="${to}">
      <div class="emo-l">${emoji}</div><div class="t-sub">${esc(title)}</div><div class="muted small">${esc(sub)}</div>
    </button>`;

  const todayCard = (t, p) => {
    const [strong, light] = catColors(t.category);
    return `<button class="card today" style="background:var(--${light})" data-act="nav" data-to="topic/${t.id}">
      <div class="grow">
        ${pill(t.level, strong, '#fff')}
        <div class="t-title mt-s">${esc(t.title)}</div>
        <div class="muted">${esc(t.titleVi)}</div>
        <div class="mt">${bar(doneCount(p, t.id) / ACTIVITIES.length, strong, '#fff')}</div>
      </div>
      <div class="today-emoji">${t.emoji}</div>
    </button>`;
  };

  const topicRow = (t, p) => {
    const [strong, light] = catColors(t.category);
    const done = doneCount(p, t.id);
    return `<button class="card tile" data-act="nav" data-to="topic/${t.id}">
      ${badge(t.emoji, `var(--${light})`, 54)}
      <div class="grow">
        <div class="t-sub">${esc(t.title)}</div>
        <div class="muted small">${esc(t.titleVi)}</div>
        <div class="mt-s">${bar(done / ACTIVITIES.length, strong)}</div>
      </div>
      ${done === ACTIVITIES.length ? '<span class="emo">⭐</span>' : pill(t.level, strong, `var(--${light})`)}
    </button>`;
  };

  const chip = (label, selected, color, id) =>
    `<button class="cat-chip ${selected ? 'on' : ''}" style="--a:var(--${color})" data-act="category" data-id="${id}">${esc(label)}</button>`;

  return {
    view() {
      const p = Progress.get();
      const topics = category ? app.content.topics.filter((t) => t.category === category) : app.content.topics;
      const score = speakingScore(p);
      return `<div class="screen"><div class="scroll pad" data-scroll="home">
        ${greeting(p)}
        ${goalCard(p)}
        ${p.freezeUsedDay === Review.today() ? `<div class="note" style="background:var(--sky-l)">🧊 Hôm qua bạn nghỉ, một lượt đóng băng đã giữ chuỗi ${p.streak} ngày cho bạn!</div>` : ''}
        ${Review.all().length ? `<div class="grid2">${quests(p)}${reviewCard()}</div>` : quests(p)}
        ${section('Luyện nghe nói như người bản xứ')}
        <div class="tiles4">
          ${feature('🎯', 'Phòng phát âm', score != null ? `Điểm phát âm ${score}` : 'Sửa âm người Việt hay sai', 'lavender-l', 'lab')}
          ${feature('📖', 'Truyện nghe', 'Nghe chuyện, trả lời câu hỏi', 'mint-l', 'stories')}
          ${feature('📚', '3000 từ vựng', `${p.learnedWords.length} từ đã thuộc`, 'sky-l', 'vocab')}
          ${feature('📰', 'Đọc báo song ngữ', 'BBC, Guardian, NPR… · chạm từ để dịch', 'peach-l', 'news')}
        </div>
        ${section('Chủ đề hôm nay ✨')}
        ${todayCard(today, p)}
        ${section('Khám phá chủ đề')}
        <div class="chips" data-scroll="cats">
          ${chip('🌈 Tất cả', category == null, 'sky', '')}
          ${app.content.categories.map((c) => chip(`${c.emoji} ${c.name}`, category === c.id, catColors(c.id)[0], c.id)).join('')}
        </div>
        <div class="grid2">${topics.map((t) => topicRow(t, p)).join('')}</div>
      </div></div>`;
    },
    enter() {
      if (!Progress.get().name && !sessionStorage.getItem('se.askedName')) askName(app);
    },
    actions: {
      category: (d) => { category = d.id || null; },
      claim: (d) => { Progress.claim(questsToday(Progress.get()).find((q) => q.id === d.id)); },
    },
  };
}

function askName(app) {
  sessionStorage.setItem('se.askedName', '1');
  openDialog({
    icon: '👋',
    title: 'Chào bạn mới!',
    body: `<p class="muted">Mình nên gọi bạn là gì nhỉ?</p><input class="field" maxlength="20" placeholder="Tên của bạn" autocomplete="given-name">`,
    buttons: [
      { label: 'Để sau' },
      {
        label: 'Bắt đầu học',
        primary: true,
        color: 'mint',
        onClick: (el) => {
          const name = el.querySelector('input').value.trim();
          if (name) Progress.set({ name });
          app.update();
        },
      },
    ],
  });
}

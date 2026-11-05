import {
  ACTIVITIES, LEVELS, doneCount, goalProgress, isDone, level, nextInPath, questCount, questsToday, stageProgress,
} from '../logic.js';
import { Progress, Review } from '../store.js';
import { badge, bar, catColors, esc, openDialog, pill, ring } from '../ui.js';

const LEVEL_ORDER = Object.keys(LEVELS);

// Remembered across visits within the session: which view, which stages are open, quests open.
const ui = { view: 'path', open: null, quests: false };

export function homeScreen(app) {
  let category = null;
  const path = app.content.path || [];

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
    <div class="goal-card compact">
      ${ring(goalProgress(p), 56, '#fff', `<b class="small">${Math.floor(goalProgress(p) * 100)}%</b>`)}
      <div class="grow">
        <div class="t-sub">Hôm nay ${p.todayXp} / ${p.dailyGoal} XP</div>
        <div class="small o85">${goalProgress(p) >= 1 ? 'Đạt mục tiêu rồi, giỏi quá! 🎯' : `Cấp ${level(p)} · còn ${100 - (p.xp % 100)} XP để lên cấp`}</div>
      </div>
    </div>`;

  /** The next unfinished topic along the path, so learners never wonder what to do next. */
  const continueCard = (p) => {
    const next = nextInPath(path, app.content.topics, p);
    if (!next)
      return `<div class="card next-card"><div class="t-sub">Bạn đã học hết lộ trình 🎉</div>
        <div class="muted small">Ôn lại thẻ ghi nhớ và đọc báo mỗi ngày để giữ phong độ.</div>
        <button class="btn" data-act="nav" data-to="review">🔁 Ôn tập</button></div>`;
    const { stage, stageNo, topic } = next;
    const step = ACTIVITIES.findIndex((a) => !isDone(p, topic.id, a.id));
    const lv = LEVELS[stage.level] || LEVELS.A2;
    return `<button class="card next-card" data-act="nav" data-to="topic/${topic.id}" style="--a:var(--${lv.color});--al:var(--${lv.color}-l)">
      <div class="tiny bold" style="color:var(--a)">HỌC TIẾP · CHẶNG ${stageNo} · ${esc(stage.level)} ${esc(stage.title.toUpperCase())}</div>
      <div class="row center-v gap">
        ${badge(topic.emoji, 'var(--al)', 52)}
        <div class="grow">
          <div class="t-sub">${esc(topic.title)}</div>
          <div class="muted small">${esc(topic.titleVi)} · Bước ${step + 1}: ${esc(ACTIVITIES[step].label)}</div>
        </div>
        <span class="go">Học tiếp →</span>
      </div>
    </button>`;
  };

  const shortcuts = () => {
    const due = Review.due().length;
    const tile = (emoji, label, to, extra = '') =>
      `<button class="shortcut" data-act="nav" data-to="${to}"><span class="emo">${emoji}</span>${extra}<span>${label}</span></button>`;
    return `<div class="shortcuts">
      ${tile('🔁', 'Ôn tập', 'review', due ? `<i class="due">${due > 99 ? '99+' : due}</i>` : '')}
      ${tile('📚', 'Từ vựng', 'vocab')}
      ${tile('🎯', 'Phát âm', 'lab')}
      ${tile('📖', 'Truyện', 'stories')}
      ${tile('📰', 'Đọc báo', 'news')}
    </div>`;
  };

  /** Daily quests folded into one line; tap to see them. */
  const quests = (p) => {
    const list = questsToday(p);
    const done = list.filter((q) => questCount(p, q.id) >= q.target).length;
    const claimable = list.some((q) => questCount(p, q.id) >= q.target && !p.claimed.includes(q.id));
    return `<div class="card quests ${ui.quests ? 'open' : ''}">
      <button class="row center-v gap-s quest-head" data-act="quests">
        <span class="grow t-sub">📜 Nhiệm vụ hôm nay · ${done}/${list.length}</span>
        ${claimable ? '<span class="chest-dot">🎁</span>' : ''}
        <span class="muted">${ui.quests ? '▴' : '▾'}</span>
      </button>
      ${ui.quests ? `${list.map((q) => {
        const n = Math.min(questCount(p, q.id), q.target);
        const claimed = p.claimed.includes(q.id);
        return `<div class="quest">
          <span class="emo">${q.emoji}</span>
          <div class="grow"><div class="semi small">${esc(q.title)}</div>${bar(n / q.target, n >= q.target ? 'mint' : 'sun')}</div>
          ${claimed ? '<span class="emo">✅</span>'
            : n >= q.target ? `<button class="chest" data-act="claim" data-id="${q.id}">🎁 +${q.reward}</button>`
            : `<span class="muted tiny">${n}/${q.target}</span>`}
        </div>`;
      }).join('')}<div class="muted tiny">🧊 Đạt mục tiêu ngày = +1 lượt đóng băng chuỗi</div>` : ''}
    </div>`;
  };

  const dots = (p, id) => `<span class="dots">${ACTIVITIES.map((a) => `<i class="${isDone(p, id, a.id) ? 'on' : ''}"></i>`).join('')}</span>`;

  const topicRow = (t, p) => {
    const [, light] = catColors(t.category);
    const complete = doneCount(p, t.id) === ACTIVITIES.length;
    return `<button class="path-topic" data-act="nav" data-to="topic/${t.id}">
      ${badge(t.emoji, `var(--${light})`, 42)}
      <div class="grow"><div class="semi">${esc(t.title)}</div><div class="muted tiny">${esc(t.titleVi)}</div></div>
      ${complete ? '<span class="check">✓</span>' : dots(p, t.id)}
    </button>`;
  };

  /** Stages from easy to hard; the one in progress is open, the others fold away. */
  const pathView = (p) => {
    const current = path.findIndex((st) => stageProgress(st, p).topicsDone < st.topics.length);
    if (ui.open == null) ui.open = new Set([Math.max(0, current)]);
    return path.map((st, i) => {
      const prog = stageProgress(st, p);
      const lv = LEVELS[st.level] || LEVELS.A2;
      const open = ui.open.has(i);
      const finished = prog.topicsDone === st.topics.length;
      const topics = st.topics.map((id) => app.topic(id)).filter(Boolean);
      return `<div class="card stage ${open ? 'open' : ''} ${i === current ? 'current' : ''}" style="--a:var(--${lv.color});--al:var(--${lv.color}-l)">
        <button class="stage-head" data-act="stage" data-i="${i}">
          <div class="stage-no">${i + 1}</div>
          <div class="grow">
            <div class="row center-v gap-s wrap">${pill(`${st.level} · ${lv.name}`, lv.color, `var(--${lv.color}-l)`)}<span class="t-sub">${esc(st.title)}</span></div>
            <div class="muted tiny mt-xs">${esc(st.subtitle)}</div>
            <div class="row center-v gap-s mt-xs"><div class="grow">${bar(prog.done / prog.total, lv.color)}</div>
              <span class="tiny muted">${finished ? '✅ Hoàn thành' : `${prog.topicsDone}/${st.topics.length} chủ đề`}</span></div>
          </div>
          <span class="muted">${open ? '▴' : '▾'}</span>
        </button>
        ${open ? `<div class="stage-topics">${topics.map((t) => topicRow(t, p)).join('')}</div>` : ''}
      </div>`;
    }).join('');
  };

  /** The previous browse-by-category list, with each category's topics from easy to hard. */
  const categoryView = (p) => {
    const chip = (label, selected, color, id) =>
      `<button class="cat-chip ${selected ? 'on' : ''}" style="--a:var(--${color})" data-act="category" data-id="${id}">${esc(label)}</button>`;
    const topics = (category ? app.content.topics.filter((t) => t.category === category) : app.content.topics)
      .slice().sort((a, b) => LEVEL_ORDER.indexOf(a.level) - LEVEL_ORDER.indexOf(b.level));
    return `<div class="chips" data-scroll="cats">
        ${chip('🌈 Tất cả', category == null, 'sky', '')}
        ${app.content.categories.map((c) => chip(`${c.emoji} ${c.name}`, category === c.id, catColors(c.id)[0], c.id)).join('')}
      </div>
      <div class="grid2">${topics.map((t) => {
        const [strong, light] = catColors(t.category);
        const done = doneCount(p, t.id);
        return `<button class="card tile" data-act="nav" data-to="topic/${t.id}">
          ${badge(t.emoji, `var(--${light})`, 50)}
          <div class="grow"><div class="t-sub">${esc(t.title)}</div><div class="muted small">${esc(t.titleVi)}</div><div class="mt-s">${bar(done / ACTIVITIES.length, strong)}</div></div>
          ${done === ACTIVITIES.length ? '<span class="emo">⭐</span>' : pill(t.level, strong, `var(--${light})`)}
        </button>`;
      }).join('')}</div>`;
  };

  return {
    view() {
      const p = Progress.get();
      return `<div class="screen"><div class="scroll pad" data-scroll="home">
        ${greeting(p)}
        ${goalCard(p)}
        ${p.freezeUsedDay === Review.today() ? `<div class="note" style="background:var(--sky-l)">🧊 Hôm qua bạn nghỉ, một lượt đóng băng đã giữ chuỗi ${p.streak} ngày cho bạn!</div>` : ''}
        ${continueCard(p)}
        ${shortcuts()}
        ${quests(p)}
        <div class="seg home-seg" style="--al:var(--sky-l);--a:var(--sky)">
          <button class="${ui.view === 'path' ? 'on' : ''}" data-act="view" data-v="path">🧭 Lộ trình</button>
          <button class="${ui.view === 'topics' ? 'on' : ''}" data-act="view" data-v="topics">🗂️ Theo chủ đề</button>
        </div>
        ${ui.view === 'path' ? pathView(p) : categoryView(p)}
      </div></div>`;
    },
    enter() {
      if (!Progress.get().name && !sessionStorage.getItem('se.askedName')) askName(app);
    },
    actions: {
      category: (d) => { category = d.id || null; },
      claim: (d) => { Progress.claim(questsToday(Progress.get()).find((q) => q.id === d.id)); },
      quests: () => { ui.quests = !ui.quests; },
      view: (d) => { ui.view = d.v; },
      stage: (d) => {
        const i = +d.i;
        if (ui.open.has(i)) ui.open.delete(i);
        else ui.open.add(i);
      },
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

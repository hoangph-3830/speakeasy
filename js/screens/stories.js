// Duolingo-style listening stories: lines read in character voices, with questions along the way.
import { QUEST } from '../logic.js';
import { speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, badge, bar, bigBtn, catColors, completeDialog, esc, sayBtn, topBar } from '../ui.js';

const pitchOf = (story, name) => story.characters.find((c) => c.name === name)?.pitch ?? 1;

export function hubScreen(app) {
  return {
    view() {
      const p = Progress.get();
      return `<div class="screen">
        ${topBar('Truyện nghe', 'Nghe chuyện đời thường, trả lời câu hỏi')}
        <div class="scroll pad" data-scroll="stories"><div class="grid2">
        ${app.stories.stories.map((s) => {
          const [strong, light] = catColors(s.category);
          return `<button class="card tile" data-act="nav" data-to="story/${s.id}">
            ${badge(s.emoji, `var(--${light})`, 60)}
            <div class="grow">
              <div class="t-sub">${esc(s.title)}</div>
              <div class="muted">${esc(s.titleVi)}</div>
              <div class="tiny bold" style="color:var(--${strong})">${esc(s.characters.filter((c) => c.name !== 'Narrator').map((c) => c.name).join(' · '))}</div>
            </div>
            <span style="color:var(--${strong});font-size:20px">${p.done.includes(`story:${s.id}`) ? '⭐' : '▶'}</span>
          </button>`;
        }).join('')}
        </div></div>
      </div>`;
    },
  };
}

export function storyScreen(app, id) {
  const story = app.stories.stories.find((s) => s.id === id);
  if (!story) return null;
  const [strong, light] = catColors(story.category);
  let pos = 0;
  const answers = {};
  const revealed = new Set();
  let listenOnly = false;

  // Each new line is read in its character's voice.
  const play = () => {
    const item = story.items[pos];
    requestAnimationFrame(() => document.querySelector(`[data-item="${pos}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    if (item.type !== 'question') speaker.say(item.en, { pitch: pitchOf(story, item.speaker) });
  };

  const correctCount = () => Object.entries(answers).filter(([i, a]) => story.items[i].answer === a).length;

  const line = (item, i) => {
    const narrator = item.speaker === 'Narrator';
    const hidden = listenOnly && !revealed.has(i);
    return `<div data-item="${i}" class="story-line">
      ${narrator ? '' : `<div class="tiny bold" style="color:var(--a)">${esc(item.speaker)}</div>`}
      <div class="${narrator ? 'narration' : 'bubble'} row center-v gap-s" data-hl="${esc(item.en)}">
        <div class="grow">${hidden ? `<button class="link muted" data-act="reveal" data-i="${i}">🎧 • • •  chạm để xem chữ</button>`
          : `<div class="${narrator ? 'italic' : ''}">${esc(item.en)}</div><div class="muted small">${esc(item.vi)}</div>`}</div>
        ${sayBtn(item.en, { size: 38, pitch: pitchOf(story, item.speaker) })}
      </div>
    </div>`;
  };

  const question = (item, i) => {
    const picked = answers[i];
    return `<div data-item="${i}" class="question">
      <div class="bold">❓ ${esc(item.q)}</div>
      ${item.options.map((o, k) => {
        const state = picked == null ? '' : k === item.answer ? 'right' : k === picked ? 'wrong' : '';
        return `<button class="option small-opt ${state}" data-act="answer" data-i="${i}" data-k="${k}" ${picked != null ? 'disabled' : ''}>${esc(o)}</button>`;
      }).join('')}
      ${picked != null ? `<div class="semi" style="color:var(--${picked === item.answer ? 'mint' : 'coral'})">${picked === item.answer ? '🎉 Chính xác!' : '😅 Chưa đúng, đáp án được tô xanh.'}</div>` : ''}
    </div>`;
  };

  return {
    enter: play,
    view() {
      const current = story.items[pos];
      const waiting = current.type === 'question' && answers[pos] == null;
      const last = pos === story.items.length - 1;
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar(story.title, story.titleVi, `<button class="chip-toggle ${listenOnly ? 'on' : ''}" data-act="listenOnly">${listenOnly ? '🎧 ✓' : '🎧'}</button>`)}
        <div class="pad-x">${bar((pos + 1) / story.items.length)}</div>
        ${listenOnly ? '<p class="muted pad-x small">Chế độ chỉ nghe: chữ bị ẩn, chạm vào câu để xem.</p>' : ''}
        <div class="scroll pad chat" data-scroll="story">
          ${story.items.slice(0, pos + 1).map((it, i) => (it.type === 'question' ? question(it, i) : line(it, i))).join('')}
        </div>
        <div class="bottom">${bigBtn(waiting ? 'Chọn một đáp án' : last ? 'Hoàn thành 🎉' : 'Tiếp tục', 'advance', { disabled: waiting, color: last ? 'mint' : undefined })}</div>
      </div>`;
    },
    actions: {
      listenOnly: () => { listenOnly = !listenOnly; },
      reveal: (d) => { revealed.add(+d.i); },
      answer: (d) => { if (answers[+d.i] == null) answers[+d.i] = +d.k; },
      advance: () => {
        if (pos < story.items.length - 1) {
          pos++;
          app.update();
          play();
          return false;
        }
        Progress.count(QUEST.LISTEN);
        Progress.markDone(`story:${story.id}`);
        const xp = 10 + correctCount() * 2;
        Progress.addXp(xp);
        completeDialog({
          title: 'Hết truyện rồi!',
          message: `Bạn trả lời đúng ${correctCount()}/${story.items.filter((x) => x.type === 'question').length} câu hỏi.`,
          xp,
          onDismiss: app.back,
        });
      },
    },
  };
}

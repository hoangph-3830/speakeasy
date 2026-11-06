// Timed vocabulary challenges: "Đua 60 giây" (pick the meaning) and "Đúng hay Sai" (judge a meaning).
import {
  SPEED_PENALTY, SPEED_SECONDS, TRUEFALSE_SECONDS, challenge, speedPoints, speedRound, trueFalseRound,
} from '../challenges.js';
import { QUEST } from '../logic.js';
import { speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, esc, openDialog, topBar } from '../ui.js';

/** Result dialog shared by every challenge: score, record, XP, play again. */
export function showResult(app, id, { score, label, xp, restart }) {
  const c = challenge(id);
  const best = Progress.get().gameBest?.[id];
  const record = Progress.recordGameBest(id, score, c.lowerIsBetter);
  if (xp > 0) Progress.addXp(xp);
  Progress.count(QUEST.LISTEN);
  openDialog({
    icon: record ? '🏆' : c.emoji,
    title: record ? 'Kỷ lục mới!' : 'Hết giờ!',
    body: `<p class="h-md">${esc(label)}</p>
      <p class="muted">${best != null && !record ? `Kỷ lục của bạn: ${best}` : record && best != null ? `Kỷ lục cũ: ${best}` : 'Lần đầu chơi, đây là kỷ lục của bạn!'}</p>
      ${xp > 0 ? `<div class="center"><span class="pill" style="color:var(--xp-ink);background:var(--sun-soft)">+${xp} XP</span></div>` : ''}`,
    buttons: [
      { label: 'Về', onClick: () => app.back() },
      { label: 'Chơi lại', primary: true, onClick: restart },
    ],
  });
}

/** One timer loop that writes the bar straight into the page, so the screen isn't redrawn 10× a second. */
function countdown(seconds, onEnd) {
  const t = { endAt: Date.now() + seconds * 1000, total: seconds * 1000, id: null };
  t.id = setInterval(() => {
    const left = Math.max(0, t.endAt - Date.now());
    const bar = document.querySelector('.timebar i');
    if (bar) bar.style.width = `${(left / t.total) * 100}%`;
    const label = document.querySelector('.time-left');
    if (label) label.textContent = `${Math.ceil(left / 1000)} s`;
    if (left <= 0) { clearInterval(t.id); onEnd(); }
  }, 100);
  return t;
}

const timeBar = (t) => `<div class="row center-v gap-s pad-x"><div class="timebar grow"><i style="width:${Math.max(0, ((t.endAt - Date.now()) / t.total) * 100)}%"></i></div><b class="time-left">${Math.ceil(Math.max(0, t.endAt - Date.now()) / 1000)} s</b></div>`;

export function speedScreen(app) {
  let round, score, streak, picked, timer, over;
  const groups = () => app.vocab.groups;

  function next() {
    picked = null;
    round = speedRound(groups());
    speaker.say(round.word.w);
  }
  function start() {
    score = 0; streak = 0; over = false;
    clearInterval(timer?.id);
    next();
    timer = countdown(SPEED_SECONDS, () => {
      over = true;
      app.update();
      showResult(app, 'speed', { score, label: `${score} điểm`, xp: Math.floor(score / 2), restart: () => { start(); app.update(); } });
    });
  }

  return {
    enter() { app.loadVocab().then(() => { start(); app.update(); }); },
    leave() { clearInterval(timer?.id); },
    view() {
      if (!round) return `<div class="screen">${topBar('Đua 60 giây')}<div class="empty"><div class="huge">⚡</div><p class="muted">Đang chuẩn bị…</p></div></div>`;
      return `<div class="screen" ${accent('sun')}>
        ${topBar('⚡ Đua 60 giây', `Điểm: ${score}`, streak >= 5 ? `<b class="combo">🔥 x2</b>` : '')}
        ${timeBar(timer)}
        <div class="scroll pad center">
          <div class="prompt"><div class="word-big">${esc(round.word.w)}</div><div class="muted">${esc(round.word.ipa)}</div></div>
          <div class="stack">${round.options.map((o, i) => {
            const state = picked == null ? '' : o === round.answer ? 'right' : i === picked ? 'wrong' : '';
            return `<button class="option ${state}" data-act="pick" data-i="${i}" ${picked != null || over ? 'disabled' : ''}>${esc(o)}</button>`;
          }).join('')}</div>
          <p class="muted small">Chuỗi đúng: ${streak} · Sai bị trừ ${SPEED_PENALTY} giây</p>
        </div>
      </div>`;
    },
    actions: {
      pick: (d) => {
        if (over || picked != null) return false;
        if (round.options[+d.i] === round.answer) {
          streak++;
          score += speedPoints(streak);
          next();
          return;
        }
        // A wrong pick costs time and shows the right meaning for a moment.
        picked = +d.i;
        streak = 0;
        timer.endAt -= SPEED_PENALTY * 1000;
        setTimeout(() => { if (!over) { next(); app.update(); } }, 600);
      },
    },
  };
}

export function trueFalseScreen(app) {
  let round, score, reveal, timer, over;

  function next() {
    reveal = false;
    round = trueFalseRound(app.vocab.groups);
    speaker.say(round.word.w);
  }
  function start() {
    score = 0; over = false;
    clearInterval(timer?.id);
    next();
    timer = countdown(TRUEFALSE_SECONDS, () => {
      over = true;
      app.update();
      showResult(app, 'truefalse', { score, label: `${score} câu đúng`, xp: Math.floor(score / 2), restart: () => { start(); app.update(); } });
    });
  }

  return {
    enter() { app.loadVocab().then(() => { start(); app.update(); }); },
    leave() { clearInterval(timer?.id); },
    view() {
      if (!round) return `<div class="screen">${topBar('Đúng hay Sai')}<div class="empty"><div class="huge">✅</div><p class="muted">Đang chuẩn bị…</p></div></div>`;
      return `<div class="screen" ${accent('mint')}>
        ${topBar('✅ Đúng hay Sai', `Đúng: ${score}`)}
        ${timeBar(timer)}
        <div class="scroll pad center">
          <div class="prompt"><div class="word-big">${esc(round.word.w)}</div><div class="muted">${esc(round.word.ipa)}</div></div>
          <div class="tf-meaning">= ${esc(round.shown)} ?</div>
          ${reveal ? `<div class="answer bad"><div class="answer-title">😅 Chưa đúng</div><div>${esc(round.word.w)} nghĩa là: <b>${esc(round.word.vi)}</b></div></div>` : ''}
          <div class="row gap">
            <button class="btn big tf" data-act="answer" data-v="1" style="--a:var(--mint)" ${reveal || over ? 'disabled' : ''}>✅ Đúng</button>
            <button class="btn big tf" data-act="answer" data-v="0" style="--a:var(--coral)" ${reveal || over ? 'disabled' : ''}>❌ Sai</button>
          </div>
        </div>
      </div>`;
    },
    actions: {
      answer: (d) => {
        if (over || reveal) return false;
        if ((d.v === '1') === round.correct) { score++; next(); return; }
        reveal = true;
        setTimeout(() => { if (!over) { next(); app.update(); } }, 900);
      },
    },
  };
}

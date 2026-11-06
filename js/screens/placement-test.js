// "Kiểm tra trình độ": a short adaptive quiz from A1 up that stops at the first level the learner
// cannot pass, then starts "continue learning" at the next level.
import { LEVEL_ORDER, PLACEMENT_PER_LEVEL, levelFailed, placementQuiz, placementResult, studyLevel } from '../learning-plan.js';
import { LEVELS } from '../logic.js';
import { speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, bar, bigBtn, esc, pill, topBar } from '../ui.js';

export function placementScreen(app) {
  let quiz, index, answers, picked, finished;

  function start() {
    quiz = placementQuiz(app.content.topics);
    index = 0; answers = []; picked = null; finished = false;
  }
  start();

  function finish() {
    finished = true;
    Progress.set({ placedLevel: placementResult(answers) });
    Progress.addXp(10);
  }

  const resultView = () => {
    const placed = Progress.get().placedLevel;
    const next = studyLevel(placed);
    const lv = LEVELS[next];
    return `<div class="scroll pad center">
      <div class="huge">${placed ? '🎓' : '🌱'}</div>
      <h2>${placed ? `Trình độ của bạn: ${esc(placed)} · ${esc(LEVELS[placed].name)}` : 'Mình bắt đầu từ nền tảng nhé!'}</h2>
      <p class="muted">Đúng ${answers.filter((a) => a.ok).length}/${answers.length} câu. ${placed
        ? `Lộ trình sẽ đưa bạn thẳng tới các bài ${esc(next)}, bỏ qua phần bạn đã biết. Bài dễ hơn vẫn mở để ôn bất cứ lúc nào.`
        : 'Lộ trình bắt đầu từ cấp A1, từng bước một.'}</p>
      <div>${pill(`Học tiếp: ${next} · ${lv.name}`, lv.color, `var(--${lv.color}-l)`)}</div>
      <div class="mt">${bigBtn('Bắt đầu học →', 'home', { color: 'mint' })}</div>
      <button class="link mt" data-act="again">Làm lại bài kiểm tra</button>
    </div>`;
  };

  const questionView = () => {
    const q = quiz[index];
    const lv = LEVELS[q.level];
    return `<div class="scroll pad">
      <div class="row center-v gap-s">${pill(`${q.level} · ${lv.name}`, lv.color, `var(--${lv.color}-l)`)}<span class="muted small">Câu này nghĩa là gì?</span></div>
      <div class="prompt">${esc(q.prompt)}</div>
      <div class="stack">${q.options.map((o, i) => {
        const state = picked == null ? '' : o === q.answer ? 'right' : i === picked ? 'wrong' : '';
        return `<button class="option ${state}" data-act="pick" data-i="${i}" ${picked != null ? 'disabled' : ''}>${esc(o)}</button>`;
      }).join('')}</div>
      ${picked != null ? bigBtn('Tiếp tục', 'next') : '<button class="link mt" data-act="dontKnow">🤷 Mình chưa biết câu này</button>'}
    </div>`;
  };

  return {
    view: () => `<div class="screen" ${accent('sky')}>
      ${topBar('🧭 Kiểm tra trình độ', finished ? 'Kết quả' : `Tối đa ${quiz.length} câu · dừng khi gặp cấp khó`)}
      ${finished ? '' : `<div class="pad-x">${bar(index / quiz.length)}</div>`}
      ${finished ? resultView() : questionView()}
    </div>`,
    actions: {
      pick: (d) => {
        if (picked != null) return false;
        picked = +d.i;
        const q = quiz[index];
        answers.push({ level: q.level, ok: q.options[picked] === q.answer });
        speaker.say(q.prompt);
      },
      dontKnow: () => {
        answers.push({ level: quiz[index].level, ok: false });
        picked = -1;
      },
      next: () => {
        const q = quiz[index];
        const levelDone = answers.filter((a) => a.level === q.level).length === PLACEMENT_PER_LEVEL;
        const last = q.level === LEVEL_ORDER[LEVEL_ORDER.length - 1] && levelDone;
        picked = null;
        if (levelFailed(answers, q.level) || last || index === quiz.length - 1) { finish(); return; }
        index++;
      },
      again: () => { start(); },
      home: () => { app.nav('home'); return false; },
    },
  };
}

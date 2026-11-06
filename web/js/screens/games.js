import { CHALLENGES } from '../challenges.js';
import { GAME_MODES, QUEST, buildGame, compare, gameMode, shuffled } from '../logic.js';
import { speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, badge, bar, bigBtn, catColors, completeDialog, esc, sayBtn, scored, sleep, topBar } from '../ui.js';

export function hubScreen() {
  return {
    view: () => `<div class="screen"><div class="scroll pad" data-scroll="play">
      <h1>Vừa chơi vừa học 🎮</h1>
      <p class="muted">Ôn lại từ vựng & mẫu câu của tất cả chủ đề.</p>
      <div class="section"><h2>🎯 Thử thách từ vựng</h2></div>
      <div class="grid2">${CHALLENGES.map((c) => {
        const best = Progress.get().gameBest?.[c.id];
        return `<button class="card tile challenge" data-act="nav" data-to="challenge/${c.id}">
          ${badge(c.emoji, 'var(--sun-l)', 56)}
          <div class="grow"><div class="t-sub">${esc(c.title)}</div><div class="muted small">${esc(c.about)}</div>
          ${best != null ? `<div class="tiny bold" style="color:var(--peach)">🏆 Kỷ lục: ${best}${c.lowerIsBetter ? ' lượt' : ''}</div>` : ''}</div>
        </button>`;
      }).join('')}</div>
      <div class="section"><h2>🧩 Trò chơi theo chủ đề</h2></div>
      <div class="row gap">
        <button class="card quick" style="background:var(--lavender-l)" data-act="nav" data-to="lab">🎯 Phòng phát âm</button>
        <button class="card quick" style="background:var(--mint-l)" data-act="nav" data-to="stories">📖 Truyện nghe</button>
      </div>
      <div class="grid2">
      ${GAME_MODES.map((m) => `
        <button class="card tile" data-act="nav" data-to="game/all/${m.id}">
          ${badge(m.emoji, `var(--${m.color}-l)`, 58)}
          <div class="grow"><div class="t-sub">${esc(m.title)}</div><div class="muted small">${esc(m.description)}</div></div>
          <span style="color:var(--${m.color});font-size:20px">▶</span>
        </button>`).join('')}
      </div>
    </div></div>`,
  };
}

export function gameScreen(app, topicId, modeId) {
  const topic = app.topic(topicId);
  const mode = gameMode(modeId);
  const [strong, light] = topic ? catColors(topic.category) : [mode.color, `${mode.color}-l`];
  let rounds, index, correct, combo, r;

  // Per-round state, reset by next().
  let picked, solved, selLeft, selRight, mistakes, wrong, placed, checked, typed, showHint, result, left, right;

  function startRound() {
    r = rounds[index];
    picked = null; checked = null; typed = ''; showHint = false; result = null;
    solved = []; selLeft = null; selRight = null; mistakes = 0; wrong = null; placed = [];
    if (r.type === 'match') {
      left = shuffled(r.pairs.map((p) => p[0]));
      right = shuffled(r.pairs.map((p) => p[1]));
    }
    const audio = r.type === 'listen' ? r.audio : r.type === 'dictation' ? r.sentence : r.type === 'meaning' && r.speakPrompt ? r.prompt : null;
    if (audio) sleep(300).then(() => r === rounds[index] && speaker.say(audio));
  }

  function newSession() {
    rounds = buildGame(topic ? [topic] : app.content.topics, mode.id);
    index = 0; correct = 0; combo = 0;
    startRound();
  }
  newSession();

  function answered(ok) {
    if (r.type === 'listen' || r.type === 'dictation') Progress.count(QUEST.LISTEN);
    if (ok) { correct++; combo++; } else combo = 0;
    if (index < rounds.length - 1) {
      index++;
      startRound();
      return;
    }
    let xp;
    if (topic) xp = Progress.complete(topic.id, 'GAME', Math.floor(correct / 2));
    else { xp = correct; Progress.addXp(xp); }
    completeDialog({
      title: correct === rounds.length ? 'Hoàn hảo! 🏆' : correct * 2 >= rounds.length ? 'Làm tốt lắm!' : 'Cố lên nhé!',
      message: `Bạn trả lời đúng ${correct}/${rounds.length} vòng.`,
      xp,
      onDismiss: app.back,
      onAgain: () => { newSession(); app.update(); },
    });
  }

  /** Feedback banner + continue button shown after answering a question. */
  const feedback = (ok, answer) => `
    <div class="answer ${ok ? 'ok' : 'bad'}">
      <div class="answer-title">${ok ? '🎉 Chính xác!' : '😅 Chưa đúng rồi'}</div>
      ${!ok && answer ? `<div>Đáp án: ${esc(answer)}</div>` : ''}
      ${bigBtn('Tiếp tục', 'continue', { color: ok ? 'mint' : 'coral', data: `data-ok="${ok ? 1 : 0}"` })}
    </div>`;

  function choiceView(title, prompt, audio, options, answer) {
    return `<h3>${esc(title)}</h3>
      ${prompt ? `<div class="prompt">${esc(prompt)}</div>` : ''}
      ${audio ? `<div class="row center-h gap-l">${sayBtn(audio, { size: 72 })}${sayBtn(audio, { size: 72, slow: true })}</div>` : ''}
      <div class="stack">
      ${options.map((o, i) => {
        const state = picked == null ? '' : o === answer ? 'right' : o === picked ? 'wrong' : '';
        return `<button class="option ${state}" data-act="pick" data-i="${i}" ${picked != null ? 'disabled' : ''}>${esc(o)}</button>`;
      }).join('')}
      </div>
      ${picked != null ? feedback(picked === answer, answer) : ''}`;
  }

  function matchView() {
    const tile = (text, side, isSolved, sel, isWrong) =>
      `<button class="match ${isSolved ? 'solved' : isWrong ? 'wrong shake' : sel ? 'sel' : ''}" data-act="match" data-side="${side}" data-v="${esc(text)}" ${isSolved ? 'disabled' : ''}>${esc(text)}</button>`;
    return `<h3>Ghép từ với nghĩa đúng</h3>
      <p class="muted">Chạm một từ bên trái rồi chọn nghĩa bên phải.</p>
      <div class="row gap top">
        <div class="stack grow">${left.map((w) => tile(w, 'l', solved.includes(w), selLeft === w, wrong?.[0] === w)).join('')}</div>
        <div class="stack grow">${right.map((m) => tile(m, 'r', r.pairs.some((p) => p[1] === m && solved.includes(p[0])), selRight === m, wrong?.[1] === m)).join('')}</div>
      </div>
      ${solved.length === r.pairs.length
        ? feedback(mistakes <= 1) + (mistakes > 1 ? `<p class="muted">Bạn ghép sai ${mistakes} lần — vòng sau cẩn thận hơn nhé!</p>` : '')
        : ''}`;
  }

  function scrambleView() {
    return `<h3>Xếp thành câu hoàn chỉnh</h3>
      <p class="muted">💬 ${esc(r.hint)}</p>
      <div class="tray">${placed.map((i) => `<button class="word filled" data-act="unplace" data-i="${i}">${esc(r.tiles[i])}</button>`).join('')}</div>
      <div class="tiles">${r.tiles.map((w, i) => (placed.includes(i) ? '' : `<button class="word" data-act="place" data-i="${i}">${esc(w)}</button>`)).join('')}</div>
      ${checked == null ? bigBtn('Kiểm tra', 'checkScramble', { disabled: placed.length !== r.tiles.length }) : feedback(checked, r.sentence)}`;
  }

  /** The sentence is only heard, never shown, until the learner submits what they typed. */
  function dictationView() {
    return `<h3>Nghe và chép lại</h3>
      <p class="muted">Nghe bao nhiêu lần cũng được. Dấu câu và viết hoa không tính.</p>
      <div class="row center-h gap-l">${sayBtn(r.sentence, { size: 72 })}${sayBtn(r.sentence, { size: 72, slow: true })}</div>
      ${showHint ? `<p class="muted">💬 ${esc(r.hint)}</p>` : '<button class="link bold" data-act="hint" style="color:var(--a)">💡 Xem gợi ý nghĩa</button>'}
      <textarea class="field" rows="2" data-input="typed" placeholder="Gõ câu bạn nghe được…" autocapitalize="off" autocorrect="off" spellcheck="false" ${result ? 'readonly' : ''}>${esc(typed)}</textarea>
      ${result
        ? `<div class="card"><div class="muted tiny">Câu gốc · đúng ${result.score}%</div>${scored(result)}</div>${feedback(result.score >= 80)}`
        : bigBtn('Kiểm tra', 'checkDictation', { disabled: !typed.trim() })}`;
  }

  const roundView = () =>
    ({
      match: matchView,
      scramble: scrambleView,
      dictation: dictationView,
      listen: () => choiceView('Nghe và chọn câu đúng', null, r.audio, r.options, r.answer),
      meaning: () => choiceView(r.speakPrompt ? 'Câu này nghĩa là gì?' : 'Nói câu này bằng tiếng Anh thế nào?', r.prompt, r.speakPrompt ? r.prompt : null, r.options, r.answer),
    })[r.type]();

  return {
    view: () => `<div class="screen" ${accent(strong, light)}>
      ${topBar(mode.title, topic?.title || 'Tất cả chủ đề', combo >= 2 ? `<b class="combo">🔥 x${combo}</b>` : '')}
      <div class="pad-x">${bar(index / rounds.length)}<div class="muted small mt-xs">Vòng ${index + 1}/${rounds.length} · đúng ${correct}</div></div>
      <div class="scroll pad round" data-scroll="round-${index}">${roundView()}</div>
    </div>`,
    actions: {
      continue: (d) => answered(d.ok === '1'),
      pick: (d) => {
        picked = r.options[+d.i];
        const speakAnswer = r.type === 'listen' || (r.type === 'meaning' && !r.speakPrompt);
        if (picked === r.answer && speakAnswer) speaker.say(r.answer);
      },
      match: (d) => {
        if (d.side === 'l') selLeft = d.v; else selRight = d.v;
        if (selLeft == null || selRight == null) return;
        if (r.pairs.some((p) => p[0] === selLeft && p[1] === selRight)) {
          solved.push(selLeft);
          speaker.say(selLeft);
        } else {
          mistakes++;
          wrong = [selLeft, selRight];
          const w = wrong;
          setTimeout(() => { if (wrong === w) { wrong = null; app.update(); } }, 450);
        }
        selLeft = selRight = null;
      },
      place: (d) => { if (checked == null) placed.push(+d.i); },
      unplace: (d) => { if (checked == null) placed = placed.filter((i) => i !== +d.i); },
      checkScramble: () => {
        // Joins tiles the same way the sentence was split, so a correct order compares equal.
        checked = placed.map((i) => r.tiles[i]).join(' ') === r.sentence;
        speaker.say(r.sentence);
      },
      hint: () => { showHint = true; },
      typed: (v, el) => {
        typed = v;
        const btn = el.closest('.round').querySelector('[data-act=checkDictation]');
        if (btn) btn.disabled = !v.trim();
      },
      checkDictation: () => { result = compare(r.sentence, typed); },
    },
  };
}

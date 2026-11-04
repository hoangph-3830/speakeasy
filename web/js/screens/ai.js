// Talk with Gemini: free chat or role-play, every learner line corrected, optional hands-free call.
import { AiError, reply } from '../ai.js';
import { AI_LEVELS, QUEST, START_CUE, scenarios } from '../logic.js';
import { listen, speaker } from '../speech.js';
import { AiSettings, Progress } from '../store.js';
import { badge, catColors, esc, micBtn, sayBtn, section, toggleChip, topBar } from '../ui.js';

/** Picks who the AI should be and how hard it should talk. */
export function hubScreen(app) {
  const list = scenarios(app.content);
  return {
    view() {
      const { level, apiKey } = AiSettings.get();
      return `<div class="screen"><div class="scroll pad" data-scroll="ai">
        <h1>Trò chuyện với AI 🤖</h1>
        <p class="muted">Nói gì cũng được. AI đáp như người bản xứ và sửa từng câu cho bạn.</p>
        ${apiKey ? '' : `<button class="note" style="background:var(--sun-l)" data-act="nav" data-to="me">🔑 Cần nhập Gemini API key (miễn phí) trong tab Tiến độ › Cài đặt trước khi trò chuyện. Chạm để mở.</button>`}
        <div class="card">
          <div class="semi">Độ khó khi AI nói</div>
          <div class="row gap-s mt-s">${AI_LEVELS.map((l) => `<button class="filter grow ${level === l.id ? 'on' : ''}" data-act="level" data-id="${l.id}">${esc(l.label)}</button>`).join('')}</div>
        </div>
        ${section('Chọn tình huống')}
        <div class="grid2">
        ${list.map((s) => {
          const t = app.topic(s.id.replace(/^topic:/, ''));
          const [strong, light] = t ? catColors(t.category) : ['sun', 'sun-l'];
          return `<button class="card tile" data-act="nav" data-to="ai/${encodeURIComponent(s.id)}">
            ${badge(s.emoji, `var(--${light})`, 50)}
            <div class="grow"><div class="t-sub">${esc(s.title)}</div><div class="muted small">AI đóng vai: ${esc(s.aiRole)}</div></div>
            <span style="color:var(--${strong})">▶</span>
          </button>`;
        }).join('')}
        </div>
        <p class="muted small center">Câu bạn nói được gửi tới Google Gemini để AI trả lời. Các phần học khác vẫn chạy offline.</p>
      </div></div>`;
    },
    actions: {
      level: (d) => { AiSettings.set({ level: d.id }); },
    },
  };
}

export function chatScreen(app, scenarioId) {
  const scenario = scenarios(app.content).find((s) => s.id === scenarioId);
  if (!scenario) return null;
  const turns = [{ fromUser: true, text: START_CUE, hidden: true }];
  let loading = false;
  let error = null;
  let input = '';
  let showVi = false;
  // Voice call (Duolingo Max style): hands-free, the mic opens by itself after the AI speaks.
  let callMode = false;
  let showCallText = false;
  let alive = true;

  const scrollDown = () =>
    requestAnimationFrame(() => { const el = document.querySelector('[data-scroll=chat]'); if (el) el.scrollTop = el.scrollHeight; });

  async function ask() {
    loading = true;
    error = null;
    app.update();
    scrollDown();
    try {
      const level = AI_LEVELS.find((l) => l.id === AiSettings.get().level) || AI_LEVELS[1];
      const r = await reply(scenario, level, turns);
      if (!alive) return;
      // Attach the correction to the learner line it was about.
      let lastUser = turns.length - 1;
      while (lastUser >= 0 && !turns[lastUser].fromUser) lastUser--;
      if (lastUser >= 0 && !turns[lastUser].hidden) turns[lastUser] = { ...turns[lastUser], correction: r.correction };
      turns.push({ fromUser: false, text: r.reply, raw: JSON.stringify(r), reply: r });
      loading = false;
      showCallText = false;
      app.update();
      scrollDown();
      if (callMode) {
        await speaker.sayAndWait(r.reply);
        if (alive && callMode) listenNow();
      } else if (AiSettings.get().autoSpeak) speaker.say(r.reply);
    } catch (e) {
      if (!alive) return;
      error = e instanceof AiError ? e.message : 'Có lỗi xảy ra. Thử lại nhé.';
      loading = false;
      app.update();
      scrollDown();
    }
  }

  function send(text) {
    const t = text.trim();
    if (!t || loading) return;
    turns.push({ fromUser: true, text: t });
    input = '';
    Progress.addXp(2);
    Progress.count(QUEST.SPEAK);
    ask();
  }

  // Cancelling the recognizer pauses the call; the mic button resumes it.
  const listenNow = () => listen(callMode ? 'Đến lượt bạn nói' : 'Nói câu trả lời bằng tiếng Anh', { onResult: (alts) => send(alts[0]) });

  const aiBubble = (t, vi) => `
    <div class="bubble-row"><div class="bubble" data-hl="${esc(t.text)}">
      <div class="row center-v gap-s"><div class="grow">${esc(t.text)}</div>${sayBtn(t.text, { size: 34 })}</div>
      ${vi && t.reply?.reply_vi ? `<div class="muted small mt-xs">${esc(t.reply.reply_vi)}</div>` : ''}
    </div></div>`;

  const correction = (c, title) => `
    <div class="fix">
      <div class="tiny bold" style="color:var(--mint)">${title}</div>
      <div class="row center-v gap-s mt-xs"><div class="grow semi">${esc(c.corrected)}</div>${sayBtn(c.corrected, { size: 32 })}</div>
      ${c.explanation_vi ? `<div class="muted small mt-xs">💡 ${esc(c.explanation_vi)}</div>` : ''}
    </div>`;

  const learnerBubble = (t) => `
    <div class="bubble-row mine">
      <div class="bubble mine-bg">${esc(t.text)}</div>
      ${t.correction ? (t.correction.has_issue ? correction(t.correction, '✨ Người bản xứ sẽ nói:') : '<div class="tiny semi" style="color:var(--mint)">✅ Tự nhiên lắm!</div>') : ''}
    </div>`;

  const errorBox = () => (error ? `<div class="err">😕 ${esc(error)}<br><button class="link bold" data-act="retry" style="color:var(--coral)">Thử lại</button></div>` : '');

  /** Hands-free call screen: big avatar and no text unless asked, so the learner has to listen. */
  function callView() {
    const lastAi = [...turns].reverse().find((t) => !t.fromUser);
    const lastLearner = [...turns].reverse().find((t) => t.fromUser && !t.hidden);
    const talking = speaker.speaking != null && speaker.speaking === lastAi?.text;
    const c = lastLearner?.correction;
    return `<div class="call">
      <div class="avatar ${talking ? 'pulse' : ''}" data-hl="${esc(lastAi?.text || '')}">${scenario.emoji}</div>
      <div class="h-sm center">${esc(scenario.aiRole.split(',')[0])}</div>
      <div class="muted">${error ? 'Mất kết nối' : loading ? 'Đang nghĩ…' : talking ? 'Đang nói… 🎧' : 'Đến lượt bạn 🎤'}</div>
      <div class="call-body">
        ${errorBox()}
        ${lastAi ? (showCallText ? aiBubble(lastAi, true) : '<button class="link" data-act="callText" style="color:var(--sky)">👀 Không nghe kịp? Xem chữ</button>') : ''}
        ${c?.has_issue ? correction(c, '✨ Câu vừa rồi, người bản xứ sẽ nói:') : ''}
      </div>
      <div class="row center-h gap-xl">${micBtn('mic', 72)}<button class="mic hang" data-act="hangUp" style="--s:72px">📵</button></div>
      <div class="muted tiny mt-s">🎤 Nói    ·    📵 Kết thúc gọi</div>
    </div>`;
  }

  return {
    enter() {
      // Keep the call status ("Đang nói…" / "Đến lượt bạn") in step with the voice.
      speaker.onChange(() => alive && callMode && app.update());
      ask();
    },
    leave: () => { alive = false; },
    view() {
      const { autoSpeak } = AiSettings.get();
      const trailing = `<div class="row gap-xs">
        ${toggleChip('📞', callMode, 'call')}
        ${callMode ? '' : toggleChip('🔊', autoSpeak, 'autoSpeak') + toggleChip('VI', showVi, 'vi')}
      </div>`;
      const head = topBar(scenario.title, `AI đóng vai: ${scenario.aiRole}`, trailing);
      if (callMode) return `<div class="screen" style="--a:var(--sky);--al:var(--sky-l)">${head}${callView()}</div>`;
      const lastAi = [...turns].reverse().find((t) => !t.fromUser);
      const suggestions = !loading ? lastAi?.reply?.suggestions || [] : [];
      return `<div class="screen" style="--a:var(--sky);--al:var(--sky-l)">${head}
        <div class="scroll pad-x chat" data-scroll="chat">
          ${turns.filter((t) => !t.hidden).map((t) => (t.fromUser ? learnerBubble(t) : aiBubble(t, showVi))).join('')}
          ${loading ? '<div class="bubble typing">• • •</div>' : ''}
          ${errorBox()}
        </div>
        ${suggestions.length ? `<div class="suggest"><span>💡</span>${suggestions.map((s, i) => `<button class="sugg" data-act="suggest" data-i="${i}">${esc(s)}</button>`).join('')}</div>` : ''}
        <div class="composer">
          ${micBtn('mic', 52)}
          <textarea class="field grow" rows="1" data-input="input" data-enter="send" placeholder="Hoặc gõ tiếng Anh…" enterkeyhint="send">${esc(input)}</textarea>
          <button class="send" data-act="send" ${!input.trim() || loading ? 'disabled' : ''} aria-label="Gửi">
            <svg viewBox="0 0 24 24" width="22" height="22"><path d="M2 21 23 12 2 3v7l15 2-15 2z" fill="currentColor"/></svg>
          </button>
        </div>
      </div>`;
    },
    actions: {
      call: () => {
        callMode = !callMode;
        // Only open the mic straight away if the AI has had its turn.
        if (callMode && !loading && !error && !turns[turns.length - 1].fromUser) setTimeout(listenNow, 50);
        if (!callMode) speaker.stop();
      },
      autoSpeak: () => { AiSettings.set({ autoSpeak: !AiSettings.get().autoSpeak }); },
      vi: () => { showVi = !showVi; },
      callText: () => { showCallText = true; },
      hangUp: () => { callMode = false; speaker.stop(); },
      retry: () => { ask(); return false; },
      mic: () => { listenNow(); return false; },
      // Hear it, then say it yourself: the hint fills the box rather than sending.
      suggest: (d) => {
        const lastAi = [...turns].reverse().find((t) => !t.fromUser);
        input = lastAi.reply.suggestions[+d.i];
        speaker.say(input);
      },
      input: (v, el) => {
        input = v;
        const btn = el.parentElement.querySelector('.send');
        if (btn) btn.disabled = !v.trim() || loading;
      },
      send: () => { send(input); return false; },
    },
  };
}

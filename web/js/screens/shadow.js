// Shadowing (luyện nhại): hear a native sentence, record yourself, then compare the two by ear,
// with your loudness and pitch drawn so rhythm and end-of-sentence intonation are visible.
import { intonationHint, modelSeconds, pitchTrend, speedFeedback } from '../audio.js';
import { QUEST, isUserLine } from '../logic.js';
import { canRecord, play, startRecording } from '../recorder.js';
import { speaker, toast } from '../speech.js';
import { Progress } from '../store.js';
import { accent, bar, bigBtn, catColors, completeDialog, esc, outBtn, sayBtn, sleep, topBar } from '../ui.js';

const RATINGS = [[1, '😕 Chưa giống'], [2, '🙂 Khá giống'], [3, '😎 Rất giống']];

export function shadowScreen(app, id) {
  const topic = app.topic(id);
  if (!topic) return null;
  const [strong, light] = catColors(topic.category);
  const seen = new Set();
  const sentences = [
    ...topic.phrases.map((p) => [p.en, p.vi]),
    ...(topic.native || []).map((n) => [n.natural, n.vi]),
    ...topic.dialogue.lines.filter(isUserLine).map((l) => [l.en, l.vi]),
  ].filter(([en]) => (seen.has(en) ? false : seen.add(en)));

  let index = 0;
  let looping = false;
  let loopToken = 0;
  let recording = null;
  let tick = null;
  let alive = true;
  const clips = {};
  const stars = {};

  async function loop() {
    const my = ++loopToken;
    while (looping && alive && my === loopToken) {
      await speaker.sayAndWait(sentences[index][0]);
      if (!looping || my !== loopToken) return;
      await sleep(1000);
    }
  }
  const stopLoop = () => { looping = false; loopToken++; speaker.stop(); };

  async function record() {
    stopLoop();
    try {
      recording = await startRecording(() => finish());
    } catch {
      toast('Chưa có quyền dùng micro. Cho phép micro cho trang này trong Cài đặt › Safari › Micro rồi thử lại.');
      return;
    }
    app.update();
    // Elapsed time is written straight into the page so the screen doesn't redraw 5 times a second.
    tick = setInterval(() => {
      const el = document.querySelector('.rec-time');
      if (el && recording) el.textContent = `${((performance.now() - recording.started) / 1000).toFixed(1)} s / 10 s`;
    }, 200);
  }

  async function finish() {
    if (!recording) return;
    clearInterval(tick);
    const r = recording;
    recording = null;
    const i = index;
    const clip = await r.stop();
    if (clips[i]) URL.revokeObjectURL(clips[i].url);
    clips[i] = clip;
    Progress.count(QUEST.SPEAK);
    if (alive) app.update();
  }

  /** Waveform bars with the pitch curve drawn over them. */
  function draw(root) {
    const canvas = root.querySelector('canvas.wave');
    const clip = clips[index];
    if (!canvas || !clip?.rms.length) return;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    const g = canvas.getContext('2d');
    g.scale(dpr, dpr);
    const css = getComputedStyle(canvas);
    const peak = Math.max(...clip.rms, 0.01);
    const bw = w / clip.rms.length;
    g.fillStyle = css.getPropertyValue('--wave') || '#9ad3f2';
    clip.rms.forEach((v, k) => {
      const bh = Math.max(2, (v / peak) * (h - 8));
      g.fillRect(k * bw, (h - bh) / 2, Math.max(1, bw - 1), bh);
    });
    const voiced = clip.pitch.filter((f) => f > 0);
    if (voiced.length < 2) return;
    const lo = Math.min(...voiced) * 0.9, hi = Math.max(...voiced) * 1.1;
    const pw = w / clip.pitch.length;
    g.strokeStyle = css.getPropertyValue('--pitch') || '#f2667a';
    g.lineWidth = 3;
    g.lineCap = g.lineJoin = 'round';
    g.beginPath();
    let pen = false;
    clip.pitch.forEach((f, k) => {
      if (!f) { pen = false; return; }
      const x = k * pw + pw / 2, y = h - ((f - lo) / (hi - lo)) * (h - 10) - 5;
      if (pen) g.lineTo(x, y); else g.moveTo(x, y);
      pen = true;
    });
    g.stroke();
  }

  function feedback(clip, en) {
    if (!clip.seconds) return '<p class="muted small">Đã ghi âm. Trình duyệt này chưa phân tích được giọng, bạn vẫn nghe lại được.</p>';
    const hint = intonationHint(en);
    const trend = pitchTrend(clip.pitch);
    const tone = trend === 'unknown' ? 'Chưa đo được ngữ điệu, thử nói to và rõ hơn.'
      : trend === hint.want ? `Cuối câu bạn ${trend === 'rising' ? 'lên' : 'hạ'} giọng ✓`
      : hint.want === 'rising' ? 'Cuối câu nên lên giọng ↗' : 'Cuối câu nên hạ giọng ↘';
    const model = modelSeconds(en, speaker.rate);
    return `<div class="stack small">
      <div>⏱️ Bạn: ${clip.seconds.toFixed(1)} s · Mẫu: khoảng ${model.toFixed(1)} s. ${esc(speedFeedback(clip.seconds, model))}</div>
      <div>🎵 ${esc(tone)}</div>
    </div>`;
  }

  return {
    leave() {
      alive = false;
      stopLoop();
      clearInterval(tick);
      recording?.cancel();
      Object.values(clips).forEach((c) => URL.revokeObjectURL(c.url));
    },
    after: draw,
    view() {
      const [en, vi] = sentences[index];
      const hint = intonationHint(en);
      const clip = clips[index];
      const last = index === sentences.length - 1;
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar('Luyện nhại (shadowing)', topic.title)}
        <div class="scroll pad" data-scroll="shadow">
          ${bar((index + 1) / sentences.length)}
          <div class="muted center small">Câu ${index + 1} / ${sentences.length}</div>
          <div class="big-card" style="background:var(--al)">
            <div class="h-sm">${esc(en)}</div>
            <div class="muted mt-s">${esc(vi)}</div>
            <div class="hint-pill mt-s">${esc(hint.text)}</div>
            <div class="row center-h gap mt">
              ${sayBtn(en, { size: 48 })}${sayBtn(en, { size: 48, slow: true })}
              <button class="chip-toggle ${looping ? 'on' : ''}" data-act="loop">${looping ? '🔁 Đang lặp' : '🔁 Lặp'}</button>
            </div>
          </div>
          ${!canRecord ? '<div class="err">Trình duyệt này chưa hỗ trợ ghi âm. Trên iPad hãy mở bằng Safari (iPadOS 14.3 trở lên).</div>' : ''}
          <div class="center stack">
            ${recording
              ? `<button class="mic recording" data-act="stop" style="--s:84px" aria-label="Dừng ghi âm">⏹</button><div class="rec-time muted">0.0 s / 10 s</div><div class="muted small">Đang ghi âm… nói theo mẫu rồi bấm dừng</div>`
              : `<button class="mic" data-act="record" style="--s:84px" ${!canRecord ? 'disabled' : ''} aria-label="Ghi âm">⏺</button><div class="muted small">${clip ? 'Ghi lại lần nữa' : 'Nghe mẫu vài lần, rồi bấm ⏺ và nói theo'}</div>`}
          </div>
          ${clip ? `
            <div class="card">
              <canvas class="wave" aria-label="Sóng âm và cao độ giọng bạn"></canvas>
              <div class="tiny muted">Cột xanh: độ to · Đường đỏ: cao độ giọng bạn</div>
              ${feedback(clip, en)}
              <div class="row gap-s">
                <button class="btn outline grow" data-act="playMine">▶ Nghe lại giọng bạn</button>
                <button class="btn grow" data-act="compare">🎧 Mẫu → Bạn</button>
              </div>
              <div class="muted small">Bạn thấy giống người bản xứ chưa?</div>
              <div class="row gap-s">${RATINGS.map(([n, label]) => `<button class="filter grow ${stars[index] === n ? 'on' : ''}" data-act="rate" data-n="${n}">${label}</button>`).join('')}</div>
            </div>` : ''}
        </div>
        <div class="bottom">
          <div class="row gap">
            ${outBtn('← Trước', 'prev', { disabled: index === 0 || !!recording })}
            ${last ? bigBtn('Hoàn thành', 'finish', { color: 'mint', disabled: !!recording }) : bigBtn(clip ? 'Tiếp →' : 'Bỏ qua →', 'next', { disabled: !!recording })}
          </div>
        </div>
      </div>`;
    },
    actions: {
      loop: () => {
        if (looping) stopLoop();
        else { looping = true; loop(); }
      },
      record: () => { record(); return false; },
      stop: () => { finish(); return false; },
      playMine: () => { stopLoop(); play(clips[index].url); return false; },
      compare: async () => {
        stopLoop();
        await speaker.sayAndWait(sentences[index][0]);
        await sleep(300);
        await play(clips[index].url);
        return false;
      },
      rate: (d) => { stars[index] = +d.n; },
      prev: () => { stopLoop(); index--; },
      next: () => { stopLoop(); index++; },
      finish: () => {
        stopLoop();
        const total = Object.values(stars).reduce((a, b) => a + b, 0);
        const xp = 5 + total;
        Progress.addXp(xp);
        completeDialog({
          title: 'Nhại giọng chăm quá! 🎙️',
          message: `Bạn đã ghi âm ${Object.keys(clips).length}/${sentences.length} câu. Nghe lại giọng mình là cách sửa ngữ điệu nhanh nhất.`,
          xp,
          onDismiss: app.back,
        });
        return false;
      },
    },
  };
}

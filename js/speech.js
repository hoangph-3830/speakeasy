// Text-to-speech and speech recognition through the browser's Web Speech API.
// On iPad/iPhone Safari these use the system English voices and Siri dictation.

// iOS ships joke voices under English locales; never pick them for lessons.
const NOVELTY = /albert|bad news|bahh|bells|boing|bubbles|cellos|good news|jester|organ|superstar|trinoids|whisper|wobble|zarvox|fred|junior|ralph|kathy|grandma|grandpa|eddy|flo|reed|rocko|sandy|shelley/i;

class Speaker {
  constructor() {
    this.rate = 0.9;
    this.accent = 'US';
    this.speaking = null;
    this.listeners = new Set();
    this.callbacks = new Map();
    this.seq = 0;
    this.currentId = null;
    this.voices = [];
    const synth = window.speechSynthesis;
    if (synth) {
      const load = () => (this.voices = synth.getVoices());
      load();
      synth.addEventListener?.('voiceschanged', load);
    }
  }

  get supported() {
    return 'speechSynthesis' in window;
  }

  onChange(fn) {
    this.listeners.add(fn);
  }

  setSpeaking(text) {
    this.speaking = text;
    this.listeners.forEach((fn) => fn(text));
  }

  /** Best installed voice for the chosen accent; enhanced/premium voices sound far more natural. */
  voice() {
    const lang = this.accent === 'UK' ? 'en-GB' : 'en-US';
    const norm = (v) => v.lang.replace('_', '-').toLowerCase();
    const pool = this.voices.filter((v) => !NOVELTY.test(v.name));
    const exact = pool.filter((v) => norm(v) === lang.toLowerCase());
    const english = exact.length ? exact : pool.filter((v) => norm(v).startsWith('en'));
    const rank = (v) =>
      (/enhanced|premium|natural|neural/i.test(v.name) ? 4 : 0) +
      (/samantha|daniel|google|siri|ava|serena|arthur|karen|moira/i.test(v.name) ? 2 : 0) +
      (v.localService ? 1 : 0);
    return [...english].sort((a, b) => rank(b) - rank(a))[0] || null;
  }

  finish(id) {
    // A cancelled utterance finishes after its replacement started; keep the newer highlight.
    if (id === this.currentId) {
      this.currentId = null;
      this.setSpeaking(null);
    }
    const cb = this.callbacks.get(id);
    if (cb) {
      this.callbacks.delete(id);
      cb();
    }
  }

  /** Speaks `text`; `onDone` runs when it finishes, fails or is stopped. */
  say(text, { slow = false, pitch = 1, onDone } = {}) {
    const id = ++this.seq;
    this.currentId = id;
    this.setSpeaking(text);
    if (onDone) this.callbacks.set(id, onDone);
    const synth = window.speechSynthesis;
    if (!synth) return this.finish(id);
    const u = new SpeechSynthesisUtterance(text);
    u.lang = this.accent === 'UK' ? 'en-GB' : 'en-US';
    const v = this.voice();
    if (v) u.voice = v;
    u.rate = slow ? this.rate * 0.6 : this.rate;
    // Pitch gives story characters distinct voices with a single engine.
    u.pitch = pitch;
    u.onend = u.onerror = () => this.finish(id);
    // Safari sometimes never fires onend; don't let a dialogue hang on it.
    const words = text.split(/\s+/).length;
    setTimeout(() => this.finish(id), 4000 + (words * 650) / u.rate);
    this.utterance = u; // keeps it from being garbage-collected mid-sentence
    synth.cancel();
    synth.speak(u);
  }

  /** Resolves once `text` has been spoken (or speech was stopped). */
  sayAndWait(text, pitch = 1) {
    return new Promise((resolve) => this.say(text, { pitch, onDone: resolve }));
  }

  stop() {
    window.speechSynthesis?.cancel();
    this.currentId = null;
    this.setSpeaking(null);
    // Release anyone waiting in sayAndWait so their loops don't hang.
    const pending = [...this.callbacks.values()];
    this.callbacks.clear();
    pending.forEach((cb) => cb());
  }

  /** iOS only allows speech after a user gesture; speaking once on the first tap unlocks it for later. */
  unlock() {
    const synth = window.speechSynthesis;
    if (!synth || this.unlocked) return;
    this.unlocked = true;
    const u = new SpeechSynthesisUtterance(' ');
    u.volume = 0;
    synth.speak(u);
  }
}

export const speaker = new Speaker();

// ---- Recognition ------------------------------------------------------------

const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
export const canListen = !!Recognition;

let active = null;

/**
 * Listens for one English utterance and calls `onResult(alternatives)` with every guess, best first.
 * Shows a full-screen "listening" sheet with `prompt`, like Android's recognizer dialog.
 */
export function listen(prompt, { onResult, onCancel = () => {}, onError } = {}) {
  speaker.stop();
  const fail = (msg) => (onError ? onError(msg) : toast(msg));
  if (!Recognition) {
    fail('Trình duyệt này chưa hỗ trợ nhận diện giọng nói. Trên iPad/iPhone hãy mở bằng Safari (iOS 14.5 trở lên).');
    return onCancel();
  }
  if (active) active.abort();
  const rec = new Recognition();
  active = rec;
  rec.lang = 'en-US';
  rec.interimResults = true;
  rec.maxAlternatives = 5;
  rec.continuous = false;

  const sheet = listeningSheet(prompt, () => rec.stop(), () => { cancelled = true; rec.abort(); });
  let cancelled = false;
  let finals = null;
  let interim = '';

  rec.onresult = (e) => {
    const r = e.results[e.results.length - 1];
    const alts = [...r].map((a) => a.transcript.trim()).filter(Boolean);
    if (r.isFinal) finals = alts;
    else interim = alts[0] || '';
    sheet.heard(alts[0] || '');
  };
  rec.onerror = (e) => {
    if (e.error === 'aborted' || e.error === 'no-speech') return;
    cancelled = true;
    const msgs = {
      'not-allowed': 'Chưa có quyền dùng micro. Vào Cài đặt › Safari › Micro (hoặc bấm "aA" trên thanh địa chỉ) để cho phép.',
      'service-not-allowed': 'Nhận diện giọng nói đang tắt. Bật Cài đặt › Siri › "Đọc chính tả" (Dictation) rồi thử lại.',
      network: 'Không kết nối được dịch vụ nhận diện giọng nói. Kiểm tra mạng rồi thử lại.',
      'audio-capture': 'Không tìm thấy micro.',
    };
    fail(msgs[e.error] || `Không nhận được giọng nói (${e.error}).`);
  };
  rec.onend = () => {
    if (active === rec) active = null;
    sheet.close();
    // Safari sometimes ends without marking the last result final.
    const heard = finals || (interim ? [interim] : null);
    if (!cancelled && heard && heard.length) onResult(heard);
    else onCancel();
  };
  try {
    rec.start();
  } catch {
    sheet.close();
    onCancel();
  }
}

function listeningSheet(prompt, onDone, onCancel) {
  const el = document.createElement('div');
  el.className = 'listen-sheet';
  el.innerHTML = `
    <div class="listen-card" role="dialog" aria-label="Đang nghe">
      <div class="listen-orb">🎤</div>
      <div class="listen-prompt"></div>
      <div class="listen-heard">Đang nghe… hãy nói bằng tiếng Anh</div>
      <div class="row gap">
        <button class="btn ghost" data-x="cancel">Huỷ</button>
        <button class="btn" data-x="done">Xong</button>
      </div>
    </div>`;
  el.querySelector('.listen-prompt').textContent = prompt || '';
  el.querySelector('[data-x=done]').onclick = onDone;
  el.querySelector('[data-x=cancel]').onclick = onCancel;
  document.body.appendChild(el);
  return {
    heard(text) {
      if (text) el.querySelector('.listen-heard').textContent = `“${text}”`;
    },
    close: () => el.remove(),
  };
}

export function toast(msg) {
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  document.body.appendChild(el);
  setTimeout(() => el.classList.add('out'), 3800);
  setTimeout(() => el.remove(), 4300);
}

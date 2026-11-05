// Text-to-speech and speech recognition through the browser's Web Speech API.
// On iPad/iPhone Safari these use the system English voices and Siri dictation.
import { speechChunks } from './logic.js';

// iOS ships joke voices under English locales; never pick them for lessons.
const NOVELTY = /albert|bad news|bahh|bells|boing|bubbles|cellos|good news|jester|organ|superstar|trinoids|whisper|wobble|zarvox|fred|junior|ralph|kathy|grandma|grandpa|eddy|flo|reed|rocko|sandy|shelley/i;

class Speaker {
  constructor() {
    this.rate = 0.9;
    this.accent = 'US';
    /** Name of the voice picked in Settings; empty means automatic. */
    this.voiceName = '';
    this.speaking = null;
    this.listeners = new Set();
    this.callbacks = new Map();
    this.seq = 0;
    this.currentId = null;
    this.voices = [];
    const synth = window.speechSynthesis;
    if (synth) {
      const load = () => {
        this.voices = synth.getVoices();
        // Voices arrive late on most browsers; screens listing them redraw.
        window.dispatchEvent(new Event('se-voices'));
      };
      load();
      synth.addEventListener?.('voiceschanged', load);
    }
  }

  get supported() {
    return 'speechSynthesis' in window;
  }

  /** In-app browsers (Zalo, Facebook…) expose speechSynthesis with no voices, which speaks nothing. */
  hasVoices() {
    if (!this.voices.length) this.voices = window.speechSynthesis?.getVoices() || [];
    // Voices load late; give the browser a moment after start before calling it silent.
    return this.voices.length > 0 || performance.now() < 3000;
  }

  onChange(fn) {
    this.listeners.add(fn);
  }

  setSpeaking(text) {
    this.speaking = text;
    this.listeners.forEach((fn) => fn(text));
  }

  /**
   * English voices for the accent, smoothest first: neural "Natural" voices (Edge), iOS Premium and
   * Enhanced downloads, Google's online voices (Chrome), then the well-known system voices.
   */
  englishVoices() {
    const lang = this.accent === 'UK' ? 'en-gb' : 'en-us';
    const norm = (v) => v.lang.replace('_', '-').toLowerCase();
    const pool = this.voices.filter((v) => !NOVELTY.test(v.name) && norm(v).startsWith('en'));
    const rank = (v) =>
      (norm(v) === lang ? 20 : 0) +
      (/natural|neural/i.test(v.name) ? 12 : 0) +
      (/premium/i.test(v.name) ? 11 : 0) +
      (/enhanced/i.test(v.name) ? 9 : 0) +
      (/^google/i.test(v.name) ? 8 : 0) +
      (/siri|ava|zoe|evan|nathan|samantha|daniel|serena|arthur|karen|moira|aria|jenny|guy|libby|ryan|sonia/i.test(v.name) ? 3 : 0) +
      (/compact|eloquence/i.test(v.name) ? -6 : 0);
    return [...pool].sort((a, b) => rank(b) - rank(a));
  }

  /** The learner's chosen voice when it is installed, otherwise the best one for the accent. */
  voice() {
    return (this.voiceName && this.voices.find((v) => v.name === this.voiceName)) || this.englishVoices()[0] || null;
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
    // The utterance being replaced counts as finished, so anyone waiting on it carries on.
    if (this.currentId != null) this.finish(this.currentId);
    const id = ++this.seq;
    this.currentId = id;
    this.setSpeaking(text);
    if (onDone) this.callbacks.set(id, onDone);
    const synth = window.speechSynthesis;
    if (!synth || !this.hasVoices()) {
      noVoiceWarning();
      return this.finish(id);
    }
    synth.cancel();
    const v = this.voice();
    const rate = slow ? this.rate * 0.6 : this.rate;
    // Long text is queued sentence by sentence: it flows without gaps and engines don't cut it off.
    const chunks = speechChunks(text);
    this.utterances = chunks.map((chunk, i) => {
      const u = new SpeechSynthesisUtterance(chunk);
      u.lang = v?.lang || (this.accent === 'UK' ? 'en-GB' : 'en-US');
      if (v) u.voice = v;
      u.rate = rate;
      // Pitch gives story characters distinct voices with a single engine.
      u.pitch = pitch;
      if (i === chunks.length - 1) u.onend = () => this.finish(id);
      u.onerror = (e) => e.error !== 'interrupted' && e.error !== 'canceled' && this.finish(id);
      return u; // kept on this.utterances so they aren't garbage-collected mid-sentence
    });
    // Safari sometimes never fires onend; don't let a dialogue hang on it.
    const words = text.split(/\s+/).length;
    setTimeout(() => this.finish(id), 4000 + (words * 650) / rate);
    this.utterances.forEach((u) => synth.speak(u));
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

/** Apps like Zalo and Facebook open links in their own browser, which has no speech engine. */
export const inAppBrowser = (() => {
  const ua = navigator.userAgent;
  const m = ua.match(/Zalo|FBAN|FBAV|FB_IAB|Messenger|Instagram|Line\/|TikTok|musical_ly|Snapchat|Twitter|; wv\)/i);
  if (!m) return null;
  const name = /zalo/i.test(ua) ? 'Zalo' : /FBAN|FBAV|FB_IAB|messenger/i.test(ua) ? 'Facebook' : /instagram/i.test(ua) ? 'Instagram'
    : /line\//i.test(ua) ? 'Line' : /tiktok|musical_ly/i.test(ua) ? 'TikTok' : 'ứng dụng này';
  return { name, ios: /iPhone|iPad|iPod/i.test(ua) };
})();

/** Opens this page in Chrome on Android (intent link); iOS apps only allow it from their own menu. */
export function openInBrowser() {
  const { host, pathname, search, hash } = location;
  location.href = `intent://${host}${pathname}${search}${hash}#Intent;scheme=https;package=com.android.chrome;end`;
}

let warned = false;
function noVoiceWarning() {
  if (warned) return;
  warned = true;
  toast(inAppBrowser
    ? `Trình duyệt trong ${inAppBrowser.name} không có giọng đọc. Mở trang bằng ${inAppBrowser.ios ? 'Safari' : 'Chrome'} để nghe được nhé.`
    : 'Máy chưa có giọng đọc tiếng Anh. Thử mở bằng Chrome hoặc Safari, hoặc cài giọng tiếng Anh trong cài đặt máy.');
  setTimeout(() => (warned = false), 15000);
}

export function toast(msg) {
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  document.body.appendChild(el);
  setTimeout(() => el.classList.add('out'), 3800);
  setTimeout(() => el.remove(), 4300);
}

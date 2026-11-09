// Text-to-speech and speech recognition through the browser's Web Speech API.
// On iPad/iPhone Safari these use the system English voices and Siri dictation.
import { speechChunks } from './logic.js';
import { playOnline, releaseOnline, stopOnline, unlockOnline } from './online-voice.js';

// iOS ships joke voices under English locales; never pick them for lessons.
const NOVELTY = /albert|bad news|bahh|bells|boing|bubbles|cellos|good news|jester|organ|superstar|trinoids|whisper|wobble|zarvox|fred|junior|ralph|kathy|grandma|grandpa|eddy|flo|reed|rocko|sandy|shelley/i;

class Speaker {
  constructor() {
    this.rate = 0.9;
    this.accent = 'US';
    this.lastAudioAt = -Infinity;
    /** Name of the voice picked in Settings; empty means automatic. */
    this.voiceName = '';
    /** "Giọng đọc online": play MP3 speech instead of the browser's engine (see online-voice.js). */
    this.online = false;
    this.speaking = null;
    this.listeners = new Set();
    this.callbacks = new Map();
    this.seq = 0;
    this.currentId = null;
    this.voices = [];
    /** Voices that stayed silent when asked to speak (e.g. listed on iOS but not downloaded). */
    this.silentVoices = new Set();
    const synth = window.speechSynthesis;
    if (synth) {
      // Back from the background, iOS may keep a stuck queue that blocks everything after it.
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && !this.speaking) { synth.cancel(); synth.resume(); }
      });
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
    // iOS needs a moment after speech before the microphone works (see listen()).
    if (this.speaking != null && text == null) this.lastAudioAt = performance.now();
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
      (/ava|zoe|evan|nathan|samantha|daniel|serena|arthur|karen|moira|aria|jenny|guy|libby|ryan|sonia/i.test(v.name) ? 3 : 0) +
      (/compact|eloquence|siri/i.test(v.name) ? -6 : 0);
    return [...pool].sort((a, b) => rank(b) - rank(a));
  }

  /** The learner's chosen voice when it is installed, otherwise the best one for the accent. */
  voice() {
    const usable = (v) => v && !this.silentVoices.has(v.name);
    const chosen = this.voiceName && this.voices.find((v) => v.name === this.voiceName);
    return (usable(chosen) && chosen) || this.englishVoices().find(usable) || null;
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
  say(text, { slow = false, pitch = 1, onDone, local = false } = {}) {
    // The utterance being replaced counts as finished, so anyone waiting on it carries on.
    if (this.currentId != null) this.finish(this.currentId);
    const id = ++this.seq;
    this.currentId = id;
    this.setSpeaking(text);
    if (onDone) this.callbacks.set(id, onDone);
    if (this.online && !local) return this.sayOnline(id, text, { slow, pitch, onDone });
    const synth = window.speechSynthesis;
    // In-app browsers expose an engine that never speaks; anywhere else an empty voice list is no
    // proof of silence (iPad home-screen apps often list none yet speak with the default voice).
    if (!synth || (inAppBrowser && !this.hasVoices())) {
      noVoiceWarning();
      return this.finish(id);
    }
    // iOS drops an utterance queued in the same tick as cancel(), so wait a beat when something was playing.
    const busy = synth.speaking || synth.pending;
    synth.cancel();
    // iOS can be left paused after the app was in the background or a video played.
    synth.resume();
    const v = this.voice();
    const rate = slow ? this.rate * 0.6 : this.rate;
    // Long text is queued sentence by sentence: it flows without gaps and engines don't cut it off.
    const chunks = speechChunks(text);
    let started = false;
    const queue = (voice) => {
      this.utterances = chunks.map((chunk, i) => {
        const u = new SpeechSynthesisUtterance(chunk);
        u.lang = voice?.lang || (this.accent === 'UK' ? 'en-GB' : 'en-US');
        if (voice) u.voice = voice;
        u.rate = rate;
        // Pitch gives story characters distinct voices with a single engine.
        u.pitch = pitch;
        u.onstart = () => { started = true; this.note('start', voice?.name || 'mặc định'); };
        if (i === chunks.length - 1) u.onend = () => { this.note('end'); this.finish(id); };
        u.onerror = (e) => {
          this.note('error', e.error);
          if (e.error !== 'interrupted' && e.error !== 'canceled') this.finish(id);
        };
        return u; // kept on this.utterances so they aren't garbage-collected mid-sentence
      });
      this.utterances.forEach((u) => synth.speak(u));
    };
    if (busy) setTimeout(() => id === this.currentId && queue(v), 80);
    else queue(v);
    setTimeout(() => {
      // synth.speaking means sound is playing even if the engine skipped the start event.
      if (started || synth.speaking || id !== this.currentId) return;
      this.note('silent', v?.name || 'mặc định');
      // A listed voice that is not downloaded (or a Siri voice) speaks nothing on iOS: retry with the default.
      if (v?.localService) {
        this.silentVoices.add(v.name);
        synth.cancel();
        setTimeout(() => id === this.currentId && queue(null), 80);
      } else if (!this.voices.length) noVoiceWarning();
    }, 1500);
    // Safari sometimes never fires onend; don't let a dialogue hang on it.
    const words = text.split(/\s+/).length;
    setTimeout(() => this.finish(id), 5500 + (words * 650) / rate);
  }

  sayOnline(id, text, { slow, pitch, onDone }) {
    window.speechSynthesis?.cancel();
    const speed = slow ? this.rate * 0.6 : this.rate;
    playOnline(speechChunks(text), {
      accent: this.accent,
      speed,
      onStart: () => this.note('start', 'online'),
      onEnd: () => { this.note('end', 'online'); this.finish(id); },
      onError: (e) => {
        this.note('error', `online: ${e.message || e.name || e}`);
        if (id !== this.currentId) return;
        // No network or the service refused: read it with the device's engine instead.
        this.callbacks.delete(id);
        this.say(text, { slow, pitch, onDone, local: true });
      },
    });
    // A stalled download must not leave a dialogue waiting forever.
    const words = text.split(/s+/).length;
    setTimeout(() => this.finish(id), 8000 + (words * 900) / speed);
  }

  /** Resolves once `text` has been spoken (or speech was stopped). */
  sayAndWait(text, pitch = 1) {
    return new Promise((resolve) => this.say(text, { pitch, onDone: resolve }));
  }

  stop() {
    window.speechSynthesis?.cancel();
    stopOnline();
    this.currentId = null;
    this.setSpeaking(null);
    // Release anyone waiting in sayAndWait so their loops don't hang.
    const pending = [...this.callbacks.values()];
    this.callbacks.clear();
    pending.forEach((cb) => cb());
  }

  /** Last speech events, newest last, for the sound check in Settings. */
  note(event, detail = '') {
    this.events = [...(this.events || []).slice(-7), `${new Date().toLocaleTimeString()} ${event}${detail ? ` (${detail})` : ''}`];
  }

  /** iOS only allows speech after a user gesture; speaking once on the first tap unlocks it for later. */
  unlock() {
    unlockOnline();
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

const IOS = /iPhone|iPad|iPod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

/** What to check when the microphone never comes on; iPhone settings differ from Android's. */
const NO_AUDIO_HELP = IOS
  ? `<b>Micro chưa nhận được tiếng.</b> Thử lần lượt:<br>
     1. Đợi giọng đọc tắt hẳn rồi bấm <b>Thử lại</b>.<br>
     2. Bấm <b>aA</b> trên thanh địa chỉ › <b>Cài đặt trang web</b> › <b>Micro: Cho phép</b>.<br>
     3. Cài đặt › <b>Siri</b> (hoặc Bàn phím) › bật <b>Đọc chính tả</b>.<br>
     4. Vẫn không được: tải lại trang, và mở bằng Safari (không mở trong Zalo/Facebook).`
  : '<b>Micro chưa nhận được tiếng.</b> Kiểm tra quyền micro của trình duyệt, đợi giọng đọc tắt hẳn rồi bấm <b>Thử lại</b>.';

const ERRORS = {
  'not-allowed': 'Chưa có quyền dùng micro. Bấm "aA" trên thanh địa chỉ › Cài đặt trang web › Micro: Cho phép (Android: biểu tượng ổ khoá › Quyền).',
  'service-not-allowed': 'Nhận diện giọng nói đang tắt. Bật Cài đặt › Siri › "Đọc chính tả" (Dictation) rồi thử lại.',
  network: 'Không kết nối được dịch vụ nhận diện giọng nói. Kiểm tra mạng rồi thử lại.',
  'audio-capture': 'Không tìm thấy micro, hoặc ứng dụng khác đang dùng micro.',
};

/**
 * Hands the audio hardware to the microphone. Forcing Safari's audio session to "playback" left the
 * iPad microphone recording silence, so the page never sets a session type and puts back the
 * default; the online voice's player lets go of its audio as well.
 */
export function prepareMic() {
  releaseOnline();
  try {
    if (navigator.audioSession && navigator.audioSession.type !== 'auto') navigator.audioSession.type = 'auto';
  } catch {
    // Not supported: nothing to change.
  }
}

/**
 * Listens for one English utterance and calls `onResult(alternatives)` with every guess, best first.
 * Shows a full-screen "listening" sheet with `prompt`, like Android's recognizer dialog.
 *
 * iOS Safari keeps the audio session for a moment after text-to-speech, and a recognizer started
 * then hears nothing and never ends (WebKit bug 321436). So: wait a little after speech, and if no
 * audio arrives, throw the recognizer away and start a fresh one.
 */
export function listen(prompt, { onResult, onCancel = () => {} } = {}) {
  const sinceSpeech = speaker.speaking != null ? 0 : performance.now() - speaker.lastAudioAt;
  speaker.stop();
  if (!Recognition) {
    toast('Trình duyệt này chưa hỗ trợ nhận diện giọng nói. Trên iPad/iPhone hãy mở bằng Safari (iOS 14.5 trở lên).');
    return onCancel();
  }
  if (active) active.abort();

  let rec = null;
  let attempts = 0;
  let settled = false;
  let finals = null;
  let interim = '';
  let timers = [];
  const clear = () => { timers.forEach(clearTimeout); timers = []; };
  const heardSoFar = () => finals || (interim ? [interim] : null);

  const drop = () => {
    const r = rec;
    rec = null;
    if (active === r) active = null;
    try { r?.abort(); } catch { /* already stopped */ }
  };

  const settle = (heard) => {
    if (settled) return;
    settled = true;
    clear();
    drop();
    sheet.close();
    if (heard?.length) onResult(heard);
    else onCancel();
  };

  const giveUp = (html) => {
    clear();
    drop();
    sheet.problem(html);
  };

  function start() {
    clear();
    attempts++;
    finals = null;
    interim = '';
    prepareMic();
    const r = new Recognition();
    rec = r;
    active = r;
    r.lang = 'en-US';
    r.interimResults = true;
    r.maxAlternatives = 5;
    r.continuous = false;
    let gotAudio = false;
    sheet.status(attempts > 1 ? 'Đang bật lại micro…' : 'Đang nghe… hãy nói bằng tiếng Anh');

    r.onaudiostart = () => { gotAudio = true; sheet.status('🎙️ Micro đã bật, mời bạn nói'); };
    r.onspeechstart = () => { gotAudio = true; sheet.status('Đang nghe giọng bạn…'); };
    r.onresult = (e) => {
      if (rec !== r) return;
      gotAudio = true;
      const res = e.results[e.results.length - 1];
      const alts = [...res].map((a) => a.transcript.trim()).filter(Boolean);
      if (res.isFinal) finals = alts;
      else interim = alts[0] || '';
      sheet.heard(alts[0] || '');
    };
    r.onerror = (e) => {
      if (rec !== r || e.error === 'aborted') return;
      if (e.error === 'no-speech') return giveUp('<b>Chưa nghe thấy gì.</b> Nói to và gần micro hơn rồi bấm <b>Thử lại</b>.');
      giveUp(ERRORS[e.error] || `Không nhận được giọng nói (${e.error}).`);
    };
    r.onend = () => {
      if (rec !== r || settled) return;
      const heard = heardSoFar();
      if (heard) return settle(heard);
      if (!gotAudio && attempts < 3) return start();
      giveUp(gotAudio ? '<b>Chưa nghe rõ.</b> Bấm <b>Thử lại</b> và nói lại nhé.' : NO_AUDIO_HELP);
    };
    try {
      r.start();
    } catch {
      giveUp(NO_AUDIO_HELP);
      return;
    }
    // A stuck session never reports audio: replace it with a fresh recognizer.
    timers.push(setTimeout(() => {
      if (rec !== r || gotAudio || settled) return;
      r.onend = null;
      try { r.abort(); } catch { /* ignore */ }
      if (attempts < 3) start();
      else giveUp(NO_AUDIO_HELP);
    }, 3000));
  }

  const sheet = listeningSheet(prompt, {
    // "Xong": stop and use what was heard; iOS sometimes never fires onend after stop().
    onDone: () => {
      if (!rec) return settle(heardSoFar());
      try { rec.stop(); } catch { /* ignore */ }
      timers.push(setTimeout(() => settle(heardSoFar()), 1500));
    },
    onCancel: () => settle(null),
    // A tap is a fresh user gesture, which also satisfies browsers that want one for the mic.
    onRetry: () => { attempts = 0; start(); },
  });

  const wait = IOS ? Math.max(0, 1200 - sinceSpeech) : 0;
  if (wait > 0) {
    sheet.status('Đang chuẩn bị micro…');
    timers.push(setTimeout(start, wait));
  } else start();
}

function listeningSheet(prompt, { onDone, onCancel, onRetry }) {
  const el = document.createElement('div');
  el.className = 'listen-sheet';
  el.innerHTML = `
    <div class="listen-card" role="dialog" aria-label="Đang nghe">
      <div class="listen-orb">🎤</div>
      <div class="listen-prompt"></div>
      <div class="listen-heard"></div>
      <div class="listen-help" hidden></div>
      <div class="row gap">
        <button class="btn ghost" data-x="cancel">Huỷ</button>
        <button class="btn" data-x="done">Xong</button>
        <button class="btn" data-x="retry" hidden>Thử lại</button>
      </div>
    </div>`;
  const q = (sel) => el.querySelector(sel);
  q('.listen-prompt').textContent = prompt || '';
  q('[data-x=done]').onclick = onDone;
  q('[data-x=cancel]').onclick = onCancel;
  q('[data-x=retry]').onclick = () => {
    q('.listen-help').hidden = true;
    q('[data-x=retry]').hidden = true;
    q('[data-x=done]').hidden = false;
    q('.listen-orb').classList.remove('off');
    onRetry();
  };
  document.body.appendChild(el);
  return {
    status(text) {
      q('.listen-heard').textContent = text;
    },
    heard(text) {
      if (text) q('.listen-heard').textContent = `“${text}”`;
    },
    problem(html) {
      q('.listen-help').innerHTML = html;
      q('.listen-help').hidden = false;
      q('.listen-heard').textContent = '';
      q('[data-x=done]').hidden = true;
      q('[data-x=retry]').hidden = false;
      q('.listen-orb').classList.add('off');
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

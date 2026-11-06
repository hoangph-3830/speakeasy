// "Giọng đọc online": speech fetched as MP3 from Google's translate voice and played through an
// <audio> element. Media playback works on iPads where Safari's speech engine reports that it is
// speaking yet stays silent (seen on iPadOS 27), because it uses the same channel as video.

const ENDPOINT = 'https://translate.google.com/translate_tts';
/** The service reads at most about 200 characters per request. */
const MAX_CHARS = 180;
// A tenth of a second of silence; playing it on the first tap lets later pieces start without a tap on iOS.
function silentWav(samples = 800) {
  const bytes = new Uint8Array(44 + samples * 2);
  const view = new DataView(bytes.buffer);
  const text = (at, str) => [...str].forEach((ch, i) => view.setUint8(at + i, ch.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, 36 + samples * 2, true); text(8, 'WAVE');
  text(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, 8000, true); view.setUint32(28, 16000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, samples * 2, true);
  return `data:audio/wav;base64,${btoa(String.fromCharCode(...bytes))}`;
}

/** Pieces of at most MAX_CHARS, cut at the last space before the limit. */
export function onlineChunks(sentences) {
  const out = [];
  for (const s of sentences) {
    let rest = s.trim();
    while (rest.length > MAX_CHARS) {
      const cut = rest.lastIndexOf(' ', MAX_CHARS);
      const at = cut > 40 ? cut : MAX_CHARS;
      out.push(rest.slice(0, at));
      rest = rest.slice(at).trim();
    }
    if (rest) out.push(rest);
  }
  return out;
}

/** Address of the MP3 for one piece; speed runs from 0.3 (slow) to 1 (normal). */
export const onlineUrl = (text, accent, speed) =>
  `${ENDPOINT}?ie=UTF-8&client=tw-ob&tl=${accent === 'UK' ? 'en-GB' : 'en-US'}&ttsspeed=${Math.min(1, Math.max(0.3, speed)).toFixed(2)}&q=${encodeURIComponent(text)}`;

let audio = null;
let token = 0;
const player = () => {
  if (!audio) {
    audio = new Audio();
    audio.preload = 'auto';
  }
  return audio;
};

/** Called on the first tap: iOS then lets this element play later pieces on its own. */
export function unlockOnline() {
  const a = player();
  a.src = silentWav();
  a.play().catch(() => {});
}

/**
 * Plays the pieces one after another. `onEnd` runs after the last one; `onError` runs when the
 * service cannot be reached (offline, blocked), so the caller can fall back to the speech engine.
 */
export function playOnline(sentences, { accent, speed, onStart, onEnd, onError }) {
  const mine = ++token;
  const pieces = onlineChunks(sentences);
  const a = player();
  let i = 0;
  const next = () => {
    if (mine !== token) return;
    if (i >= pieces.length) return onEnd();
    a.src = onlineUrl(pieces[i++], accent, speed);
    a.play().then(() => i === 1 && onStart?.()).catch((e) => mine === token && onError(e));
  };
  a.onended = next;
  a.onerror = () => mine === token && onError(new Error('audio'));
  next();
}

export function stopOnline() {
  token++;
  if (audio) {
    audio.onended = null;
    audio.onerror = null;
    audio.pause();
  }
}

/**
 * iPhones and iPads, where Safari's speech engine can report that it is speaking and stay silent
 * (seen on iPadOS 27). Safari no longer puts the real iOS version in its user agent, so all of them
 * get the online voice by default. iPads report themselves as a Mac; a touch screen tells them apart.
 */
export function speechEngineMayBeSilent(ua = navigator.userAgent, touchPoints = navigator.maxTouchPoints || 0) {
  return /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && touchPoints > 1);
}

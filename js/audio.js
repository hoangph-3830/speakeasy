// Pure analysis of a recorded attempt for shadowing: loudness per frame, a pitch curve and the
// intonation at the end of the sentence. No DOM here, so `node web/test/logic.test.mjs` covers it.

const WH = /^(what|where|when|why|who|whom|whose|which|how)\b/i;

/** The intonation a sentence wants at its end. */
export function intonationHint(sentence) {
  const s = sentence.trim();
  if (s.endsWith('?') && WH.test(s)) return { want: 'falling', text: '↘ Câu hỏi Wh-: hạ giọng ở cuối' };
  if (s.endsWith('?')) return { want: 'rising', text: '↗ Câu hỏi Yes/No: lên giọng ở cuối' };
  return { want: 'falling', text: '↘ Câu kể: hạ giọng ở cuối, nhấn từ quan trọng' };
}

/** Averages blocks of samples down to about `target` Hz; pitch tracking is far cheaper at 16 kHz. */
export function downsample(samples, rate, target = 16000) {
  const step = Math.max(1, Math.floor(rate / target));
  if (step === 1) return { samples, rate };
  const out = new Float32Array(Math.floor(samples.length / step));
  for (let i = 0; i < out.length; i++) {
    let sum = 0;
    for (let k = 0; k < step; k++) sum += samples[i * step + k];
    out[i] = sum / step;
  }
  return { samples: out, rate: rate / step };
}

/** Root-mean-square loudness per `ms` frame. */
export function rmsFrames(samples, rate, ms = 20) {
  const n = Math.max(1, Math.round((rate * ms) / 1000));
  const out = [];
  for (let i = 0; i + n <= samples.length; i += n) {
    let sum = 0;
    for (let k = i; k < i + n; k++) sum += samples[k] * samples[k];
    out.push(Math.sqrt(sum / n));
  }
  return out;
}

/** Fundamental frequency of one frame by normalised autocorrelation, or 0 when unvoiced. */
export function framePitch(frame, rate, min = 70, max = 400) {
  let energy = 0;
  for (const x of frame) energy += x * x;
  if (energy / frame.length < 1e-4) return 0;
  const lo = Math.floor(rate / max);
  const hi = Math.min(Math.ceil(rate / min), frame.length - 1);
  const rs = [];
  let best = 0;
  for (let lag = lo; lag <= hi; lag++) {
    let c = 0, e1 = 0, e2 = 0;
    for (let i = 0; i + lag < frame.length; i++) {
      c += frame[i] * frame[i + lag];
      e1 += frame[i] * frame[i];
      e2 += frame[i + lag] * frame[i + lag];
    }
    rs[lag] = c / Math.sqrt(e1 * e2 || 1);
    best = Math.max(best, rs[lag]);
  }
  if (best < 0.6) return 0;
  // Multiples of the period correlate almost as well; the first strong peak is the real pitch.
  let bestLag = 0;
  for (let lag = lo + 1; lag < hi; lag++)
    if (rs[lag] >= 0.9 * best && rs[lag] >= rs[lag - 1] && rs[lag] >= rs[lag + 1]) { bestLag = lag; break; }
  if (!bestLag) return 0;
  // Parabolic interpolation around the peak gives sub-sample accuracy.
  const ac = (lag) => { let c = 0; for (let i = 0; i + lag < frame.length; i++) c += frame[i] * frame[i + lag]; return c; };
  const y0 = ac(bestLag - 1), y1 = ac(bestLag), y2 = ac(bestLag + 1);
  const shift = (y0 - y2) / (2 * (y0 - 2 * y1 + y2) || 1);
  return rate / (bestLag + (Math.abs(shift) < 1 ? shift : 0));
}

/** Pitch per `ms` frame (0 = silence or noise). */
export function pitchCurve(samples, rate, ms = 40) {
  const n = Math.round((rate * ms) / 1000);
  const out = [];
  for (let i = 0; i + n <= samples.length; i += n) out.push(framePitch(samples.subarray ? samples.subarray(i, i + n) : samples.slice(i, i + n), rate));
  return out;
}

const median = (xs) => {
  const s = [...xs].sort((a, b) => a - b);
  return s.length ? s[Math.floor(s.length / 2)] : 0;
};

/** Whether the voice goes up or down over the last 30% of voiced frames compared with the middle. */
export function pitchTrend(curve) {
  const voiced = curve.filter((f) => f > 0);
  if (voiced.length < 6) return 'unknown';
  const cut = Math.floor(voiced.length * 0.7);
  const middle = median(voiced.slice(Math.floor(voiced.length * 0.3), cut));
  const end = median(voiced.slice(cut));
  const ratio = end / middle;
  return ratio > 1.06 ? 'rising' : ratio < 0.94 ? 'falling' : 'flat';
}

/** Rough length of the native model: about 2.5 words a second at normal speed. */
export const modelSeconds = (sentence, rate = 1) => sentence.split(/\s+/).filter(Boolean).length / (2.5 * rate);

export function speedFeedback(learnerSeconds, modelSecs) {
  const ratio = learnerSeconds / modelSecs;
  if (ratio > 1.4) return 'Bạn nói chậm hơn người bản xứ khá nhiều, thử nói liền mạch hơn';
  if (ratio < 0.7) return 'Hơi nhanh, nói rõ từng từ nhé';
  return 'Tốc độ ổn 👍';
}

/** Cuts leading and trailing silence so durations compare speech, not pauses. */
export function trimSilence(samples, rate, threshold = 0.02) {
  const frames = rmsFrames(samples, rate, 10);
  const n = Math.round(rate / 100);
  const first = frames.findIndex((r) => r > threshold);
  if (first < 0) return samples.slice(0, 0);
  let last = frames.length - 1;
  while (last > first && frames[last] <= threshold) last--;
  return samples.slice(first * n, (last + 1) * n);
}

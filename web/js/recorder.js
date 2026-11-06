// Microphone recording for shadowing: MediaRecorder for playback, decoded PCM for analysis.
import { downsample, pitchCurve, rmsFrames, trimSilence } from './audio.js';
import { micAudioSession } from './speech.js';

export const canRecord = !!(navigator.mediaDevices?.getUserMedia && window.MediaRecorder);

const MAX_SECONDS = 10;

/** Starts recording; resolves to a handle whose stop() gives the clip and its analysis. */
export async function startRecording(onAutoStop) {
  micAudioSession();
  const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } });
  // Safari records AAC in MP4, Chrome and Firefox record Opus in WebM.
  const type = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm'].find((t) => MediaRecorder.isTypeSupported?.(t));
  const rec = new MediaRecorder(stream, type ? { mimeType: type } : undefined);
  const chunks = [];
  rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
  const started = performance.now();
  const done = new Promise((resolve) => (rec.onstop = resolve));
  rec.start();
  const timer = setTimeout(() => { if (rec.state === 'recording') { rec.stop(); onAutoStop?.(); } }, MAX_SECONDS * 1000);

  return {
    started,
    async stop() {
      clearTimeout(timer);
      if (rec.state === 'recording') rec.stop();
      await done;
      // Releasing the mic also hands iOS audio back to the speaker for text-to-speech.
      stream.getTracks().forEach((t) => t.stop());
      const blob = new Blob(chunks, { type: rec.mimeType || type || 'audio/webm' });
      return { blob, url: URL.createObjectURL(blob), ...(await analyse(blob)) };
    },
    cancel() {
      clearTimeout(timer);
      if (rec.state === 'recording') rec.stop();
      stream.getTracks().forEach((t) => t.stop());
    },
  };
}

/** Loudness frames, pitch curve and speaking time of a clip. */
async function analyse(blob) {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    const ctx = new Ctx();
    const audio = await ctx.decodeAudioData(await blob.arrayBuffer());
    ctx.close?.();
    const { samples, rate } = downsample(audio.getChannelData(0), audio.sampleRate);
    const speech = trimSilence(samples, rate);
    return {
      seconds: speech.length / rate,
      rms: rmsFrames(speech, rate, 20),
      pitch: pitchCurve(speech, rate, 40),
    };
  } catch {
    // Some browsers can't decode their own recording; playback still works.
    return { seconds: 0, rms: [], pitch: [] };
  }
}

/** Plays a recorded clip; resolves when it ends. */
export function play(url) {
  return new Promise((resolve) => {
    const a = new Audio(url);
    a.onended = a.onerror = () => resolve();
    a.play().catch(resolve);
  });
}

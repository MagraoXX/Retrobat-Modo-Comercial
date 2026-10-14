package com.magraoxx.eichatnext;

import android.content.Context;

import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

public final class SherpaVadEngine implements VoiceActivityEngine {
    private final Context context;
    private Listener listener;
    private Vad vad;
    private boolean speechActive;

    public SherpaVadEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override public synchronized boolean initialize() {
        if (vad != null) return true;
        try {
            SileroVadModelConfig silero = new SileroVadModelConfig();
            silero.setModel("silero_vad.onnx");
            silero.setThreshold(0.50f);
            silero.setMinSilenceDuration(0.38f);
            silero.setMinSpeechDuration(0.18f);
            silero.setWindowSize(512);
            silero.setMaxSpeechDuration(20.0f);

            VadModelConfig config = new VadModelConfig();
            config.setSileroVadModelConfig(silero);
            config.setSampleRate(16000);
            config.setNumThreads(1);
            config.setProvider("cpu");
            config.setDebug(false);

            vad = new Vad(context.getAssets(), config);
            return true;
        } catch (Throwable error) {
            emitError("Falha ao iniciar VAD local: " + error.getMessage());
            return false;
        }
    }

    @Override public void onPcm(short[] samples, int count, int sampleRate, float rmsDb) {
        Vad current;
        synchronized (this) {
            if (vad == null && !initialize()) return;
            current = vad;
        }
        if (samples == null || count <= 0 || current == null) return;
        int n = Math.min(samples.length, count);
        float[] normalized = new float[n];
        for (int i = 0; i < n; i++) normalized[i] = samples[i] / 32768.0f;

        try {
            current.acceptWaveform(normalized);
            boolean detected = current.isSpeechDetected();
            if (detected && !speechActive) {
                speechActive = true;
                Listener l = listener;
                if (l != null) l.onSpeechStarted();
            }

            while (!current.empty()) {
                SpeechSegment segment = current.front();
                current.pop();
                speechActive = false;
                if (segment == null || segment.getSamples() == null) continue;
                float[] speech = segment.getSamples();
                short[] pcm = new short[speech.length];
                for (int i = 0; i < speech.length; i++) {
                    float clamped = Math.max(-1f, Math.min(1f, speech[i]));
                    pcm[i] = (short) Math.round(clamped * 32767f);
                }
                Listener l = listener;
                if (l != null && pcm.length > 0) l.onSpeechEnded(pcm, sampleRate);
            }
        } catch (Throwable error) {
            emitError("Erro no detector de fala: " + error.getMessage());
        }
    }

    @Override public synchronized void reset() {
        speechActive = false;
        if (vad != null) {
            try { vad.reset(); } catch (Throwable ignored) {}
        }
    }

    @Override public synchronized void release() {
        speechActive = false;
        if (vad != null) {
            try { vad.release(); } catch (Throwable ignored) {}
            vad = null;
        }
    }

    private void emitError(String message) {
        Listener l = listener;
        if (l != null) l.onError(message);
    }
}

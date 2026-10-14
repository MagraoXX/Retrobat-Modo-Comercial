package com.magraoxx.eichatnext;

import android.content.Context;

public final class VoicePipeline {
    public interface Listener {
        void onSpeechStarted();
        void onTranscript(String text);
        void onSpeechSegmentWithoutStt(long durationMs);
        void onError(String message);
    }

    private final AudioBus audioBus = new AudioBus();
    private final SherpaVadEngine vad;
    private final SherpaSpeechToTextEngine stt;
    private final Listener listener;
    private boolean started;

    public VoicePipeline(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        this.listener = listener;
        this.vad = new SherpaVadEngine(app);
        this.stt = new SherpaSpeechToTextEngine(app);

        vad.setListener(new VoiceActivityEngine.Listener() {
            @Override public void onSpeechStarted() {
                Listener l = VoicePipeline.this.listener;
                if (l != null) l.onSpeechStarted();
            }

            @Override public void onSpeechEnded(short[] utterancePcm, int sampleRate) {
                if (utterancePcm == null || utterancePcm.length == 0) return;
                if (stt.hasModelFiles()) {
                    stt.startUtterance();
                    stt.acceptPcm(utterancePcm, utterancePcm.length, sampleRate);
                    stt.finishUtterance();
                } else {
                    long durationMs = Math.round((utterancePcm.length * 1000.0) / sampleRate);
                    Listener l = VoicePipeline.this.listener;
                    if (l != null) l.onSpeechSegmentWithoutStt(durationMs);
                }
            }

            @Override public void onError(String message) {
                emitError(message);
            }
        });

        stt.setListener(new SpeechToTextEngine.Listener() {
            @Override public void onPartial(String text) {}

            @Override public void onFinal(String text) {
                Listener l = VoicePipeline.this.listener;
                if (l != null) l.onTranscript(text);
            }

            @Override public void onError(String message) {
                emitError(message);
            }
        });
    }

    public synchronized boolean start() {
        if (started) return true;
        if (!vad.initialize()) return false;
        audioBus.subscribe(vad);
        started = true;
        return true;
    }

    public void acceptPcm(short[] samples, int count, int sampleRate, float rmsDb) {
        if (!started) return;
        audioBus.publish(samples, count, sampleRate, rmsDb);
    }

    public boolean isSttModelReady() {
        return stt.hasModelFiles();
    }

    public synchronized void reset() {
        vad.reset();
        stt.reset();
    }

    public synchronized void release() {
        if (!started) {
            stt.release();
            vad.release();
            return;
        }
        audioBus.unsubscribe(vad);
        audioBus.clear();
        vad.release();
        stt.release();
        started = false;
    }

    private void emitError(String message) {
        Listener l = listener;
        if (l != null) l.onError(message);
    }
}

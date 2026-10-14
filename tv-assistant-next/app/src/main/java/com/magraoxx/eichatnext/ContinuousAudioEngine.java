package com.magraoxx.eichatnext;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Process;

public final class ContinuousAudioEngine {
    public interface Listener {
        void onAudio(short[] samples, int count, float rmsDb);
        void onStarted();
        void onStopped();
        void onError(String message);
    }

    public static final int SAMPLE_RATE = 16000;
    private final Context context;
    private final Listener listener;
    private volatile boolean running;
    private AudioRecord recorder;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private Thread thread;

    public ContinuousAudioEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Permissão de microfone não concedida.");
            return;
        }

        int min = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            listener.onError("A TV não informou um buffer de áudio válido.");
            return;
        }

        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 2, SAMPLE_RATE));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecorder();
                listener.onError("Não foi possível inicializar o microfone.");
                return;
            }
            enableAudioEffects(recorder.getAudioSessionId());
            recorder.startRecording();
            running = true;
            listener.onStarted();
            thread = new Thread(this::captureLoop, "EiChatNext-Audio");
            thread.start();
        } catch (Throwable error) {
            releaseRecorder();
            listener.onError("Falha ao abrir o microfone: " + error.getMessage());
        }
    }

    private void enableAudioEffects(int sessionId) {
        releaseEffects();
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId);
                if (echoCanceler != null) echoCanceler.setEnabled(true);
            }
        } catch (Throwable ignored) {
            echoCanceler = null;
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
            }
        } catch (Throwable ignored) {
            noiseSuppressor = null;
        }
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        final short[] buffer = new short[1600];
        while (running) {
            AudioRecord current = recorder;
            if (current == null) break;
            int read;
            try {
                read = current.read(buffer, 0, buffer.length);
            } catch (Throwable error) {
                if (running) listener.onError("Erro lendo áudio: " + error.getMessage());
                break;
            }
            if (read > 0) {
                short[] copy = new short[read];
                System.arraycopy(buffer, 0, copy, 0, read);
                listener.onAudio(copy, read, rmsDb(copy, read));
            } else if (read < 0 && running) {
                listener.onError("Erro de captura: código " + read);
                break;
            }
        }
        synchronized (this) {
            running = false;
            releaseRecorder();
        }
        listener.onStopped();
    }

    private static float rmsDb(short[] data, int count) {
        if (count <= 0) return -120f;
        double sum = 0;
        for (int i = 0; i < count; i++) {
            double n = data[i] / 32768.0;
            sum += n * n;
        }
        double rms = Math.sqrt(sum / count);
        if (rms <= 0.000001) return -120f;
        return (float) (20.0 * Math.log10(rms));
    }

    public synchronized void stop() {
        if (!running && recorder == null) return;
        running = false;
        AudioRecord current = recorder;
        if (current != null) {
            try { current.stop(); } catch (Throwable ignored) {}
        }
    }

    private void releaseRecorder() {
        releaseEffects();
        AudioRecord current = recorder;
        recorder = null;
        if (current != null) {
            try { current.release(); } catch (Throwable ignored) {}
        }
    }

    private void releaseEffects() {
        AcousticEchoCanceler aec = echoCanceler;
        echoCanceler = null;
        if (aec != null) {
            try { aec.setEnabled(false); } catch (Throwable ignored) {}
            try { aec.release(); } catch (Throwable ignored) {}
        }
        NoiseSuppressor ns = noiseSuppressor;
        noiseSuppressor = null;
        if (ns != null) {
            try { ns.setEnabled(false); } catch (Throwable ignored) {}
            try { ns.release(); } catch (Throwable ignored) {}
        }
    }
}

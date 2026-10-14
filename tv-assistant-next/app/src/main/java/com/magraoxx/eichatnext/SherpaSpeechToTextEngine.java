package com.magraoxx.eichatnext;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SherpaSpeechToTextEngine implements SpeechToTextEngine {
    private final Object lock = new Object();
    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();
    private final File modelDir;
    private Listener listener;
    private OfflineRecognizer recognizer;
    private OfflineStream stream;
    private boolean released;

    public SherpaSpeechToTextEngine(File modelDir) {
        this.modelDir = modelDir;
    }

    @Override public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean hasModelFiles() {
        return encoder().isFile() && decoder().isFile() && tokens().isFile();
    }

    public boolean initialize() {
        synchronized (lock) {
            if (released) return false;
            if (recognizer != null) return true;
            if (!hasModelFiles()) {
                emitError("Modelo Whisper português ainda não foi instalado.");
                return false;
            }
            try {
                FeatureConfig feature = new FeatureConfig();
                feature.setSampleRate(16000);
                feature.setFeatureDim(80);

                OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
                whisper.setEncoder(encoder().getAbsolutePath());
                whisper.setDecoder(decoder().getAbsolutePath());
                whisper.setLanguage("pt");
                whisper.setTask("transcribe");

                OfflineModelConfig model = new OfflineModelConfig();
                model.setWhisper(whisper);
                model.setTokens(tokens().getAbsolutePath());
                model.setModelType("whisper");
                model.setNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
                model.setProvider("cpu");

                OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                config.setFeatConfig(feature);
                config.setModelConfig(model);
                config.setDecodingMethod("greedy_search");

                recognizer = new OfflineRecognizer(null, config);
                return true;
            } catch (Throwable error) {
                emitError("Falha ao iniciar STT local: " + error.getMessage());
                return false;
            }
        }
    }

    @Override public void startUtterance() {
        synchronized (lock) {
            releaseStreamLocked();
            if (!initialize()) return;
            try {
                stream = recognizer.createStream();
            } catch (Throwable error) {
                emitError("Falha ao abrir turno de voz: " + error.getMessage());
            }
        }
    }

    @Override public void acceptPcm(short[] samples, int count, int sampleRate) {
        if (samples == null || count <= 0) return;
        OfflineStream current;
        synchronized (lock) {
            current = stream;
        }
        if (current == null) return;
        int n = Math.min(count, samples.length);
        float[] normalized = new float[n];
        for (int i = 0; i < n; i++) normalized[i] = samples[i] / 32768.0f;
        try {
            current.acceptWaveform(normalized, sampleRate);
        } catch (Throwable error) {
            emitError("Falha alimentando STT: " + error.getMessage());
        }
    }

    @Override public void finishUtterance() {
        final OfflineStream finished;
        final OfflineRecognizer currentRecognizer;
        synchronized (lock) {
            finished = stream;
            stream = null;
            currentRecognizer = recognizer;
        }
        if (finished == null || currentRecognizer == null) return;
        decoderExecutor.execute(() -> {
            try {
                currentRecognizer.decode(finished);
                OfflineRecognizerResult result = currentRecognizer.getResult(finished);
                String text = result == null ? "" : result.getText().trim();
                Listener current = listener;
                if (current != null && !text.isEmpty()) current.onFinal(text);
            } catch (Throwable error) {
                emitError("Falha decodificando fala: " + error.getMessage());
            } finally {
                try { finished.release(); } catch (Throwable ignored) {}
            }
        });
    }

    @Override public void reset() {
        synchronized (lock) {
            releaseStreamLocked();
        }
    }

    @Override public void release() {
        synchronized (lock) {
            released = true;
            releaseStreamLocked();
            if (recognizer != null) {
                try { recognizer.release(); } catch (Throwable ignored) {}
                recognizer = null;
            }
        }
        decoderExecutor.shutdownNow();
    }

    private void releaseStreamLocked() {
        if (stream != null) {
            try { stream.release(); } catch (Throwable ignored) {}
            stream = null;
        }
    }

    private File encoder() { return new File(modelDir, "tiny-encoder.int8.onnx"); }
    private File decoder() { return new File(modelDir, "tiny-decoder.int8.onnx"); }
    private File tokens() { return new File(modelDir, "tiny-tokens.txt"); }

    private void emitError(String message) {
        Listener current = listener;
        if (current != null) current.onError(message);
    }
}

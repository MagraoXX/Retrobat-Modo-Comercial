package com.magraoxx.eichatnext;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LanKokoroTextToSpeechEngine implements TextToSpeechEngine {
    private final Context context;
    private final String endpoint;
    private final String voice;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private Listener listener;
    private Future<?> activeJob;
    private HttpURLConnection activeConnection;
    private MediaPlayer player;
    private volatile boolean speaking;
    private volatile long generation;

    public LanKokoroTextToSpeechEngine(Context context, String endpoint, String voice) {
        this.context = context.getApplicationContext();
        this.endpoint = endpoint;
        this.voice = voice == null || voice.trim().isEmpty() ? "pm_alex" : voice.trim();
    }

    @Override public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        stop();
        final long mine;
        synchronized (lock) {
            mine = ++generation;
            activeJob = executor.submit(() -> synthesizeAndPlay(text.trim(), mine));
        }
    }

    private void synthesizeAndPlay(String text, long mine) {
        HttpURLConnection connection = null;
        File output = new File(context.getCacheDir(), "ei_chat_reply_" + mine + ".wav");
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            synchronized (lock) {
                if (mine != generation) return;
                activeConnection = connection;
            }
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject body = new JSONObject()
                    .put("input", text)
                    .put("voice", voice)
                    .put("speed", 1.04);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                emitError("Servidor de voz respondeu HTTP " + status);
                return;
            }

            try (InputStream input = connection.getInputStream(); FileOutputStream file = new FileOutputStream(output)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (mine != generation || Thread.currentThread().isInterrupted()) return;
                    file.write(buffer, 0, read);
                }
            }
            if (mine != generation) return;
            main.post(() -> play(output, mine));
        } catch (Throwable error) {
            if (mine == generation && !Thread.currentThread().isInterrupted()) {
                emitError("Voz local indisponível: " + error.getMessage());
            }
        } finally {
            synchronized (lock) {
                if (activeConnection == connection) activeConnection = null;
            }
            if (connection != null) connection.disconnect();
        }
    }

    private void play(File file, long mine) {
        if (mine != generation || !file.isFile()) return;
        releasePlayer();
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setDataSource(file.getAbsolutePath());
            next.setOnPreparedListener(mp -> {
                if (mine != generation) {
                    releasePlayer();
                    return;
                }
                speaking = true;
                Listener current = listener;
                if (current != null) current.onStart();
                mp.start();
            });
            next.setOnCompletionListener(mp -> {
                if (mine != generation) return;
                speaking = false;
                releasePlayer();
                Listener current = listener;
                if (current != null) current.onDone();
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (mine == generation) emitError("Falha reproduzindo voz: " + what + "/" + extra);
                speaking = false;
                releasePlayer();
                return true;
            });
            next.prepareAsync();
        } catch (Throwable error) {
            speaking = false;
            releasePlayer();
            emitError("Falha abrindo áudio da voz: " + error.getMessage());
        }
    }

    @Override public void stop() {
        HttpURLConnection connection;
        Future<?> job;
        synchronized (lock) {
            generation++;
            connection = activeConnection;
            activeConnection = null;
            job = activeJob;
            activeJob = null;
        }
        if (connection != null) {
            try { connection.disconnect(); } catch (Throwable ignored) {}
        }
        if (job != null) job.cancel(true);
        main.post(() -> {
            speaking = false;
            releasePlayer();
        });
    }

    private void releasePlayer() {
        MediaPlayer current = player;
        player = null;
        if (current != null) {
            try { if (current.isPlaying()) current.stop(); } catch (Throwable ignored) {}
            try { current.release(); } catch (Throwable ignored) {}
        }
    }

    private void emitError(String message) {
        main.post(() -> {
            Listener current = listener;
            if (current != null) current.onError(message);
        });
    }

    @Override public boolean isSpeaking() { return speaking; }

    @Override public void release() {
        stop();
        executor.shutdownNow();
    }
}

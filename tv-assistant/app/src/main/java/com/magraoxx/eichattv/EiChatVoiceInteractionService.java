package com.magraoxx.eichattv;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

public class EiChatVoiceInteractionService extends VoiceInteractionService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer wakeSpeech;
    private boolean serviceReady;
    private boolean wakeTriggered;

    private final Runnable restartWake = new Runnable() {
        @Override public void run() { startWakeRecognition(); }
    };

    private final Runnable waitForSessionToClose = new Runnable() {
        @Override public void run() {
            if (!serviceReady) return;
            boolean active = getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                    .getBoolean("session_active", false);
            if (active) {
                main.postDelayed(this, 900);
            } else {
                wakeTriggered = false;
                startWakeRecognition();
            }
        }
    };

    @Override
    public void onReady() {
        super.onReady();
        serviceReady = true;
        android.content.SharedPreferences prefs = getSharedPreferences("ei_chat_runtime", MODE_PRIVATE);
        android.content.SharedPreferences.Editor edit = prefs.edit().putLong("voice_service_ready_at", System.currentTimeMillis());
        if (!prefs.getBoolean("wake_word_migrated_v25", false)) {
            edit.putBoolean("wake_word_enabled", false).putBoolean("wake_word_migrated_v25", true);
        }
        edit.apply();
        if (prefs.getBoolean("wake_word_enabled", false)) main.postDelayed(this::startWakeRecognition, 700);
    }

    @Override
    public void onShutdown() {
        serviceReady = false;
        main.removeCallbacksAndMessages(null);
        destroyWakeRecognition();
        super.onShutdown();
    }

    private void startWakeRecognition() {
        if (!serviceReady || wakeTriggered) return;
        if (!getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                .getBoolean("wake_word_enabled", true)) return;
        if (getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                .getBoolean("session_active", false)) {
            scheduleWake(900);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            noteState("sem permissao de microfone", -100);
            scheduleWake(4000);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            noteState("reconhecimento indisponivel", -101);
            scheduleWake(5000);
            return;
        }

        destroyWakeRecognition();
        wakeSpeech = SpeechRecognizer.createSpeechRecognizer(this);
        wakeSpeech.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { noteState("ouvindo palavra de ativacao", 0); }
            @Override public void onBeginningOfSpeech() { noteState("fala detectada", 0); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { noteState("processando palavra de ativacao", 0); }
            @Override public void onError(int error) {
                noteState("erro reconhecimento " + error, error);
                scheduleWake(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1300 : 550);
            }
            @Override public void onResults(Bundle results) {
                if (!handleRecognition(results)) scheduleWake(180);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                handleRecognition(partialResults);
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            wakeSpeech.startListening(i);
        } catch (Throwable t) {
            noteState("falha ao iniciar escuta", -102);
            scheduleWake(1200);
        }
    }

    private boolean handleRecognition(Bundle b) {
        if (wakeTriggered || b == null) return wakeTriggered;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return false;
        for (String raw : list) {
            WakeMatch match = parseWake(raw);
            if (match != null) {
                triggerWake(raw, match.command);
                return true;
            }
        }
        return false;
    }

    private void triggerWake(String raw, String command) {
        wakeTriggered = true;
        destroyWakeRecognition();
        getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                .edit()
                .putLong("last_wake_at", System.currentTimeMillis())
                .putString("last_wake_text", raw == null ? "" : raw)
                .putString("wake_state", "ativado")
                .apply();

        Bundle args = new Bundle();
        args.putBoolean("continuous", true);
        args.putBoolean("request_screen_context", true);
        if (command != null && !command.isEmpty()) args.putString("initial_query", command);
        try {
            int flags = VoiceInteractionSession.SHOW_WITH_ASSIST | VoiceInteractionSession.SHOW_WITH_SCREENSHOT;
            showSession(args, flags);
        } catch (Throwable t) {
            noteState("falha ao abrir painel", -103);
            wakeTriggered = false;
            scheduleWake(1200);
            return;
        }
        main.removeCallbacks(waitForSessionToClose);
        main.postDelayed(waitForSessionToClose, 1200);
    }

    private void scheduleWake(long delay) {
        destroyWakeRecognition();
        main.removeCallbacks(restartWake);
        if (serviceReady && !wakeTriggered) main.postDelayed(restartWake, delay);
    }

    private void destroyWakeRecognition() {
        if (wakeSpeech != null) {
            try { wakeSpeech.cancel(); } catch (Throwable ignored) {}
            try { wakeSpeech.destroy(); } catch (Throwable ignored) {}
            wakeSpeech = null;
        }
    }

    private void noteState(String state, int error) {
        getSharedPreferences("ei_chat_runtime", MODE_PRIVATE)
                .edit()
                .putString("wake_state", state)
                .putInt("wake_last_error", error)
                .putLong("wake_state_at", System.currentTimeMillis())
                .apply();
    }

    private static WakeMatch parseWake(String raw) {
        String n = norm(raw);
        if (n.equals("chat") || n.equals("ei chat") || n.equals("hey chat")) return new WakeMatch("");
        String[] prefixes = new String[]{"ei chat ", "hey chat ", "chat "};
        for (String prefix : prefixes) {
            if (n.startsWith(prefix) && n.length() > prefix.length()) {
                return new WakeMatch(n.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static final class WakeMatch {
        final String command;
        WakeMatch(String command) { this.command = command; }
    }
}

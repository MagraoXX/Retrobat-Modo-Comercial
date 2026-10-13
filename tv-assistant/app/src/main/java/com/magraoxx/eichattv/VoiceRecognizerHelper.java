package com.magraoxx.eichattv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

public final class VoiceRecognizerHelper {
    private static final String PREFS = "ei_chat_voice";
    private static final String TAG = "EiChatVoice";
    private static final String VOICE_RECOGNITION_SERVICE = "voice_recognition_service";

    private VoiceRecognizerHelper() {}

    public static SpeechRecognizer create(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(), VOICE_RECOGNITION_SERVICE);
        if (flat != null && !flat.trim().isEmpty()) {
            ComponentName component = ComponentName.unflattenFromString(flat.trim());
            if (component != null) {
                try {
                    record(context, "recognizer_component", flat.trim());
                    return SpeechRecognizer.createSpeechRecognizer(context, component);
                } catch (Throwable t) {
                    record(context, "recognizer_component_error", safe(t));
                }
            }
        }
        record(context, "recognizer_component", "system-default");
        return SpeechRecognizer.createSpeechRecognizer(context);
    }

    public static Intent intent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L);
        return i;
    }

    public static void record(Context context, String key, String value) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_" + key, value == null ? "" : value)
                    .putLong("last_event_at", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {}
        Log.d(TAG, key + "=" + value);
    }

    public static String diagnostic(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String recognizer = p.getString("last_recognizer_component", "desconhecido");
        String event = p.getString("last_event", "nenhum");
        String error = p.getString("last_error", "nenhum");
        String partial = p.getString("last_partial", "");
        String result = p.getString("last_result", "");
        return "Serviço de voz: " + recognizer +
                "\nÚltimo evento: " + event +
                "\nÚltimo erro: " + error +
                (partial.isEmpty() ? "" : "\nParcial: " + partial) +
                (result.isEmpty() ? "" : "\nResultado: " + result);
    }

    public static String errorName(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_AUDIO: return "ERROR_AUDIO";
            case SpeechRecognizer.ERROR_CLIENT: return "ERROR_CLIENT";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "ERROR_INSUFFICIENT_PERMISSIONS";
            case SpeechRecognizer.ERROR_NETWORK: return "ERROR_NETWORK";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "ERROR_NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH: return "ERROR_NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "ERROR_RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_SERVER: return "ERROR_SERVER";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "ERROR_SPEECH_TIMEOUT";
            default: return "ERROR_" + e;
        }
    }

    private static String safe(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return m == null ? (t == null ? "erro desconhecido" : t.getClass().getSimpleName()) : m;
    }
}

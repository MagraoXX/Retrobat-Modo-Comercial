package com.magraoxx.eichattv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

public class EiChatSession extends android.service.voice.VoiceInteractionSession {
    private TextView title, status, pulse;
    private SpeechRecognizer speech;
    private TextToSpeech tts;
    private boolean ttsReady, continuous;
    private String pendingSpeech;
    private Runnable pendingAfter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CommandEngine engine;
    private OpenAiClient ai;
    private ChatGptAuth auth;

    public EiChatSession(Context c) {
        super(c);
        engine = new CommandEngine(c);
        ai = new OpenAiClient(c);
        auth = new ChatGptAuth(c);
    }

    private int dp(int v) { return Math.round(v * getContext().getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); if (stroke != 0) g.setStroke(dp(1), stroke); return g;
    }
    private TextView tv(String s, float size) {
        TextView v = new TextView(getContext()); v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }

    @Override public View onCreateContentView() {
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        LinearLayout shell = new LinearLayout(getContext()); shell.setOrientation(LinearLayout.VERTICAL); shell.setGravity(Gravity.CENTER_HORIZONTAL); shell.setPadding(dp(28), 0, dp(28), dp(34));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(20), dp(15), dp(20), dp(15)); box.setBackground(bg(Color.rgb(31, 31, 31), 28, Color.rgb(72, 72, 72)));
        shell.addView(box, new LinearLayout.LayoutParams(-1, -2));

        pulse = tv("●", 30); pulse.setTextColor(Color.rgb(120, 185, 255)); pulse.setGravity(Gravity.CENTER); box.addView(pulse, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout words = new LinearLayout(getContext()); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(8), 0, dp(8), 0); box.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        title = tv("Ei Chat", 18); title.setTypeface(null, 1); words.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = tv("Pronto", 16); status.setTextColor(Color.rgb(220, 220, 220)); status.setMaxLines(4); words.addView(status, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = tv("Diga “encerrar chamada” para sair", 12); hint.setTextColor(Color.rgb(145, 145, 145)); hint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); box.addView(hint, new LinearLayout.LayoutParams(-2, dp(56)));
        return shell;
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        if (title == null) setContentView(onCreateContentView());
        continuous = args != null && args.getBoolean("continuous", false);
        startListening();
    }

    private void set(String t, String s, int color) {
        if (title != null) title.setText(t);
        if (status != null) status.setText(s);
        if (pulse != null) pulse.setTextColor(color);
    }

    private void startListening() {
        if (!auth.isConnected()) { set("Ei Chat", "Abra o aplicativo e conecte sua conta ChatGPT primeiro.", Color.rgb(255, 180, 80)); speak("Abra o Ei Chat e conecte sua conta ChatGPT primeiro.", this::hide); return; }
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { set("Ei Chat", "Permissão de microfone necessária.", Color.rgb(255, 180, 80)); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) { set("Ei Chat", "Reconhecimento de voz indisponível nesta TV.", Color.rgb(255, 105, 105)); return; }
        destroySpeech();
        speech = SpeechRecognizer.createSpeechRecognizer(getContext());
        speech.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { set("Ei Chat • ouvindo", "Pode falar…", Color.rgb(120, 185, 255)); }
            public void onBeginningOfSpeech() { set("Ei Chat • ouvindo", "Estou ouvindo você…", Color.rgb(120, 185, 255)); }
            public void onRmsChanged(float r) { if (pulse != null) pulse.setText(r > 2f ? "●" : "•"); }
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { set("Ei Chat • entendendo", "Entendendo sua voz…", Color.rgb(190, 150, 255)); }
            public void onError(int e) { set("Ei Chat", "Não consegui ouvir. Tente novamente.", Color.rgb(255, 105, 105)); if (continuous) main.postDelayed(() -> startListening(), 900); }
            public void onResults(Bundle b) {
                ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (a == null || a.isEmpty() || a.get(0).trim().isEmpty()) { set("Ei Chat", "Não entendi.", Color.rgb(255, 180, 80)); if (continuous) main.postDelayed(() -> startListening(), 700); return; }
                handle(a.get(0).trim());
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
        android.content.Intent i = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3); i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { speech.startListening(i); } catch (Throwable e) { set("Ei Chat", "Falha ao abrir o microfone.", Color.rgb(255, 105, 105)); }
    }

    private void handle(String q) {
        String n = norm(q);
        if (n.contains("encerrar chamada") || n.contains("finalizar chamada") || n.equals("encerrar")) {
            continuous = false; destroySpeech(); set("Ei Chat", "Chamada encerrada.", Color.rgb(130, 220, 165)); speak("Chamada encerrada.", this::hide); return;
        }
        if ((n.contains("iniciar chamada") || n.contains("inicie chamada")) && n.contains("voz")) {
            continuous = true; set("Ei Chat • chamada", "Conversa contínua ativada.", Color.rgb(130, 220, 165)); speak("Chamada de voz iniciada. Pode falar comigo sem repetir o comando.", this::startListening); return;
        }

        set("Você", q, Color.rgb(120, 185, 255));
        CommandEngine.Result r = engine.execute(q);
        if (r.handled) { set("Ei Chat", r.message, Color.rgb(130, 220, 165)); speak(r.message, afterReply()); return; }
        set("Ei Chat • pensando", "Analisando…", Color.rgb(190, 150, 255));
        ai.ask(q, new OpenAiClient.Callback() {
            public void onSuccess(String x) { getContext().getMainExecutor().execute(() -> { set("Ei Chat", x, Color.rgb(130, 220, 165)); speak(x, afterReply()); }); }
            public void onError(String x) { getContext().getMainExecutor().execute(() -> { set("Ei Chat", x, Color.rgb(255, 105, 105)); if (continuous) main.postDelayed(() -> startListening(), 900); }); }
        });
    }

    private Runnable afterReply() {
        return () -> {
            if (continuous) main.postDelayed(() -> startListening(), 300);
            else main.postDelayed(this::hide, 2600);
        };
    }

    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(getContext(), x -> {
            if (x == TextToSpeech.SUCCESS) {
                ttsReady = true; tts.setLanguage(new Locale("pt", "BR")); tts.setSpeechRate(1.02f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { Runnable r = pendingAfter; pendingAfter = null; if (r != null) main.post(r); }
                    @Override public void onError(String id) { Runnable r = pendingAfter; pendingAfter = null; if (r != null) main.post(r); }
                });
                String p = pendingSpeech; pendingSpeech = null; if (p != null) speak(p, pendingAfter);
            }
        });
    }
    private void speak(String s, Runnable after) {
        if (s == null || s.trim().isEmpty()) { if (after != null) after.run(); return; }
        pendingAfter = after;
        if (!ttsReady) { pendingSpeech = s; initTts(); return; }
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ei_chat_session");
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
    private void destroySpeech() { if (speech != null) { try { speech.cancel(); } catch (Throwable ignored) {} try { speech.destroy(); } catch (Throwable ignored) {} speech = null; } }
    @Override public void onHide() { destroySpeech(); if (tts != null) try { tts.stop(); } catch (Throwable ignored) {} super.onHide(); }
    @Override public void onDestroy() { destroySpeech(); if (tts != null) tts.shutdown(); super.onDestroy(); }
}

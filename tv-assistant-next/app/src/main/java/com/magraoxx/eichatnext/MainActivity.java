package com.magraoxx.eichatnext;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;

    private TextView stateLabel;
    private TextView transcript;
    private TextView orb;
    private EditText composer;
    private LinearLayout conversation;
    private Button talkButton;

    private ContinuousAudioEngine audioEngine;
    private VoicePipeline voicePipeline;
    private OllamaAssistantRouter assistantRouter;
    private LanKokoroTextToSpeechEngine ttsEngine;
    private VoiceSessionController voiceSession;
    private LocalTvCommandEngine localCommands;
    private final VoiceStateMachine voice = new VoiceStateMachine();

    private ConversationStore history;
    private long requestVersion;
    private boolean showingWelcome = true;
    private boolean restoringHistory;
    private long lastAudioUiAt;
    private long lastBackAt;
    private String lastSpokenText = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        history = new ConversationStore(this);
        setContentView(buildUi());
        restoreConversation();
        voice.setListener(this::renderVoiceState);

        localCommands = new LocalTvCommandEngine(this);
        assistantRouter = new OllamaAssistantRouter("http://192.168.10.17:11434", "qwen3-vl:4b");
        ttsEngine = new LanKokoroTextToSpeechEngine(
                this,
                "http://192.168.10.17:8880/v1/audio/speech",
                "pm_alex");
        ttsEngine.setListener(new TextToSpeechEngine.Listener() {
            @Override public void onStart() {
                runOnUiThread(() -> voice.onSpeaking(lastSpokenText.isEmpty() ? "Respondendo…" : lastSpokenText));
            }
            @Override public void onDone() {
                runOnUiThread(() -> {
                    if (voiceSession != null && voiceSession.isActive()) {
                        voice.onListening("Pode continuar falando.");
                    } else {
                        voice.reset();
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> voice.onError(message));
            }
        });

        voiceSession = new VoiceSessionController(new VoiceSessionController.Listener() {
            @Override public void onActivated(String phrase) {
                runOnUiThread(() -> voice.onListening("Chamada ativa. Pode falar normalmente."));
            }
            @Override public void onCommand(String command) {
                runOnUiThread(() -> submitQuery(command, true));
            }
            @Override public void onEnded() {
                runOnUiThread(() -> {
                    requestVersion++;
                    if (assistantRouter != null) assistantRouter.cancel();
                    if (ttsEngine != null) ttsEngine.stop();
                    voice.reset();
                });
            }
            @Override public void onIgnored(String ignored) {
                runOnUiThread(() -> renderVoiceState(VoiceStateMachine.State.IDLE, "Aguardando “Ei Chat”…"));
            }
        });

        voicePipeline = new VoicePipeline(this, new VoicePipeline.Listener() {
            @Override public void onSpeechStarted() {
                if (ttsEngine != null && ttsEngine.isSpeaking()) {
                    ttsEngine.stop();
                    runOnUiThread(voice::onBargeIn);
                } else if (voiceSession != null && voiceSession.isActive()) {
                    runOnUiThread(() -> voice.onListening("Estou ouvindo…"));
                }
            }
            @Override public void onTranscript(String text) {
                if (voiceSession != null) voiceSession.onTranscript(text);
            }
            @Override public void onSpeechSegmentWithoutStt(long durationMs) {
                runOnUiThread(() -> voice.onThinking("Fala detectada • " + durationMs + " ms"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> voice.onError(message));
            }
        });
        voicePipeline.start();

        audioEngine = new ContinuousAudioEngine(this, new ContinuousAudioEngine.Listener() {
            @Override public void onAudio(short[] samples, int count, float rmsDb) {
                if (voicePipeline != null) {
                    voicePipeline.acceptPcm(samples, count, ContinuousAudioEngine.SAMPLE_RATE, rmsDb);
                }
                long now = System.currentTimeMillis();
                if (now - lastAudioUiAt < 250) return;
                lastAudioUiAt = now;
                runOnUiThread(() -> animateMic(rmsDb));
            }
            @Override public void onStarted() {
                runOnUiThread(() -> {
                    if (voiceSession != null && voiceSession.isActive()) {
                        voice.onListening("Microfone contínuo ativo.");
                    } else {
                        renderVoiceState(VoiceStateMachine.State.IDLE, "Aguardando “Ei Chat”…");
                    }
                    if (talkButton != null) talkButton.setText("Pausar microfone");
                });
            }
            @Override public void onStopped() {
                runOnUiThread(() -> {
                    voice.reset();
                    if (talkButton != null) talkButton.setText("Ativar microfone");
                    if (orb != null) orb.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    voice.onError(message);
                    if (talkButton != null) talkButton.setText("Tentar novamente");
                });
            }
        });

        renderVoiceState(VoiceStateMachine.State.IDLE, "Diga “Ei Chat” quando quiser.");
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            audioEngine.start();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable box(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView label(String text, float size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private Button action(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setOnClickListener(click);
        b.setBackground(box(Color.rgb(42, 43, 48), 16, Color.rgb(65, 66, 72)));
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(box(
                    focused ? Color.rgb(62, 64, 72) : Color.rgb(42, 43, 48),
                    16,
                    focused ? Color.WHITE : Color.rgb(65, 66, 72)));
            b.setScaleX(focused ? 1.03f : 1f);
            b.setScaleY(focused ? 1.03f : 1f);
        });
        return b;
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 18, 22));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(22), dp(26), dp(18), dp(22));
        rail.setBackgroundColor(Color.rgb(22, 23, 28));
        shell.addView(rail, new LinearLayout.LayoutParams(dp(250), -1));

        TextView brand = label("✦  Ei Chat", 22, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        rail.addView(brand, new LinearLayout.LayoutParams(-1, dp(54)));
        rail.addView(label("Assistente da sua TV", 12, Color.rgb(145, 149, 160)), new LinearLayout.LayoutParams(-1, dp(42)));

        Button newChat = action("＋  Novo chat", v -> clearConversation());
        rail.addView(newChat, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView recent = label("RECENTES", 11, Color.rgb(117, 121, 132));
        recent.setPadding(dp(4), dp(24), 0, dp(8));
        rail.addView(recent, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView placeholder = label("Conversa salva neste aparelho.\nSem login obrigatório.", 13, Color.rgb(150, 154, 165));
        placeholder.setLineSpacing(dp(4), 1.1f);
        rail.addView(placeholder, new LinearLayout.LayoutParams(-1, 0, 1f));
        rail.addView(label("Next 0.1 • local-first", 12, Color.rgb(112, 116, 128)), new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(34), dp(24), dp(34), dp(24));
        shell.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Novo chat", 17, Color.rgb(235, 236, 240));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView status = label("●  IA local", 12, Color.rgb(137, 221, 176));
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(status, new LinearLayout.LayoutParams(dp(140), dp(48)));
        main.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        conversation = new LinearLayout(this);
        conversation.setOrientation(LinearLayout.VERTICAL);
        conversation.setGravity(Gravity.CENTER_HORIZONTAL);
        conversation.setPadding(dp(42), dp(24), dp(42), dp(18));
        scroll.addView(conversation, new ScrollView.LayoutParams(-1, -2));
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        showWelcome();

        LinearLayout voiceCard = new LinearLayout(this);
        voiceCard.setGravity(Gravity.CENTER_VERTICAL);
        voiceCard.setPadding(dp(18), dp(10), dp(18), dp(10));
        voiceCard.setBackground(box(Color.rgb(28, 29, 35), 22, Color.rgb(51, 53, 62)));

        orb = label("✦", 24, Color.WHITE);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(box(Color.rgb(58, 66, 88), 24, 0));
        voiceCard.addView(orb, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout stateStack = new LinearLayout(this);
        stateStack.setOrientation(LinearLayout.VERTICAL);
        stateStack.setPadding(dp(14), 0, 0, 0);
        stateLabel = label("Em espera", 14, Color.WHITE);
        stateLabel.setTypeface(Typeface.DEFAULT_BOLD);
        transcript = label("Diga “Ei Chat” quando quiser.", 12, Color.rgb(161, 165, 176));
        stateStack.addView(stateLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        stateStack.addView(transcript, new LinearLayout.LayoutParams(-1, dp(22)));
        voiceCard.addView(stateStack, new LinearLayout.LayoutParams(0, dp(52), 1f));

        talkButton = action("Pausar microfone", v -> toggleAudio());
        voiceCard.addView(talkButton, new LinearLayout.LayoutParams(dp(170), dp(48)));
        main.addView(voiceCard, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout composerBar = new LinearLayout(this);
        composerBar.setGravity(Gravity.CENTER_VERTICAL);
        composerBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        composerBar.setBackground(box(Color.rgb(31, 32, 38), 24, Color.rgb(55, 57, 66)));

        composer = new EditText(this);
        composer.setHint("Pergunte qualquer coisa ao Ei Chat");
        composer.setHintTextColor(Color.rgb(128, 132, 143));
        composer.setTextColor(Color.WHITE);
        composer.setTextSize(15);
        composer.setSingleLine(false);
        composer.setMaxLines(3);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composerBar.addView(composer, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button send = action("Enviar", v -> sendMessage());
        composerBar.addView(send, new LinearLayout.LayoutParams(dp(110), dp(48)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(70));
        cp.setMargins(0, dp(14), 0, 0);
        main.addView(composerBar, cp);

        newChat.requestFocus();
        return root;
    }

    private void showWelcome() {
        showingWelcome = true;
        conversation.removeAllViews();
        TextView mark = label("✦", 42, Color.rgb(232, 234, 241));
        mark.setGravity(Gravity.CENTER);
        conversation.addView(mark, new LinearLayout.LayoutParams(-1, dp(80)));

        TextView hero = label("Como posso ajudar?", 30, Color.WHITE);
        hero.setTypeface(Typeface.DEFAULT_BOLD);
        hero.setGravity(Gravity.CENTER);
        conversation.addView(hero, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView sub = label("Texto, voz contínua e IA local. Diga “Ei Chat” para iniciar.", 15, Color.rgb(157, 161, 173));
        sub.setGravity(Gravity.CENTER);
        sub.setMaxWidth(dp(720));
        conversation.addView(sub, new LinearLayout.LayoutParams(-1, dp(70)));
    }

    private void restoreConversation() {
        org.json.JSONArray saved = history.snapshot();
        if (saved.length() == 0) return;
        conversation.removeAllViews();
        showingWelcome = false;
        restoringHistory = true;
        try {
            for (int i = 0; i < saved.length(); i++) {
                org.json.JSONObject message = saved.optJSONObject(i);
                if (message == null) continue;
                if ("user".equals(message.optString("role"))) {
                    appendUserMessage(message.optString("content"));
                } else {
                    appendAssistantMessage(message.optString("content"));
                }
            }
        } finally { restoringHistory = false; }
    }

    private void clearConversation() {
        requestVersion++;
        history.clear();
        if (ttsEngine != null) ttsEngine.stop();
        if (assistantRouter != null) assistantRouter.cancel();
        composer.setText("");
        showWelcome();
        if (voicePipeline != null) voicePipeline.reset();
        if (voiceSession != null) voiceSession.end();
        voice.reset();
    }

    private void sendMessage() {
        String text = composer.getText().toString().trim();
        if (!text.isEmpty()) submitQuery(text, false);
    }

    private void submitQuery(String text, boolean fromVoice) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = text.trim();
        final long mine = ++requestVersion;
        assistantRouter.cancel();
        if (ttsEngine != null) ttsEngine.stop();
        composer.setText("");
        if (showingWelcome) {
            conversation.removeAllViews();
            showingWelcome = false;
        }
        appendUserMessage(clean);
        if (fromVoice) voice.onThinking(clean);

        LocalTvCommandEngine.Result command = localCommands == null
                ? LocalTvCommandEngine.Result.unhandled()
                : localCommands.execute(clean);
        if (command.handled) {
            appendAssistantMessage(command.message);
            if (fromVoice && voiceSession != null && voiceSession.isActive() && ttsEngine != null) {
                lastSpokenText = command.message;
                ttsEngine.speak(command.message);
            } else if (fromVoice) {
                voice.onListening(command.message);
            }
            return;
        }

        assistantRouter.setHistory(history.snapshot());
        assistantRouter.ask(clean, null, new AssistantRouter.Callback() {
            @Override public void onAnswer(String answer) {
                runOnUiThread(() -> {
                    if (mine != requestVersion || isDestroyed()) return;
                    appendAssistantMessage(answer);
                    if (fromVoice && voiceSession != null && voiceSession.isActive()) {
                        lastSpokenText = answer;
                        ttsEngine.speak(answer);
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (mine != requestVersion || isDestroyed()) return;
                    appendAssistantMessage(message);
                    if (fromVoice) voice.onError(message);
                });
            }
        });
    }

    private void appendUserMessage(String text) {
        if (!restoringHistory) history.append("user", text);
        TextView user = label(text, 16, Color.WHITE);
        user.setPadding(dp(18), dp(12), dp(18), dp(12));
        user.setBackground(box(Color.rgb(49, 51, 60), 18, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.RIGHT;
        p.setMargins(dp(120), dp(20), 0, dp(14));
        conversation.addView(user, p);
    }

    private void appendAssistantMessage(String text) {
        if (!restoringHistory) history.append("assistant", text == null ? "" : text);
        TextView answer = label(text == null ? "" : text, 16, Color.rgb(230, 232, 238));
        answer.setLineSpacing(dp(3), 1.12f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(12), dp(100), 0);
        conversation.addView(answer, p);
    }

    private void toggleAudio() {
        if (audioEngine == null) return;
        if (audioEngine.isRunning()) {
            if (voiceSession != null) voiceSession.end();
            audioEngine.stop();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        audioEngine.start();
    }

    private void animateMic(float rmsDb) {
        if (orb == null) return;
        float level = Math.max(0f, Math.min(1f, (rmsDb + 55f) / 45f));
        float scale = 1f + (0.12f * level);
        orb.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
    }

    private void renderVoiceState(VoiceStateMachine.State state, String message) {
        if (stateLabel == null) return;
        stateLabel.setText(state.label);
        transcript.setText(message);
        int color = state == VoiceStateMachine.State.LISTENING ? Color.rgb(68, 119, 94)
                : state == VoiceStateMachine.State.SPEAKING ? Color.rgb(94, 86, 140)
                : state == VoiceStateMachine.State.THINKING ? Color.rgb(95, 89, 63)
                : Color.rgb(58, 66, 88);
        orb.setBackground(box(color, 24, 0));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_MIC && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && audioEngine != null) {
            audioEngine.start();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (voiceSession != null) voiceSession.activateManually();
            if (audioEngine != null && !audioEngine.isRunning()
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioEngine.start();
            }
            voice.simulateWake();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (voiceSession != null && voiceSession.isActive()) {
            voiceSession.end();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackAt <= 2000L) {
            finishAndRemoveTask();
            return;
        }
        lastBackAt = now;
        Toast.makeText(this, "Pressione Voltar novamente para sair do Ei Chat", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        requestVersion++;
        if (audioEngine != null) audioEngine.stop();
        if (voicePipeline != null) voicePipeline.release();
        if (assistantRouter != null) assistantRouter.release();
        if (ttsEngine != null) ttsEngine.release();
        super.onDestroy();
    }
}
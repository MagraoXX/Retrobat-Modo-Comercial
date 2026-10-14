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

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private TextView stateLabel;
    private TextView transcript;
    private TextView orb;
    private EditText composer;
    private LinearLayout conversation;
    private ContinuousAudioEngine audioEngine;
    private VoicePipeline voicePipeline;
    private OllamaAssistantRouter assistantRouter;
    private Button talkButton;
    private long lastAudioUiAt;
    private final VoiceStateMachine voice = new VoiceStateMachine();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        assistantRouter = new OllamaAssistantRouter("http://192.168.10.17:11434", "qwen3-vl:4b");
        voicePipeline = new VoicePipeline(this, new VoicePipeline.Listener() {
            @Override public void onSpeechStarted() {
                runOnUiThread(() -> voice.onListening("Fala detectada…"));
            }
            @Override public void onTranscript(String text) {
                runOnUiThread(() -> submitQuery(text, true));
            }
            @Override public void onSpeechSegmentWithoutStt(long durationMs) {
                runOnUiThread(() -> voice.onThinking("Fala detectada • " + durationMs + " ms • aguardando modelo STT"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> voice.onError(message));
            }
        });
        voicePipeline.start();
        audioEngine = new ContinuousAudioEngine(this, new ContinuousAudioEngine.Listener() {
            @Override public void onAudio(short[] samples, int count, float rmsDb) {
                if (voicePipeline != null) voicePipeline.acceptPcm(samples, count, ContinuousAudioEngine.SAMPLE_RATE, rmsDb);
                long now = System.currentTimeMillis();
                if (now - lastAudioUiAt < 250) return;
                lastAudioUiAt = now;
                runOnUiThread(() -> {
                    if (transcript != null) transcript.setText("Microfone estável • " + Math.round(rmsDb) + " dB");
                    if (orb != null) {
                        float level = Math.max(0f, Math.min(1f, (rmsDb + 55f) / 45f));
                        float scale = 1f + (0.12f * level);
                        orb.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
                    }
                });
            }
            @Override public void onStarted() {
                runOnUiThread(() -> {
                    voice.onListening("Microfone contínuo ativo. Pode falar.");
                    if (talkButton != null) talkButton.setText("Parar");
                });
            }
            @Override public void onStopped() {
                runOnUiThread(() -> {
                    voice.reset();
                    if (talkButton != null) talkButton.setText("Falar agora");
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
        voice.setListener(this::renderVoiceState);
        renderVoiceState(VoiceStateMachine.State.IDLE, "Diga “Ei Chat” quando quiser.");
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
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
            b.setBackground(box(focused ? Color.rgb(62, 64, 72) : Color.rgb(42, 43, 48), 16,
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

        TextView placeholder = label("Esta versão começa limpa.\nO histórico local será integrado aqui.", 13, Color.rgb(150, 154, 165));
        placeholder.setLineSpacing(dp(4), 1.1f);
        rail.addView(placeholder, new LinearLayout.LayoutParams(-1, 0, 1f));
        rail.addView(label("Next 0.1 • sem login", 12, Color.rgb(112, 116, 128)), new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(34), dp(24), dp(34), dp(24));
        shell.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Novo chat", 17, Color.rgb(235, 236, 240));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView status = label("●  local-first", 12, Color.rgb(137, 221, 176));
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
        talkButton = action("Falar agora", v -> toggleAudio());
        voiceCard.addView(talkButton, new LinearLayout.LayoutParams(dp(150), dp(48)));
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
        conversation.removeAllViews();
        TextView mark = label("✦", 42, Color.rgb(232, 234, 241));
        mark.setGravity(Gravity.CENTER);
        conversation.addView(mark, new LinearLayout.LayoutParams(-1, dp(80)));
        TextView hero = label("Como posso ajudar?", 30, Color.WHITE);
        hero.setTypeface(Typeface.DEFAULT_BOLD);
        hero.setGravity(Gravity.CENTER);
        conversation.addView(hero, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView sub = label("Converse por texto ou diga “Ei Chat”. A voz contínua entra na próxima etapa desta base.", 15, Color.rgb(157, 161, 173));
        sub.setGravity(Gravity.CENTER);
        sub.setMaxWidth(dp(720));
        conversation.addView(sub, new LinearLayout.LayoutParams(-1, dp(70)));
    }

    private void clearConversation() {
        composer.setText("");
        showWelcome();
        voice.reset();
        if (voicePipeline != null) voicePipeline.reset();
    }

    private void sendMessage() {
        String text = composer.getText().toString().trim();
        if (text.isEmpty()) return;
        submitQuery(text, false);
    }

    private void submitQuery(String text, boolean fromVoice) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = text.trim();
        composer.setText("");
        conversation.removeAllViews();
        appendUserMessage(clean);
        if (fromVoice) voice.onThinking(clean);
        assistantRouter.ask(clean, null, new AssistantRouter.Callback() {
            @Override public void onAnswer(String answer) {
                runOnUiThread(() -> {
                    appendAssistantMessage(answer);
                    if (fromVoice) renderVoiceState(VoiceStateMachine.State.IDLE, "Resposta pronta • TTS natural será conectado em seguida.");
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    appendAssistantMessage(message);
                    if (fromVoice) voice.onError(message);
                });
            }
        });
    }

    private void appendUserMessage(String text) {
        TextView user = label(text, 16, Color.WHITE);
        user.setPadding(dp(18), dp(12), dp(18), dp(12));
        user.setBackground(box(Color.rgb(49, 51, 60), 18, 0));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
        up.gravity = Gravity.RIGHT;
        up.setMargins(dp(120), dp(20), 0, dp(14));
        conversation.addView(user, up);
    }

    private void appendAssistantMessage(String text) {
        TextView answer = label(text == null ? "" : text, 16, Color.rgb(230, 232, 238));
        answer.setLineSpacing(dp(3), 1.12f);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.setMargins(0, dp(12), dp(100), 0);
        conversation.addView(answer, ap);
    }

    private void toggleAudio() {
        if (audioEngine == null) return;
        if (audioEngine.isRunning()) {
            audioEngine.stop();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        voice.simulateWake();
        audioEngine.start();
    }

    private void renderVoiceState(VoiceStateMachine.State state, String message) {
        if (stateLabel == null) return;
        stateLabel.setText(state.label);
        transcript.setText(message);
        int color = state == VoiceStateMachine.State.LISTENING ? Color.rgb(68, 119, 94)
                : state == VoiceStateMachine.State.SPEAKING ? Color.rgb(94, 86, 140)
                : Color.rgb(58, 66, 88);
        orb.setBackground(box(color, 24, 0));
    }

    @Override protected void onDestroy() {
        if (audioEngine != null) audioEngine.stop();
        if (voicePipeline != null) voicePipeline.release();
        if (assistantRouter != null) assistantRouter.release();
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ASSIST) {
            voice.simulateWake();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

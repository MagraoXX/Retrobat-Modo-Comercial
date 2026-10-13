package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private static final int REQ_ASSISTANT = 1002;

    private TextView caption, micText, accountState;
    private Button listen, account;
    private EditText input;
    private LinearLayout chat;
    private ScrollView chatScroll;
    private TextToSpeech tts;
    private CommandEngine engine;
    private OpenAiClient ai;
    private ChatGptAuth auth;
    private SpeechRecognizer speech;
    private boolean pending, audioRetry;
    private long lastBackAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        engine = new CommandEngine(this);
        ai = new OpenAiClient(this);
        auth = new ChatGptAuth(this);
        buildUi();
        refreshAccount();
        tts = new TextToSpeech(this, x -> {
            if (x == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.02f);
            }
        });
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("start_listening", false)) startListening();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, float z) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(z);
        return v;
    }
    private GradientDrawable shape(int fill, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp((int) radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }
    private int lighten(int c) {
        return Color.rgb(Math.min(255, Color.red(c) + 28), Math.min(255, Color.green(c) + 28), Math.min(255, Color.blue(c) + 28));
    }
    private void paintButton(Button b, int color, boolean focused) {
        b.setBackground(shape(focused ? lighten(color) : color, 14, focused ? Color.WHITE : 0));
        b.setScaleX(focused ? 1.025f : 1f); b.setScaleY(focused ? 1.045f : 1f);
        b.setElevation(focused ? dp(8) : dp(1));
    }
    private Button button(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setId(View.generateViewId()); b.setText(s); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(Color.WHITE); b.setTypeface(null, 1); b.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        b.setPadding(dp(16), 0, dp(16), 0); b.setMinHeight(dp(52)); b.setFocusable(true); b.setFocusableInTouchMode(true);
        b.setOnClickListener(l); paintButton(b, color, false);
        b.setOnFocusChangeListener((v, f) -> paintButton((Button) v, color, f));
        return b;
    }

    private void buildUi() {
        boolean wide = getResources().getConfiguration().screenWidthDp >= 700;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(33, 33, 33));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL); side.setPadding(dp(18), dp(20), dp(18), dp(18));
        side.setBackgroundColor(Color.rgb(23, 23, 23));
        LinearLayout.LayoutParams sideLp = wide ? new LinearLayout.LayoutParams(dp(285), -1) : new LinearLayout.LayoutParams(-1, -2);
        root.addView(side, sideLp);

        TextView brand = text("Ei Chat", 27); brand.setTypeface(null, 1); brand.setGravity(Gravity.CENTER_VERTICAL);
        side.addView(brand, new LinearLayout.LayoutParams(-1, dp(48)));

        Button newChat = button("＋  Novo chat", Color.rgb(45, 45, 45), v -> resetConversation());
        LinearLayout.LayoutParams nbp = new LinearLayout.LayoutParams(-1, dp(54)); nbp.setMargins(0, dp(10), 0, dp(8)); side.addView(newChat, nbp);

        Button assistant = button("🎙  Assistente da TV", Color.rgb(45, 45, 45), v -> requestAssistantRole());
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(-1, dp(54)); abp.setMargins(0, 0, 0, dp(8)); side.addView(assistant, abp);

        Button diag = button("⚙  Diagnóstico", Color.rgb(45, 45, 45), v -> runDiagnostics());
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-1, dp(54)); dbp.setMargins(0, 0, 0, dp(8)); side.addView(diag, dbp);

        Space spacer = new Space(this); side.addView(spacer, new LinearLayout.LayoutParams(1, wide ? 0 : dp(8), wide ? 1f : 0f));
        accountState = text("ChatGPT", 13); accountState.setTextColor(Color.rgb(185, 185, 185)); side.addView(accountState, new LinearLayout.LayoutParams(-1, -2));
        account = button("Conectar ChatGPT", Color.rgb(45, 45, 45), v -> accountAction());
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(-1, dp(54)); acp.setMargins(0, dp(6), 0, 0); side.addView(account, acp);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL); main.setPadding(wide ? dp(34) : dp(16), dp(18), wide ? dp(34) : dp(16), dp(18));
        root.addView(main, new LinearLayout.LayoutParams(wide ? 0 : -1, wide ? -1 : 0, 1f));

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        caption = text("Ei Chat", 18); caption.setTypeface(null, 1); top.addView(caption, new LinearLayout.LayoutParams(0, dp(46), 1f));
        micText = text("● preparando", 14); micText.setTextColor(Color.rgb(170, 170, 170)); micText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(micText, new LinearLayout.LayoutParams(-2, dp(46))); main.addView(top, new LinearLayout.LayoutParams(-1, dp(50)));

        chatScroll = new ScrollView(this); chatScroll.setFillViewport(true);
        chat = new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chat.setPadding(0, dp(12), 0, dp(26));
        chatScroll.addView(chat, new ScrollView.LayoutParams(-1, -2)); main.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout composer = new LinearLayout(this); composer.setOrientation(LinearLayout.HORIZONTAL); composer.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(10), dp(10), dp(10)); composer.setBackground(shape(Color.rgb(47, 47, 47), 24, Color.rgb(74, 74, 74)));
        input = new EditText(this); input.setHint("Pergunte alguma coisa"); input.setHintTextColor(Color.rgb(160, 160, 160)); input.setTextColor(Color.WHITE); input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT); input.setPadding(dp(8), dp(4), dp(8), dp(4)); input.setMaxLines(4); input.setMinLines(1); input.setFocusable(true);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        listen = button("🎤", Color.rgb(66, 66, 66), v -> { audioRetry = false; startListening(); });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(58), dp(54)); vlp.setMargins(dp(8), 0, dp(6), 0); composer.addView(listen, vlp);
        Button send = button("↑", Color.rgb(245, 245, 245), v -> sendTyped()); send.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(58), dp(54)); composer.addView(send, slp);
        main.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = text("A = selecionar  •  use o microfone para conversar por voz", 12); hint.setTextColor(Color.rgb(130, 130, 130)); hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.setMargins(0, dp(7), 0, 0); main.addView(hint, hlp);

        setContentView(root);
        showWelcome();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (auth != null && auth.isConnected()) listen.requestFocus(); else account.requestFocus();
        }, 250);
    }

    private void showWelcome() {
        chat.removeAllViews();
        TextView hero = text("Como posso ajudar?", 31); hero.setTypeface(null, 1); hero.setGravity(Gravity.CENTER); hero.setTextColor(Color.rgb(245, 245, 245));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.setMargins(0, dp(54), 0, dp(24)); chat.addView(hero, hp);
        appendAssistant("Você pode perguntar qualquer coisa ou pedir comandos da TV. Ex.: “abrir YouTube”, “aumentar o volume” ou uma pergunta para a IA.");
    }
    private void resetConversation() { destroySpeech(); showWelcome(); input.setText(""); caption.setText("Ei Chat"); micText.setText(auth.isConnected() ? "● IA pronta" : "● conectar ChatGPT"); }
    private TextView message(String value, boolean user) {
        TextView v = text(value, 17); v.setLineSpacing(0, 1.14f); v.setPadding(dp(18), dp(14), dp(18), dp(14));
        v.setBackground(shape(user ? Color.rgb(64, 64, 64) : Color.rgb(38, 38, 38), 18, user ? Color.rgb(78, 78, 78) : 0));
        v.setMaxWidth(dp(860)); return v;
    }
    private void appendUser(String s) { appendMessage(s, true); }
    private void appendAssistant(String s) { appendMessage(s, false); }
    private void appendMessage(String s, boolean user) {
        if (s == null || s.trim().isEmpty()) return;
        TextView v = message(s.trim(), user); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = user ? Gravity.RIGHT : Gravity.LEFT; p.setMargins(user ? dp(90) : 0, dp(8), user ? 0 : dp(90), dp(8)); chat.addView(v, p);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendTyped() {
        String q = input.getText().toString().trim(); if (q.isEmpty()) return; input.setText(""); handle(q);
    }
    private void runDiagnostics() {
        caption.setText("Diagnóstico • captura direta"); micText.setText("● AudioRecord direto");
        appendAssistant(TvMicrophone.report(this) + "\n\nTestando 2,5 s do microfone interno… fale agora.");
        new AiVoiceRecorder(this).probeBuiltIn((ok, report) -> runOnUiThread(() -> {
            appendAssistant(TvMicrophone.report(this) + "\nReconhecimento Android: " + (SpeechRecognizer.isRecognitionAvailable(this) ? "disponível" : "indisponível") + "\nChatGPT: " + auth.accountLabel() + "\n\n" + report);
            micText.setText(ok ? "● captura direta OK" : "● captura direta falhou"); caption.setText("Ei Chat");
        }));
    }
    private void refreshAccount() {
        boolean ok = auth.isConnected(); account.setText(ok ? "Gerenciar conta" : "Conectar ChatGPT"); accountState.setText(ok ? "ChatGPT • " + auth.accountLabel() : "ChatGPT • não conectado");
        listen.setEnabled(ok); listen.setAlpha(ok ? 1f : .45f); micText.setText(ok ? "● IA pronta" : "● conectar ChatGPT");
    }
    private void accountAction() {
        if (!auth.isConnected()) { connectChatGpt(false); return; }
        final String label = auth.accountLabel();
        new android.app.AlertDialog.Builder(this)
                .setTitle("Conta ChatGPT")
                .setMessage(label)
                .setItems(new String[]{"Trocar conta", "Sair da conta", "Cancelar"}, (d, which) -> {
                    if (which == 0) switchAccount();
                    else if (which == 1) confirmSignOut();
                    else d.dismiss();
                })
                .show();
    }

    private void confirmSignOut() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Sair da conta?")
                .setMessage("O Ei Chat vai apagar desta TV os tokens salvos da conta ChatGPT. Você poderá conectar outra conta depois.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (d, w) -> signOutCurrentAccount())
                .show();
    }

    private void signOutCurrentAccount() {
        destroySpeech();
        auth.signOut();
        ai = new OpenAiClient(this);
        refreshAccount();
        caption.setText("Ei Chat");
        appendAssistant("Conta ChatGPT desconectada deste Ei Chat. Para usar a IA novamente, conecte uma conta.");
        account.requestFocus();
    }

    private void switchAccount() {
        destroySpeech();
        auth.signOut();
        ai = new OpenAiClient(this);
        refreshAccount();
        appendAssistant("Conta atual desconectada. Escolha outra conta no navegador.");
        connectChatGpt(true);
    }

    private void connectChatGpt() { connectChatGpt(false); }
    private void connectChatGpt(boolean chooseAccount) {
        caption.setText(chooseAccount ? "Trocando conta ChatGPT" : "Conectando ao ChatGPT");
        appendAssistant(chooseAccount ? "Abrindo o seletor de contas. Escolha a conta que deseja usar no Ei Chat." : "Abrindo o login. Autorize no navegador e volte ao Ei Chat.");
        account.setEnabled(false);
        auth.signIn(chooseAccount, new ChatGptAuth.Callback() {
            public void onSuccess(String m) { runOnUiThread(() -> { ai = new OpenAiClient(MainActivity.this); account.setEnabled(true); refreshAccount(); appendAssistant(m + "\nAgora você pode digitar ou usar o microfone."); listen.requestFocus(); }); }
            public void onError(String e) { runOnUiThread(() -> { account.setEnabled(true); caption.setText("Ei Chat"); refreshAccount(); appendAssistant(e); micText.setText("● conexão necessária"); account.requestFocus(); }); }
        });
    }

    private void requestAssistantRole() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQ_ASSISTANT); return;
                }
            }
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Throwable e) { appendAssistant("A TV não abriu a tela de assistente automaticamente. Abra Configurações > Apps > Assistente padrão."); }
    }

    private void startListening() {
        if (!auth.isConnected()) { connectChatGpt(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { pending = true; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { caption.setText("Ei Chat • voz indisponível"); appendAssistant("Esta TV não disponibilizou um serviço de reconhecimento de voz para aplicativos."); return; }
        if (tts != null) tts.stop(); AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) try { am.setMode(AudioManager.MODE_NORMAL); am.setSpeakerphoneOn(false); } catch (Throwable ignored) {}
        destroySpeech(); speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { audioRetry = false; caption.setText("Ei Chat • ouvindo"); micText.setText("● ouvindo"); }
            public void onBeginningOfSpeech() {} public void onRmsChanged(float r) { micText.setText(r > 2 ? "● ouvindo sua voz" : "● microfone ativo"); }
            public void onBufferReceived(byte[] b) {} public void onEndOfSpeech() { caption.setText("Ei Chat • entendendo"); micText.setText("● entendendo"); }
            public void onError(int e) {
                if (e == SpeechRecognizer.ERROR_AUDIO && !audioRetry) { audioRetry = true; caption.setText("Ei Chat • ajustando áudio"); micText.setText("● segunda tentativa"); destroySpeech(); new Handler(Looper.getMainLooper()).postDelayed(() -> startListening(), 650); return; }
                caption.setText("Ei Chat • erro de voz"); appendAssistant("Reconhecimento de voz: " + speechError(e) + ". Abra Diagnóstico se continuar."); micText.setText("● tente novamente");
            }
            public void onResults(Bundle b) { ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (a == null || a.isEmpty() || a.get(0).trim().isEmpty()) { onError(SpeechRecognizer.ERROR_NO_MATCH); return; } handle(a.get(0).trim()); }
            public void onPartialResults(Bundle b) {} public void onEvent(int t, Bundle b) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR"); i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3); i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        try { speech.startListening(i); } catch (Throwable e) { caption.setText("Ei Chat • erro de voz"); appendAssistant("Não foi possível iniciar o microfone: " + e.getMessage()); }
    }
    private void destroySpeech() { if (speech != null) { try { speech.cancel(); } catch (Throwable ignored) {} try { speech.destroy(); } catch (Throwable ignored) {} speech = null; } }
    private void handle(String q) {
        appendUser(q); caption.setText("Ei Chat"); micText.setText("● processando"); CommandEngine.Result r = engine.execute(q);
        if (r.handled) { appendAssistant(r.message); micText.setText("● comando executado"); speak(r.message); return; }
        caption.setText("Ei Chat • pensando"); ai.ask(q, new OpenAiClient.Callback() {
            public void onSuccess(String x) { runOnUiThread(() -> { caption.setText("Ei Chat"); appendAssistant(x); micText.setText("● IA pronta"); speak(x); }); }
            public void onError(String x) { runOnUiThread(() -> { caption.setText("Ei Chat • erro da IA"); appendAssistant(x); micText.setText("● tente novamente"); }); }
        });
    }
    private static String speechError(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_AUDIO: return "erro de áudio da TV"; case SpeechRecognizer.ERROR_CLIENT: return "erro do cliente"; case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "sem permissão de microfone";
            case SpeechRecognizer.ERROR_NETWORK: return "erro de rede"; case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "tempo de rede esgotado"; case SpeechRecognizer.ERROR_NO_MATCH: return "não entendi a fala";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "reconhecedor ocupado"; case SpeechRecognizer.ERROR_SERVER: return "erro do serviço de voz"; case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "nenhuma fala detectada"; default: return "código " + e;
        }
    }
    private void speak(String x) { if (tts != null && x != null && !x.trim().isEmpty()) tts.speak(x, TextToSpeech.QUEUE_FLUSH, null, "ei_chat"); }

    @Override public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBackAt <= 2000L) {
            destroySpeech();
            if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
            finishAndRemoveTask();
            return;
        }
        lastBackAt = now;
        Toast.makeText(this, "Pressione Voltar novamente para sair do Ei Chat", Toast.LENGTH_SHORT).show();
    }
    @Override public void onRequestPermissionsResult(int c, String[] p, int[] g) { super.onRequestPermissionsResult(c, p, g); if (c == REQ_MIC && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && pending) { pending = false; startListening(); } }
    @Override protected void onResume() { super.onResume(); if (auth != null) refreshAccount(); }
    @Override protected void onDestroy() { destroySpeech(); if (tts != null) tts.shutdown(); super.onDestroy(); }
}

package com.magraoxx.eichattv;

import android.Manifest;
import android.app.Dialog;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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

    private Bitmap screenBitmap;
    private String screenContext = "";
    private boolean screenshotResolved;
    private boolean assistResolved;
    private String pendingVisionQuery;
    private Runnable pendingVisionTimeout;

    public EiChatSession(Context c) {
        super(c);
        engine = new CommandEngine(c);
        ai = new OpenAiClient(c);
        auth = new ChatGptAuth(c);
    }

    private int dp(int v) { return Math.round(v * getContext().getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    private TextView tv(String s, float size) {
        TextView v = new TextView(getContext());
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    @Override public View onCreateContentView() {
        Dialog d = getWindow();
        Window w = d == null ? null : d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER_HORIZONTAL);
        shell.setPadding(dp(28), 0, dp(28), dp(34));

        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(20), dp(15), dp(20), dp(15));
        box.setBackground(bg(Color.rgb(31, 31, 31), 28, Color.rgb(72, 72, 72)));
        shell.addView(box, new LinearLayout.LayoutParams(-1, -2));

        pulse = tv("●", 30);
        pulse.setTextColor(Color.rgb(120, 185, 255));
        pulse.setGravity(Gravity.CENTER);
        box.addView(pulse, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(8), 0, dp(8), 0);
        box.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

        title = tv("Ei Chat", 18);
        title.setTypeface(null, 1);
        words.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = tv("Pronto", 16);
        status.setTextColor(Color.rgb(220, 220, 220));
        status.setMaxLines(4);
        words.addView(status, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = tv("Diga “encerrar chamada” para sair", 12);
        hint.setTextColor(Color.rgb(145, 145, 145));
        hint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        box.addView(hint, new LinearLayout.LayoutParams(-2, dp(56)));
        return shell;
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        screenshotResolved = false;
        assistResolved = false;
        screenBitmap = null;
        screenContext = "";
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("session_active", true)
                .putLong("session_opened_at", System.currentTimeMillis())
                .putInt("session_show_flags", flags)
                .putInt("disabled_show_context", getDisabledShowContext())
                .putInt("user_disabled_show_context", getUserDisabledShowContext())
                .apply();
        if (title == null) setContentView(onCreateContentView());
        continuous = args != null && args.getBoolean("continuous", false);
        String initial = args == null ? "" : args.getString("initial_query", "").trim();
        if (!initial.isEmpty()) {
            set("Ei Chat • ativado", "Comando recebido: “" + initial + "”", Color.rgb(120, 185, 255));
            main.postDelayed(() -> handle(initial), 160);
        } else {
            startListening();
        }
    }

    @Override
    public void onHandleScreenshot(Bitmap screenshot) {
        screenshotResolved = true;
        screenBitmap = screenshot;
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_screen_capture_ok", screenshot != null)
                .putLong("last_screen_capture_at", System.currentTimeMillis())
                .putInt("last_screen_width", screenshot == null ? 0 : screenshot.getWidth())
                .putInt("last_screen_height", screenshot == null ? 0 : screenshot.getHeight())
                .apply();
        flushPendingVisionIfReady();
    }

    @Override
    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
        assistResolved = true;
        screenContext = buildScreenContext(structure, content);
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_assist_context_ok", !screenContext.isEmpty())
                .putLong("last_assist_context_at", System.currentTimeMillis())
                .putString("last_assist_context_preview", screenContext.length() > 500 ? screenContext.substring(0, 500) : screenContext)
                .apply();
        flushPendingVisionIfReady();
    }

    private void set(String t, String s, int color) {
        if (title != null) title.setText(t);
        if (status != null) status.setText(s);
        if (pulse != null) pulse.setTextColor(color);
    }

    private void startListening() {
        if (!auth.isConnected()) {
            set("Ei Chat", "Abra o aplicativo e conecte sua conta ChatGPT primeiro.", Color.rgb(255, 180, 80));
            speak("Abra o Ei Chat e conecte sua conta ChatGPT primeiro.", this::hide);
            return;
        }
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            set("Ei Chat", "Permissão de microfone necessária.", Color.rgb(255, 180, 80));
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            set("Ei Chat", "Reconhecimento de voz indisponível nesta TV.", Color.rgb(255, 105, 105));
            return;
        }
        destroySpeech();
        speech = SpeechRecognizer.createSpeechRecognizer(getContext());
        speech.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { set("Ei Chat • ouvindo", "Pode falar…", Color.rgb(120, 185, 255)); }
            public void onBeginningOfSpeech() { set("Ei Chat • ouvindo", "Estou ouvindo você…", Color.rgb(120, 185, 255)); }
            public void onRmsChanged(float r) { if (pulse != null) pulse.setText(r > 2f ? "●" : "•"); }
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { set("Ei Chat • entendendo", "Entendendo sua voz…", Color.rgb(190, 150, 255)); }
            public void onError(int e) {
                set("Ei Chat", "Não consegui ouvir. Tente novamente.", Color.rgb(255, 105, 105));
                if (continuous) main.postDelayed(() -> startListening(), 900);
            }
            public void onResults(Bundle b) {
                ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (a == null || a.isEmpty() || a.get(0).trim().isEmpty()) {
                    set("Ei Chat", "Não entendi.", Color.rgb(255, 180, 80));
                    if (continuous) main.postDelayed(() -> startListening(), 700);
                    return;
                }
                handle(a.get(0).trim());
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
        android.content.Intent i = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            speech.startListening(i);
        } catch (Throwable e) {
            set("Ei Chat", "Falha ao abrir o microfone.", Color.rgb(255, 105, 105));
        }
    }

    private void handle(String q) {
        String n = norm(q);
        if (n.contains("encerrar chamada") || n.contains("finalizar chamada") || n.equals("encerrar")) {
            continuous = false;
            destroySpeech();
            set("Ei Chat", "Chamada encerrada.", Color.rgb(130, 220, 165));
            speak("Chamada encerrada.", this::hide);
            return;
        }
        if ((n.contains("iniciar chamada") || n.contains("inicie chamada")) && n.contains("voz")) {
            continuous = true;
            set("Ei Chat • chamada", "Conversa contínua ativada.", Color.rgb(130, 220, 165));
            speak("Chamada de voz iniciada. Pode falar comigo sem repetir o comando.", this::startListening);
            return;
        }

        set("Você", q, Color.rgb(120, 185, 255));
        if (isVisionRequest(n)) {
            analyzeCurrentScreen(q);
            return;
        }

        CommandEngine.Result r = engine.execute(q);
        if (r.handled) {
            set("Ei Chat", r.message, Color.rgb(130, 220, 165));
            speak(r.message, afterReply());
            return;
        }
        set("Ei Chat • pensando", "Analisando…", Color.rgb(190, 150, 255));
        ai.ask(q, new OpenAiClient.Callback() {
            public void onSuccess(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat", x, Color.rgb(130, 220, 165));
                    speak(x, afterReply());
                });
            }
            public void onError(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat", x, Color.rgb(255, 105, 105));
                    if (continuous) main.postDelayed(() -> startListening(), 900);
                });
            }
        });
    }

    private void analyzeCurrentScreen(String query) {
        destroySpeech();
        set("Ei Chat • olhando a tela", "Capturando e analisando o que está aparecendo…", Color.rgb(190, 150, 255));
        if (screenBitmap != null || screenshotResolved) {
            runVisionQuery(query);
            return;
        }
        pendingVisionQuery = query;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = () -> {
            if (pendingVisionQuery == null) return;
            String q = pendingVisionQuery;
            pendingVisionQuery = null;
            runVisionQuery(q);
        };
        main.postDelayed(pendingVisionTimeout, 900);
    }

    private void flushPendingVisionIfReady() {
        if (pendingVisionQuery == null) return;
        if (screenBitmap == null && !screenshotResolved) return;
        String q = pendingVisionQuery;
        pendingVisionQuery = null;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = null;
        runVisionQuery(q);
    }

    private void runVisionQuery(String query) {
        Bitmap image = screenBitmap;
        String context = screenContext;
        if (image == null && (context == null || context.trim().isEmpty())) {
            String msg = "Não consegui receber a imagem nem o contexto desta tela. Esse aplicativo, entrada HDMI ou conteúdo protegido pode estar bloqueando a captura.";
            set("Ei Chat • visão indisponível", msg, Color.rgb(255, 180, 80));
            speak(msg, afterReply());
            return;
        }

        String indicator = image != null
                ? "Imagem da tela recebida. Analisando detalhes…"
                : "A imagem foi bloqueada; analisando o contexto textual disponível…";
        set("Ei Chat • visão", indicator, Color.rgb(190, 150, 255));

        ai.askWithScreen(query, image, context, new OpenAiClient.Callback() {
            @Override public void onSuccess(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat • visão", x, Color.rgb(130, 220, 165));
                    speak(x, afterReply());
                });
            }

            @Override public void onError(String x) {
                getContext().getMainExecutor().execute(() -> {
                    set("Ei Chat • visão", x, Color.rgb(255, 105, 105));
                    if (continuous) main.postDelayed(() -> startListening(), 900);
                });
            }
        });
    }

    private static boolean isVisionRequest(String n) {
        if (n == null || n.isEmpty()) return false;
        String[] phrases = new String[]{
                "analisa essa", "analise essa", "analisa isso", "analise isso",
                "olha essa", "olhe essa", "olha isso", "olhe isso",
                "essa parte", "essa tela", "nessa tela", "o que tem na tela", "o que esta na tela",
                "que jogo e esse", "qual jogo e esse", "que jogo estou", "qual jogo estou",
                "que filme e esse", "qual filme e esse", "que filme e", "qual filme e",
                "que serie e essa", "qual serie e essa", "que serie e", "qual serie e",
                "que musica e essa", "qual musica e essa", "que musica e", "qual musica e",
                "onde eu estou", "onde estou", "em que parte", "parte do mapa", "no mapa",
                "pra onde eu vou", "para onde eu vou", "pra onde ir", "para onde ir",
                "como passo daqui", "como passar daqui", "como passo essa", "como passar essa",
                "essa fase", "esse puzzle", "esse quebra cabeca", "essa senha", "qual a senha",
                "que item e esse", "qual item e esse", "esse item", "esse chefe", "esse boss",
                "o que faco aqui", "o que fazer aqui", "me ajuda aqui", "me ajude aqui"
        };
        for (String p : phrases) if (n.contains(p)) return true;
        return false;
    }

    private static String buildScreenContext(AssistStructure structure, AssistContent content) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        try {
            if (structure != null) {
                ComponentName component = structure.getActivityComponent();
                if (component != null) {
                    out.append("Aplicativo/atividade: ").append(component.flattenToShortString()).append('\n');
                }
                int windows = structure.getWindowNodeCount();
                for (int i = 0; i < windows && seen.size() < 100; i++) {
                    AssistStructure.WindowNode win = structure.getWindowNodeAt(i);
                    if (win != null) collectNodeText(win.getRootViewNode(), seen, 100);
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (content != null) {
                if (content.getWebUri() != null) out.append("URL/contexto web: ").append(content.getWebUri()).append('\n');
                if (content.getClipData() != null) out.append("Há conteúdo compartilhado pela atividade.\n");
            }
        } catch (Throwable ignored) {}

        if (!seen.isEmpty()) {
            out.append("Textos/descrições visíveis:\n");
            int count = 0;
            for (String s : seen) {
                out.append("- ").append(s).append('\n');
                if (++count >= 100 || out.length() > 6500) break;
            }
        }
        return out.toString().trim();
    }

    private static void collectNodeText(AssistStructure.ViewNode node, Set<String> seen, int max) {
        if (node == null || seen.size() >= max) return;
        try {
            CharSequence text = node.getText();
            addVisibleText(seen, text, max);
            CharSequence desc = node.getContentDescription();
            addVisibleText(seen, desc, max);
            String id = node.getIdEntry();
            if (id != null && !id.trim().isEmpty() && id.length() < 80) addVisibleText(seen, id, max);
        } catch (Throwable ignored) {}
        int children = 0;
        try { children = node.getChildCount(); } catch (Throwable ignored) {}
        for (int i = 0; i < children && seen.size() < max; i++) {
            try { collectNodeText(node.getChildAt(i), seen, max); } catch (Throwable ignored) {}
        }
    }

    private static void addVisibleText(Set<String> seen, CharSequence cs, int max) {
        if (cs == null || seen.size() >= max) return;
        String s = cs.toString().replaceAll("\\s+", " ").trim();
        if (s.length() < 2 || s.length() > 300) return;
        seen.add(s);
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
                ttsReady = true;
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.02f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        Runnable r = pendingAfter;
                        pendingAfter = null;
                        if (r != null) main.post(r);
                    }
                    @Override public void onError(String id) {
                        Runnable r = pendingAfter;
                        pendingAfter = null;
                        if (r != null) main.post(r);
                    }
                });
                String p = pendingSpeech;
                pendingSpeech = null;
                if (p != null) speak(p, pendingAfter);
            }
        });
    }

    private void speak(String s, Runnable after) {
        if (s == null || s.trim().isEmpty()) {
            if (after != null) after.run();
            return;
        }
        pendingAfter = after;
        if (!ttsReady) {
            pendingSpeech = s;
            initTts();
            return;
        }
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ei_chat_session");
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private void destroySpeech() {
        if (speech != null) {
            try { speech.cancel(); } catch (Throwable ignored) {}
            try { speech.destroy(); } catch (Throwable ignored) {}
            speech = null;
        }
    }

    private void clearVisionState() {
        pendingVisionQuery = null;
        if (pendingVisionTimeout != null) main.removeCallbacks(pendingVisionTimeout);
        pendingVisionTimeout = null;
        screenBitmap = null;
        screenContext = "";
        screenshotResolved = false;
        assistResolved = false;
    }

    private void markSessionClosed() {
        getContext().getSharedPreferences("ei_chat_runtime", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("session_active", false)
                .putLong("session_closed_at", System.currentTimeMillis())
                .apply();
    }

    @Override public void onHide() {
        markSessionClosed();
        destroySpeech();
        clearVisionState();
        if (tts != null) try { tts.stop(); } catch (Throwable ignored) {}
        super.onHide();
    }

    @Override public void onDestroy() {
        markSessionClosed();
        destroySpeech();
        clearVisionState();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
